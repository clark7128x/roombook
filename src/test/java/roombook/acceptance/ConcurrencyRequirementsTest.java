package roombook.acceptance;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import roombook.testsupport.HttpTestSupport;
import roombook.testsupport.InvariantAudit;
import roombook.testsupport.TestDataBuilder;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Специфічні сценарії узгодженості та конкуренції NFR-03 (T-25, TS-16).
 */
@Tag("TS-16")
@Tag("NFR-03")
class ConcurrencyRequirementsTest extends AcceptanceTestSupport {

    @Test
    @Tag("INV-08")
    void parallelBookingsOnSameSlotProduceExactlyOneSuccess() throws Exception {
        long room = TestDataBuilder.room().name("Паралель").insert();
        String body = mapper.writeValueAsString(Map.of(
                "roomId", room, "title", "Гонка",
                "startAt", "2026-10-27T10:00", "endAt", "2026-10-27T11:00"
        ));

        int requests = 50;
        var ready = new java.util.concurrent.CountDownLatch(requests);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(requests)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<java.net.http.HttpResponse<String>>>();
            for (int i = 0; i < requests; i++) futures.add(pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, java.util.concurrent.TimeUnit.SECONDS));
                return api("POST", "/api/v1/bookings", employee, body);
            }));
            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            int created = 0, conflicts = 0;
            for (var future : futures) {
                var result = future.get(30, java.util.concurrent.TimeUnit.SECONDS);
                if (result.statusCode() == 201) created++;
                else { problem(result, 409, "SLOT_TAKEN"); conflicts++; }
            }
            assertEquals(1, created);
            assertEquals(49, conflicts);
        }

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("INV-08")
    void doubleSubmitProducesConflictNotDuplicate() throws Exception {
        long room = TestDataBuilder.room().name("Подвійне").insert();
        String body = mapper.writeValueAsString(Map.of(
                "roomId", room, "title", "Дубль",
                "startAt", "2026-10-27T10:00", "endAt", "2026-10-27T11:00"
        ));

        // Перше надсилання
        var first = api("POST", "/api/v1/bookings", employee, body);
        assertEquals(201, first.statusCode());

        // Друге надсилання того самого
        var second = api("POST", "/api/v1/bookings", employee, body);
        problem(second, 409, "SLOT_TAKEN");

        // Лише одне бронювання в БД
        var check = api("GET", "/api/v1/me/bookings?dateFrom=2026-10-27&dateTo=2026-10-27", employee, "");
        assertEquals(1, json(check).get("items").size());

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("INV-09")
    void parallelDeactivationAndBookingCannotBothSucceed() throws Exception {
        long room = TestDataBuilder.room().name("Деактивація").insert();

        // Створюємо бронювання на сьогодні й скасовуємо
        var booking = api("POST", "/api/v1/bookings", employee,
                mapper.writeValueAsString(Map.of(
                        "roomId", room, "title", "Сьогодні",
                        "startAt", "2026-10-27T16:00", "endAt", "2026-10-27T17:00"
                )));
        long bookingId = json(booking).get("id").asLong();
        api("POST", "/api/v1/bookings/" + bookingId + "/cancel", employee, "");

        // Паралельно деактивуємо кімнату й створюємо бронювання на завтра
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);

        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var deactivateTask = pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, java.util.concurrent.TimeUnit.SECONDS));
                return api("POST", "/api/v1/rooms/" + room + "/deactivate", admin, "");
            });

            var bookingTask = pool.submit(() -> {
                ready.countDown();
                assertTrue(start.await(10, java.util.concurrent.TimeUnit.SECONDS));
                return api("POST", "/api/v1/bookings", employee,
                        mapper.writeValueAsString(Map.of(
                                "roomId", room, "title", "Завтра",
                                "startAt", "2026-10-28T10:00", "endAt", "2026-10-28T11:00"
                        )));
            });

            assertTrue(ready.await(10, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();

            var deactivateResult = deactivateTask.get(20, java.util.concurrent.TimeUnit.SECONDS);
            var bookingResult = bookingTask.get(20, java.util.concurrent.TimeUnit.SECONDS);

            // Не може бути 200 і 201 одночасно
            if (deactivateResult.statusCode() == 200) problem(bookingResult, 409, "ROOM_INACTIVE");
            else {
                assertEquals(201, bookingResult.statusCode(), bookingResult.body());
                problem(deactivateResult, 409, "ROOM_HAS_UNFINISHED_BOOKINGS");
            }
        }

        InvariantAudit.assertAllInvariantsHold();
    }
}

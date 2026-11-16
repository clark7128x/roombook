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
 * Наскрізний сценарій UC-03: скасування власного бронювання (T-25, TS-16).
 */
@Tag("TS-16")
@Tag("UC-03")
class UC03CancelOwnBookingTest extends AcceptanceTestSupport {

    @Test
    @Tag("FR-07")
    @Tag("FR-17")
    @Tag("FR-18")
    void employeeViewsOwnBookingsAndCancelsUnfinishedOne() throws Exception {
        long room = TestDataBuilder.room().name("Моє").insert();

        // Створюємо кілька бронювань
        var body = mapper.writeValueAsString(Map.of(
                "roomId", room, "title", "Майбутнє",
                "startAt", "2026-10-28T10:00", "endAt", "2026-10-28T11:00"
        ));
        var future = api("POST", "/api/v1/bookings", employee, body);
        assertEquals(201, future.statusCode());
        long futureId = json(future).get("id").asLong();

        body = mapper.writeValueAsString(Map.of(
                "roomId", room, "title", "Минуле",
                "startAt", "2026-10-28T14:00", "endAt", "2026-10-28T15:00"
        ));
        var past = api("POST", "/api/v1/bookings", employee, body);
        assertEquals(201, past.statusCode());

        // Переглядає «Мої бронювання»
        var myBookings = api("GET", "/api/v1/me/bookings", employee, "");
        assertEquals(200, myBookings.statusCode());
        var items = json(myBookings).get("items");
        assertTrue(items.size() >= 2);

        // Скасовує майбутнє
        var cancel = api("POST", "/api/v1/bookings/" + futureId + "/cancel", employee, "");
        assertEquals(200, cancel.statusCode());
        assertEquals("CANCELLED", json(cancel).get("status").asString());
        assertFalse(json(cancel).get("cancellable").asBoolean());

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("FR-17")
    void employeeCannotCancelOthersBooking() throws Exception {
        long room = TestDataBuilder.room().name("Чуже").insert();

        // Інший користувач створює бронювання
        var body = mapper.writeValueAsString(Map.of(
                "roomId", room, "title", "Чужа зустріч",
                "startAt", "2026-10-27T14:00", "endAt", "2026-10-27T15:00"
        ));
        var othersBooking = api("POST", "/api/v1/bookings", other, body);
        assertEquals(201, othersBooking.statusCode());
        long bookingId = json(othersBooking).get("id").asLong();

        // EMPLOYEE намагається скасувати чуже
        var cancel = api("POST", "/api/v1/bookings/" + bookingId + "/cancel", employee, "");
        problem(cancel, 403, "NOT_PERMITTED");

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("FR-17")
    void currentBookingCanBeCancelledButFinishedCannot() throws Exception {
        long room = TestDataBuilder.room().name("Фази").insert();

        // Створюємо бронювання 10:00-11:00
        var body = mapper.writeValueAsString(Map.of(
                "roomId", room, "title", "Поточна",
                "startAt", "2026-10-27T10:00", "endAt", "2026-10-27T11:00"
        ));
        var booking = api("POST", "/api/v1/bookings", employee, body);
        assertEquals(201, booking.statusCode());
        long bookingId = json(booking).get("id").asLong();

        // 10:59:59 - ще триває, можна скасувати
        clock.setInstant(Instant.parse("2026-10-27T08:59:59Z")); // 10:59:59 за Києвом
        var duringMeeting = api("GET", "/api/v1/bookings/" + bookingId, employee, "");
        assertEquals("IN_PROGRESS", json(duringMeeting).get("phase").asString());
        assertTrue(json(duringMeeting).get("cancellable").asBoolean());

        var cancelDuring = api("POST", "/api/v1/bookings/" + bookingId + "/cancel", employee, "");
        assertEquals(200, cancelDuring.statusCode());

        // Створюємо інше бронювання для перевірки завершеного
        clock.setInstant(Instant.parse("2026-10-27T08:00:00Z")); // 10:00 за Києвом
        body = mapper.writeValueAsString(Map.of(
                "roomId", room, "title", "Завершене",
                "startAt", "2026-10-27T12:00", "endAt", "2026-10-27T13:00"
        ));
        var finished = api("POST", "/api/v1/bookings", employee, body);
        long finishedId = json(finished).get("id").asLong();

        // 13:00:00 - завершилось, скасувати не можна
        clock.setInstant(Instant.parse("2026-10-27T11:00:00Z")); // 13:00:00 за Києвом
        var afterMeeting = api("GET", "/api/v1/bookings/" + finishedId, employee, "");
        assertEquals("FINISHED", json(afterMeeting).get("phase").asString());
        assertFalse(json(afterMeeting).get("cancellable").asBoolean());

        var cancelFinished = api("POST", "/api/v1/bookings/" + finishedId + "/cancel", employee, "");
        problem(cancelFinished, 409, "BOOKING_FINISHED");

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("FR-18")
    void bookingCannotBeEditedOnlyCreatedAndCancelled() throws Exception {
        long room = TestDataBuilder.room().name("Редагування").insert();

        var body = mapper.writeValueAsString(Map.of(
                "roomId", room, "title", "Оригінал",
                "startAt", "2026-10-27T15:00", "endAt", "2026-10-27T16:00"
        ));
        var booking = api("POST", "/api/v1/bookings", employee, body);
        assertEquals(201, booking.statusCode());
        long bookingId = json(booking).get("id").asLong();

        // PUT, PATCH, DELETE не підтримуються
        for (String method : new String[]{"PUT", "PATCH", "DELETE"}) {
            var response = api(method, "/api/v1/bookings/" + bookingId, employee,
                    mapper.writeValueAsString(Map.of("title", "Змінена")));
            problem(response, 405, "UNSUPPORTED_REQUEST");
        }

        InvariantAudit.assertAllInvariantsHold();
    }
}

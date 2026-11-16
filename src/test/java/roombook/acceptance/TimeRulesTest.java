package roombook.acceptance;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import roombook.testsupport.HttpTestSupport;
import roombook.testsupport.InvariantAudit;
import roombook.testsupport.TestDataBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Перевірка правил часу та зон NFR-07, TR-01...TR-06 (T-25, TS-16).
 */
@Tag("TS-16")
@Tag("NFR-07")
class TimeRulesTest extends AcceptanceTestSupport {

    @Test
    @Tag("TR-01")
    void timeRulesCalculatedInKyivZoneRegardlessOfServerZone() throws Exception {
        long room = TestDataBuilder.room().name("Зона").insert();

        // Поточний момент: 25 жовтня 2026, 00:30 UTC = 03:30 за Києвом (зимовий час)
        clock.setInstant(Instant.parse("2026-10-25T00:30:00Z"));

        // «Сьогодні» це 25.10.2026
        var today = api("GET", "/api/v1/occupancy?date=2026-10-25", employee, "");
        assertEquals(200, today.statusCode());

        // Бронювання о 08:00 сьогодні приймається (навіть якщо серверна зона інша)
        var morning = api("POST", "/api/v1/bookings", employee,
                mapper.writeValueAsString(Map.of(
                        "roomId", room, "title", "Ранок",
                        "startAt", "2026-10-25T08:00", "endAt", "2026-10-25T09:00"
                )));
        assertEquals(201, morning.statusCode());

        // Бронювання має фазу UPCOMING і блокує деактивацію
        var booking = json(morning);
        assertEquals("UPCOMING", booking.get("phase").asString());

        var deactivate = api("POST", "/api/v1/rooms/" + room + "/deactivate", admin, "");
        problem(deactivate, 409, "ROOM_HAS_UNFINISHED_BOOKINGS");

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("TR-01")
    void horizonCalculatedFromKyivDate() throws Exception {
        long room = TestDataBuilder.room().name("Горизонт зони").insert();

        // 01:00 за Києвом у зимовий час = 23:00 UTC попереднього дня
        clock.setInstant(Instant.parse("2026-10-26T23:00:00Z"));

        // «Сьогодні» за Києвом це 27.10.2026
        // День 90 від 27.10.2026 це 25.01.2027
        LocalDate today = LocalDate.of(2026, 10, 27);
        LocalDate day90 = today.plusDays(90);
        LocalDate day91 = today.plusDays(91);

        var acceptDay90 = api("POST", "/api/v1/bookings", employee,
                mapper.writeValueAsString(Map.of(
                        "roomId", room, "title", "День 90",
                        "startAt", day90 + "T10:00", "endAt", day90 + "T11:00"
                )));
        assertEquals(201, acceptDay90.statusCode());

        var rejectDay91 = api("POST", "/api/v1/bookings", employee,
                mapper.writeValueAsString(Map.of(
                        "roomId", room, "title", "День 91",
                        "startAt", day91 + "T10:00", "endAt", day91 + "T11:00"
                )));
        problem(rejectDay91, 422, "INVALID_TIME");
        assertEquals("TOO_FAR", json(rejectDay91).get("rule").asString());

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("TR-05")
    void bookingPhaseTransitionsAtExactBoundaries() throws Exception {
        long room = TestDataBuilder.room().name("Межі фаз").insert();

        // Створюємо бронювання 10:00-11:00
        clock.setInstant(Instant.parse("2026-10-27T08:00:00Z")); // 10:00 Київ
        var booking = api("POST", "/api/v1/bookings", employee,
                mapper.writeValueAsString(Map.of(
                        "roomId", room, "title", "Фази",
                        "startAt", "2026-10-27T10:00", "endAt", "2026-10-27T11:00"
                )));
        long bookingId = json(booking).get("id").asLong();

        // 08:59:59.999 UTC = 10:59:59.999 Київ (ще триває)
        clock.setInstant(Instant.parse("2026-10-27T08:59:59.999Z"));
        var during = api("GET", "/api/v1/bookings/" + bookingId, employee, "");
        assertEquals("IN_PROGRESS", json(during).get("phase").asString());
        assertTrue(json(during).get("cancellable").asBoolean());

        // Скасування під час зустрічі дозволене
        var cancelDuring = api("POST", "/api/v1/bookings/" + bookingId + "/cancel", employee, "");
        assertEquals(200, cancelDuring.statusCode());

        // Створюємо нове для перевірки завершення
        clock.setInstant(Instant.parse("2026-10-27T08:00:00Z")); // 10:00 Київ
        var booking2 = api("POST", "/api/v1/bookings", employee,
                mapper.writeValueAsString(Map.of(
                        "roomId", room, "title", "Друге",
                        "startAt", "2026-10-27T11:00", "endAt", "2026-10-27T12:00"
                )));
        long booking2Id = json(booking2).get("id").asLong();

        // 10:00:00.000 UTC = 12:00:00.000 Київ (завершилось)
        clock.setInstant(Instant.parse("2026-10-27T10:00:00Z"));
        var finished = api("GET", "/api/v1/bookings/" + booking2Id, employee, "");
        assertEquals("FINISHED", json(finished).get("phase").asString());
        assertFalse(json(finished).get("cancellable").asBoolean());

        var cancelFinished = api("POST", "/api/v1/bookings/" + booking2Id + "/cancel", employee, "");
        problem(cancelFinished, 409, "BOOKING_FINISHED");

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("TR-04")
    void currentSlotStartAcceptedPastSlotRejected() throws Exception {
        long room = TestDataBuilder.room().name("Поточний слот").insert();

        // 10:07 за Києвом (08:07 UTC), поточний слот 10:00
        clock.setInstant(Instant.parse("2026-10-27T08:07:00Z"));

        // Початок 10:00 (поточний слот) приймається
        var current = api("POST", "/api/v1/bookings", employee,
                mapper.writeValueAsString(Map.of(
                        "roomId", room, "title", "Поточний",
                        "startAt", "2026-10-27T10:00", "endAt", "2026-10-27T11:00"
                )));
        assertEquals(201, current.statusCode());

        // Початок 09:45 (минулий слот) відхиляється
        var past = api("POST", "/api/v1/bookings", employee,
                mapper.writeValueAsString(Map.of(
                        "roomId", room, "title", "Минуле",
                        "startAt", "2026-10-27T09:45", "endAt", "2026-10-27T10:45"
                )));
        problem(past, 422, "INVALID_TIME");
        assertEquals("IN_THE_PAST", json(past).get("rule").asString());

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("TR-02")
    void localDateTimeStoredInOfficeZoneWithoutOffset() throws Exception {
        long room = TestDataBuilder.room().name("Зберігання").insert();

        // Створюємо бронювання
        var booking = api("POST", "/api/v1/bookings", employee,
                mapper.writeValueAsString(Map.of(
                        "roomId", room, "title", "Зберігання",
                        "startAt", "2026-10-27T10:00", "endAt", "2026-10-27T11:00"
                )));
        assertEquals(201, booking.statusCode());

        // Перевіряємо формат відповіді: без Z та зсуву
        var result = json(booking);
        var startAt = result.get("startAt").asString();
        assertEquals("2026-10-27T10:00:00", startAt);
        assertFalse(startAt.contains("Z"));
        assertFalse(startAt.contains("+"));

        // createdAt у UTC з Z
        var createdAt = result.get("createdAt").asString();
        assertTrue(createdAt.endsWith("Z"));

        InvariantAudit.assertAllInvariantsHold();
    }
}

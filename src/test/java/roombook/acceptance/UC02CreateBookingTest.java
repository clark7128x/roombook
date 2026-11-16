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
 * Наскрізний сценарій UC-02: створення бронювання (T-25, TS-16).
 */
@Tag("TS-16")
@Tag("UC-02")
class UC02CreateBookingTest extends AcceptanceTestSupport {

    private String bookingRequest(long roomId, String start, String end, String title) {
        return mapper.writeValueAsString(Map.of(
                "roomId", roomId,
                "title", title,
                "startAt", start,
                "endAt", end
        ));
    }

    @Test
    @Tag("FR-03")
    @Tag("FR-04")
    @Tag("FR-09")
    void employeeViewsOccupancySelectsRoomAndCreatesBooking() throws Exception {
        // Створюємо активну кімнату
        long activeRoom = TestDataBuilder.room().name("Переговорна А").floor(3).capacity(8).active(true).insert();

        // EMPLOYEE бачить активну кімнату
        var roomsResponse = api("GET", "/api/v1/rooms", employee, "");
        assertEquals(200, roomsResponse.statusCode());
        var rooms = json(roomsResponse);
        assertEquals(1, rooms.size());
        assertEquals("Переговорна А", rooms.get(0).get("name").asString());

        // Бачить зайнятість на дату
        var occupancyResponse = api("GET", "/api/v1/occupancy?date=2026-10-27", employee, "");
        assertEquals(200, occupancyResponse.statusCode());

        // Створює бронювання
        var createResponse = api("POST", "/api/v1/bookings", employee,
                bookingRequest(activeRoom, "2026-10-27T10:00", "2026-10-27T11:00", "Щоденний стендап"));
        assertEquals(201, createResponse.statusCode());
        var booking = json(createResponse);
        assertEquals(employeeId, booking.get("author").get("id").asLong());
        assertEquals("Щоденний стендап", booking.get("title").asString());
        assertEquals("ACTIVE", booking.get("status").asString());

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("FR-10")
    @Tag("FR-11")
    @Tag("FR-13")
    @Tag("FR-14")
    void bookingValidatesTimeRulesAndFieldLimits() throws Exception {
        long room = TestDataBuilder.room().name("Валідація").insert();

        // FR-10: порожня назва
        var emptyTitle = api("POST", "/api/v1/bookings", employee,
                bookingRequest(room, "2026-10-27T10:00", "2026-10-27T11:00", "   "));
        problem(emptyTitle, 400, "INVALID_INPUT");
        assertTrue(json(emptyTitle).get("errors").toString().contains("title"));

        // FR-10: надто довга назва (201 символ)
        var longTitle = api("POST", "/api/v1/bookings", employee,
                bookingRequest(room, "2026-10-27T10:00", "2026-10-27T11:00", "А".repeat(201)));
        problem(longTitle, 400, "INVALID_INPUT");

        // FR-10: межа 200 символів приймається
        var exactTitle = api("POST", "/api/v1/bookings", employee,
                bookingRequest(room, "2026-10-27T10:00", "2026-10-27T11:00", "Б".repeat(200)));
        assertEquals(201, exactTitle.statusCode());

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("FR-11")
    @Tag("FR-16")
    void bookingRejectsInvalidTimeIntervalsWithSpecificReasons() throws Exception {
        long room = TestDataBuilder.room().name("Час").insert();

        // Поза робочими годинами
        var tooEarly = api("POST", "/api/v1/bookings", employee,
                bookingRequest(room, "2026-10-27T07:45", "2026-10-27T08:15", "Рано"));
        problem(tooEarly, 422, "INVALID_TIME");
        assertEquals("OUTSIDE_WORK_HOURS", json(tooEarly).get("rule").asString());

        var tooLate = api("POST", "/api/v1/bookings", employee,
                bookingRequest(room, "2026-10-27T19:45", "2026-10-27T20:15", "Пізно"));
        problem(tooLate, 422, "INVALID_TIME");
        assertEquals("OUTSIDE_WORK_HOURS", json(tooLate).get("rule").asString());

        // Не кратне 15 хвилинам
        var notOnGrid = api("POST", "/api/v1/bookings", employee,
                bookingRequest(room, "2026-10-27T10:07", "2026-10-27T10:22", "Некратне"));
        problem(notOnGrid, 422, "INVALID_TIME");
        assertEquals("NOT_ON_GRID", json(notOnGrid).get("rule").asString());

        // Надто довге
        var tooLong = api("POST", "/api/v1/bookings", employee,
                bookingRequest(room, "2026-10-27T10:00", "2026-10-27T14:15", "Довге"));
        problem(tooLong, 422, "INVALID_TIME");
        assertEquals("BAD_DURATION", json(tooLong).get("rule").asString());

        // Нульова тривалість
        var zeroDuration = api("POST", "/api/v1/bookings", employee,
                bookingRequest(room, "2026-10-27T10:00", "2026-10-27T10:00", "Нуль"));
        problem(zeroDuration, 422, "INVALID_TIME");
        assertEquals("INTERVAL_SHAPE", json(zeroDuration).get("rule").asString());

        // Мінімум: 15 хвилин приймається
        var minimum = api("POST", "/api/v1/bookings", employee,
                bookingRequest(room, "2026-10-27T10:00", "2026-10-27T10:15", "Мінімум"));
        assertEquals(201, minimum.statusCode());

        // Максимум: 4 години приймаються
        var maximum = api("POST", "/api/v1/bookings", employee,
                bookingRequest(room, "2026-10-27T16:00", "2026-10-27T20:00", "Максимум"));
        assertEquals(201, maximum.statusCode());

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("FR-12")
    @Tag("FR-16")
    void bookingDetectsOverlapAndAllowsAdjacentIntervals() throws Exception {
        long room = TestDataBuilder.room().name("Перетини").insert();

        // Створюємо перше бронювання 10:00-11:00
        var first = api("POST", "/api/v1/bookings", employee,
                bookingRequest(room, "2026-10-27T10:00", "2026-10-27T11:00", "Перше"));
        assertEquals(201, first.statusCode());

        // Суміжний інтервал 11:00-12:00 приймається
        var adjacent = api("POST", "/api/v1/bookings", employee,
                bookingRequest(room, "2026-10-27T11:00", "2026-10-27T12:00", "Суміжне"));
        assertEquals(201, adjacent.statusCode());

        // Перетин 10:30-11:30 відхиляється
        var overlap = api("POST", "/api/v1/bookings", employee,
                bookingRequest(room, "2026-10-27T10:30", "2026-10-27T11:30", "Перетин"));
        problem(overlap, 409, "SLOT_TAKEN");

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("FR-13")
    void bookingRejectsStartInThePast() throws Exception {
        long room = TestDataBuilder.room().name("Минуле").insert();

        // Поточний час 08:07 UTC = 10:07 за Києвом, поточний слот 10:00
        clock.setInstant(Instant.parse("2026-10-27T08:07:00Z"));

        // Початок 10:00 (поточний слот) приймається
        var currentSlot = api("POST", "/api/v1/bookings", employee,
                bookingRequest(room, "2026-10-27T10:00", "2026-10-27T11:00", "Поточний"));
        assertEquals(201, currentSlot.statusCode());

        // Початок 09:45 (минулий слот) відхиляється
        var pastSlot = api("POST", "/api/v1/bookings", employee,
                bookingRequest(room, "2026-10-27T09:45", "2026-10-27T10:45", "Минуле"));
        problem(pastSlot, 422, "INVALID_TIME");
        assertEquals("IN_THE_PAST", json(pastSlot).get("rule").asString());

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("FR-14")
    void bookingRejectsStartBeyondHorizon() throws Exception {
        long room = TestDataBuilder.room().name("Горизонт").insert();

        // Поточна дата 2026-10-27, день 90 це 2027-01-25
        var day90 = api("POST", "/api/v1/bookings", employee,
                bookingRequest(room, "2027-01-25T10:00", "2027-01-25T11:00", "День 90"));
        assertEquals(201, day90.statusCode());

        // День 91 це 2027-01-26
        var day91 = api("POST", "/api/v1/bookings", employee,
                bookingRequest(room, "2027-01-26T10:00", "2027-01-26T11:00", "День 91"));
        problem(day91, 422, "INVALID_TIME");
        assertEquals("TOO_FAR", json(day91).get("rule").asString());

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("FR-15")
    @Tag("FR-16")
    void bookingRejectsInactiveRoom() throws Exception {
        long room = TestDataBuilder.room().name("Неактивна").active(false).insert();

        var response = api("POST", "/api/v1/bookings", employee,
                bookingRequest(room, "2026-10-27T10:00", "2026-10-27T11:00", "Спроба"));
        problem(response, 409, "ROOM_INACTIVE");

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("FR-16")
    void slotTakenConflictFreesSlotAfterCancellation() throws Exception {
        long room = TestDataBuilder.room().name("Звільнення").insert();

        // Створюємо бронювання
        var first = api("POST", "/api/v1/bookings", employee,
                bookingRequest(room, "2026-10-27T10:00", "2026-10-27T11:00", "Перше"));
        assertEquals(201, first.statusCode());
        long bookingId = json(first).get("id").asLong();

        // Перетин відхиляється
        var conflict = api("POST", "/api/v1/bookings", employee,
                bookingRequest(room, "2026-10-27T10:00", "2026-10-27T11:00", "Дублікат"));
        problem(conflict, 409, "SLOT_TAKEN");

        // Скасовуємо перше
        var cancel = api("POST", "/api/v1/bookings/" + bookingId + "/cancel", employee, "");
        assertEquals(200, cancel.statusCode());

        // Тепер слот вільний
        var afterCancel = api("POST", "/api/v1/bookings", employee,
                bookingRequest(room, "2026-10-27T10:00", "2026-10-27T11:00", "Після скасування"));
        assertEquals(201, afterCancel.statusCode());

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("FR-09")
    void authorIdInRequestIsIgnoredAndSessionAuthorIsUsed() throws Exception {
        long room = TestDataBuilder.room().name("Автор").insert();

        // Намагаємось передати authorId іншого користувача
        var body = mapper.writeValueAsString(Map.of(
                "roomId", room,
                "title", "Зустріч",
                "startAt", "2026-10-27T10:00",
                "endAt", "2026-10-27T11:00",
                "authorId", otherId  // Намагаємось підмінити автора
        ));

        var response = api("POST", "/api/v1/bookings", employee, body);
        assertEquals(201, response.statusCode());

        // Автор з сесії, не з запиту
        var booking = json(response);
        assertEquals(employeeId, booking.get("author").get("id").asLong());
        assertNotEquals(otherId, booking.get("author").get("id").asLong());

        InvariantAudit.assertAllInvariantsHold();
    }
}

package roombook.acceptance;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import roombook.testsupport.HttpTestSupport;
import roombook.testsupport.InvariantAudit;
import roombook.testsupport.TestDataBuilder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Наскрізний сценарій UC-04: адміністратор шукає й скасовує бронювання (T-25, TS-16).
 */
@Tag("TS-16")
@Tag("UC-04")
class UC04AdminCancelBookingTest extends AcceptanceTestSupport {

    @Test
    @Tag("FR-08")
    @Tag("FR-17")
    void adminFindsBookingsByFiltersAndCancelsAny() throws Exception {
        long room1 = TestDataBuilder.room().name("Кімната 1").insert();
        long room2 = TestDataBuilder.room().name("Кімната 2").insert();

        // Створюємо бронювання різних авторів
        var employeeBooking = api("POST", "/api/v1/bookings", employee,
                mapper.writeValueAsString(Map.of(
                        "roomId", room1, "title", "Працівник",
                        "startAt", "2026-10-27T10:00", "endAt", "2026-10-27T11:00"
                )));
        long employeeBookingId = json(employeeBooking).get("id").asLong();

        var otherBooking = api("POST", "/api/v1/bookings", other,
                mapper.writeValueAsString(Map.of(
                        "roomId", room2, "title", "Інший",
                        "startAt", "2026-10-27T14:00", "endAt", "2026-10-27T15:00"
                )));

        // EMPLOYEE не має доступу до загального списку
        var employeeList = api("GET", "/api/v1/bookings", employee, "");
        problem(employeeList, 403, "NOT_PERMITTED");

        // ADMIN бачить усі бронювання
        var allBookings = api("GET", "/api/v1/bookings", admin, "");
        assertEquals(200, allBookings.statusCode());
        assertTrue(json(allBookings).get("items").size() >= 2);

        // Фільтр за кімнатою
        var byRoom = api("GET", "/api/v1/bookings?roomId=" + room1, admin, "");
        assertEquals(200, byRoom.statusCode());
        assertEquals(1, json(byRoom).get("items").size());

        // Фільтр за автором
        var byAuthor = api("GET", "/api/v1/bookings?authorId=" + employeeId, admin, "");
        assertEquals(200, byAuthor.statusCode());
        assertEquals(1, json(byAuthor).get("items").size());

        // Фільтр за датою
        var byDate = api("GET", "/api/v1/bookings?dateFrom=2026-10-27&dateTo=2026-10-27", admin, "");
        assertEquals(200, byDate.statusCode());
        assertTrue(json(byDate).get("items").size() >= 2);

        // ADMIN скасовує чуже бронювання
        var cancel = api("POST", "/api/v1/bookings/" + employeeBookingId + "/cancel", admin, "");
        assertEquals(200, cancel.statusCode());
        assertEquals("CANCELLED", json(cancel).get("status").asString());

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("FR-02")
    void adminCanCreateAndCancelTheirOwnBookings() throws Exception {
        long room = TestDataBuilder.room().name("Адмін").insert();

        // ADMIN створює бронювання (має права EMPLOYEE)
        var adminBooking = api("POST", "/api/v1/bookings", admin,
                mapper.writeValueAsString(Map.of(
                        "roomId", room, "title", "Зустріч адміна",
                        "startAt", "2026-10-27T16:00", "endAt", "2026-10-27T17:00"
                )));
        assertEquals(201, adminBooking.statusCode());
        assertEquals(adminId, json(adminBooking).get("author").get("id").asLong());
        long bookingId = json(adminBooking).get("id").asLong();

        // ADMIN скасовує власне бронювання
        var cancel = api("POST", "/api/v1/bookings/" + bookingId + "/cancel", admin, "");
        assertEquals(200, cancel.statusCode());

        InvariantAudit.assertAllInvariantsHold();
    }
}

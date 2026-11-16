package roombook.acceptance;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import roombook.testsupport.HttpTestSupport;
import roombook.testsupport.InvariantAudit;
import roombook.testsupport.TestDataBuilder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Перевірка вимог перегляду FR-03...FR-08 (T-25, TS-16).
 */
@Tag("TS-16")
class ViewRequirementsTest extends AcceptanceTestSupport {

    @Test
    @Tag("FR-03")
    void userSeesOnlyActiveRoomsInList() throws Exception {
        long activeRoom = TestDataBuilder.room().name("Активна").active(true).insert();
        long inactiveRoom = TestDataBuilder.room().name("Неактивна").active(false).insert();

        var response = api("GET", "/api/v1/rooms", employee, "");
        assertEquals(200, response.statusCode());
        var rooms = json(response);

        // Активна кімната є в списку
        assertTrue(rooms.toString().contains("Активна"));
        // Неактивної кімнати немає
        assertFalse(rooms.toString().contains("Неактивна"));

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("FR-04")
    void userSeesOnlyActiveBookingsForDate() throws Exception {
        long room = TestDataBuilder.room().name("Статуси").insert();

        // Створюємо ACTIVE-бронювання
        var activeBody = mapper.writeValueAsString(Map.of(
                "roomId", room, "title", "Активне",
                "startAt", "2026-10-27T10:00", "endAt", "2026-10-27T11:00"
        ));
        var active = api("POST", "/api/v1/bookings", employee, activeBody);
        long activeId = json(active).get("id").asLong();

        // Створюємо друге й скасовуємо
        var cancelledBody = mapper.writeValueAsString(Map.of(
                "roomId", room, "title", "Скасоване",
                "startAt", "2026-10-27T14:00", "endAt", "2026-10-27T15:00"
        ));
        var cancelled = api("POST", "/api/v1/bookings", employee, cancelledBody);
        long cancelledId = json(cancelled).get("id").asLong();
        api("POST", "/api/v1/bookings/" + cancelledId + "/cancel", employee, "");

        // Зайнятість показує лише ACTIVE
        var occupancy = api("GET", "/api/v1/occupancy?date=2026-10-27", employee, "");
        assertEquals(200, occupancy.statusCode());
        var occupancyStr = json(occupancy).toString();
        assertTrue(occupancyStr.contains("Активне"));
        assertFalse(occupancyStr.contains("Скасоване"));

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("FR-05")
    void roomsCanBeFilteredByFloorAndCapacity() throws Exception {
        TestDataBuilder.room().name("Мала на 2").floor(2).capacity(4).insert();
        TestDataBuilder.room().name("Велика на 2").floor(2).capacity(10).insert();
        TestDataBuilder.room().name("Мала на 3").floor(3).capacity(4).insert();

        // Фільтр за поверхом
        var floor2 = api("GET", "/api/v1/rooms?floor=2", employee, "");
        assertEquals(200, floor2.statusCode());
        var floor2Rooms = json(floor2);
        assertEquals(2, floor2Rooms.size());
        assertTrue(floor2Rooms.toString().contains("на 2"));
        assertFalse(floor2Rooms.toString().contains("на 3"));

        // Фільтр за місткістю
        var capacity10 = api("GET", "/api/v1/rooms?minCapacity=10", employee, "");
        assertEquals(200, capacity10.statusCode());
        var largeRooms = json(capacity10);
        assertEquals(1, largeRooms.size());
        assertTrue(largeRooms.toString().contains("Велика"));

        // Комбінований фільтр
        var combined = api("GET", "/api/v1/rooms?floor=2&minCapacity=10", employee, "");
        assertEquals(200, combined.statusCode());
        assertEquals(1, json(combined).size());

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("FR-06")
    void freeRoomsSearchExcludesRoomsWithOverlappingBookings() throws Exception {
        long room1 = TestDataBuilder.room().name("Зайнята").insert();
        long room2 = TestDataBuilder.room().name("Вільна").insert();

        // Бронюємо room1 на 10:00-11:00
        api("POST", "/api/v1/bookings", employee,
                mapper.writeValueAsString(Map.of(
                        "roomId", room1, "title", "Зайнята",
                        "startAt", "2026-10-27T10:00", "endAt", "2026-10-27T11:00"
                )));

        // Шукаємо вільні на 10:30-11:30 (перетин)
        var response = api("GET", "/api/v1/rooms?freeFrom=2026-10-27T10:30&freeTo=2026-10-27T11:30", employee, "");
        assertEquals(200, response.statusCode());
        var freeRooms = json(response);

        // room2 вільна, room1 зайнята
        assertTrue(freeRooms.toString().contains("Вільна"));
        assertFalse(freeRooms.toString().contains("Зайнята"));

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("FR-07")
    void myBookingsShowsOnlyOwnWithoutOthers() throws Exception {
        long room = TestDataBuilder.room().name("Фільтр").insert();

        // employee створює бронювання
        api("POST", "/api/v1/bookings", employee,
                mapper.writeValueAsString(Map.of(
                        "roomId", room, "title", "Моє",
                        "startAt", "2026-10-27T10:00", "endAt", "2026-10-27T11:00"
                )));

        // other створює бронювання
        api("POST", "/api/v1/bookings", other,
                mapper.writeValueAsString(Map.of(
                        "roomId", room, "title", "Чуже",
                        "startAt", "2026-10-27T14:00", "endAt", "2026-10-27T15:00"
                )));

        // employee бачить лише своє
        var myBookings = api("GET", "/api/v1/me/bookings", employee, "");
        assertEquals(200, myBookings.statusCode());
        var items = json(myBookings).get("items");
        assertEquals(1, items.size());
        assertEquals("Моє", items.get(0).get("title").asString());

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("FR-08")
    void adminCanFilterAllBookingsByMultipleCriteria() throws Exception {
        long room1 = TestDataBuilder.room().name("Кімната 1").insert();
        long room2 = TestDataBuilder.room().name("Кімната 2").insert();

        // employee створює в кімнаті 1
        var emp1 = api("POST", "/api/v1/bookings", employee,
                mapper.writeValueAsString(Map.of(
                        "roomId", room1, "title", "Працівник в 1",
                        "startAt", "2026-10-27T10:00", "endAt", "2026-10-27T11:00"
                )));
        long emp1Id = json(emp1).get("id").asLong();

        // other створює в кімнаті 2
        api("POST", "/api/v1/bookings", other,
                mapper.writeValueAsString(Map.of(
                        "roomId", room2, "title", "Інший в 2",
                        "startAt", "2026-10-27T14:00", "endAt", "2026-10-27T15:00"
                )));

        // Скасовуємо перше
        api("POST", "/api/v1/bookings/" + emp1Id + "/cancel", admin, "");

        // ADMIN фільтрує за статусом ACTIVE
        var activeOnly = api("GET", "/api/v1/bookings?status=ACTIVE", admin, "");
        assertEquals(200, activeOnly.statusCode());
        assertEquals(1, json(activeOnly).get("items").size());

        // Фільтр за статусом CANCELLED
        var cancelledOnly = api("GET", "/api/v1/bookings?status=CANCELLED", admin, "");
        assertEquals(200, cancelledOnly.statusCode());
        assertEquals(1, json(cancelledOnly).get("items").size());

        // Фільтр за кімнатою і автором
        var filtered = api("GET", "/api/v1/bookings?roomId=" + room1 + "&authorId=" + employeeId, admin, "");
        assertEquals(200, filtered.statusCode());
        assertEquals(1, json(filtered).get("items").size());

        InvariantAudit.assertAllInvariantsHold();
    }
}

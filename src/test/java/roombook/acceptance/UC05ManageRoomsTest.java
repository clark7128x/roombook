package roombook.acceptance;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import roombook.testsupport.HttpTestSupport;
import roombook.testsupport.InvariantAudit;
import roombook.testsupport.TestDataBuilder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Наскрізний сценарій UC-05: управління кімнатами (T-25, TS-16).
 */
@Tag("TS-16")
@Tag("UC-05")
class UC05ManageRoomsTest extends AcceptanceTestSupport {

    @Test
    @Tag("FR-19")
    @Tag("FR-20")
    @Tag("FR-22")
    void adminCreatesModifiesAndReactivatesRoom() throws Exception {
        // EMPLOYEE не має прав створювати кімнати
        var employeeCreate = api("POST", "/api/v1/rooms", employee,
                mapper.writeValueAsString(Map.of(
                        "name", "Спроба", "floor", 2, "capacity", 6
                )));
        problem(employeeCreate, 403, "NOT_PERMITTED");

        // ADMIN створює кімнату
        var create = api("POST", "/api/v1/rooms", admin,
                mapper.writeValueAsString(Map.of(
                        "name", "Нова кімната", "floor", 3, "capacity", 10,
                        "equipment", "Проєктор, дошка"
                )));
        assertEquals(201, create.statusCode());
        var room = json(create);
        assertTrue(room.get("active").asBoolean());
        long roomId = room.get("id").asLong();

        // FR-20: змінює атрибути
        var update = api("PUT", "/api/v1/rooms/" + roomId, admin,
                mapper.writeValueAsString(Map.of(
                        "name", "Перейменована", "floor", 4, "capacity", 12,
                        "equipment", "Проєктор, дошка, телевізор"
                )));
        assertEquals(200, update.statusCode());
        assertEquals("Перейменована", json(update).get("name").asString());
        assertEquals(4, json(update).get("floor").asInt());

        // Деактивує
        var deactivate = api("POST", "/api/v1/rooms/" + roomId + "/deactivate", admin, "");
        assertEquals(200, deactivate.statusCode());
        assertFalse(json(deactivate).get("active").asBoolean());

        // FR-22: повторно активує
        var activate = api("POST", "/api/v1/rooms/" + roomId + "/activate", admin, "");
        assertEquals(200, activate.statusCode());
        assertTrue(json(activate).get("active").asBoolean());

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("FR-19")
    void roomNameMustBeUniqueIgnoringCaseAndWhitespace() throws Exception {
        // Створюємо кімнату «Альфа»
        api("POST", "/api/v1/rooms", admin,
                mapper.writeValueAsString(Map.of(
                        "name", "Альфа", "floor", 1, "capacity", 4
                )));

        // Дублікат з іншим регістром і пробілами
        var duplicate = api("POST", "/api/v1/rooms", admin,
                mapper.writeValueAsString(Map.of(
                        "name", " альфа ", "floor", 1, "capacity", 4
                )));
        problem(duplicate, 409, "ROOM_NAME_TAKEN");

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("FR-20")
    void renamingRoomDoesNotAffectExistingBookings() throws Exception {
        long room = TestDataBuilder.room().name("Оригінальна назва").insert();

        // Створюємо бронювання
        var booking = api("POST", "/api/v1/bookings", employee,
                mapper.writeValueAsString(Map.of(
                        "roomId", room, "title", "Зустріч",
                        "startAt", "2026-10-27T10:00", "endAt", "2026-10-27T11:00"
                )));
        long bookingId = json(booking).get("id").asLong();

        // Перейменовуємо кімнату
        api("PUT", "/api/v1/rooms/" + room, admin,
                mapper.writeValueAsString(Map.of(
                        "name", "Нова назва", "floor", 1, "capacity", 4
                )));

        // Бронювання лишається
        var check = api("GET", "/api/v1/bookings/" + bookingId, employee, "");
        assertEquals(200, check.statusCode());
        assertEquals("Нова назва", json(check).get("room").get("name").asString());

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("FR-21")
    void roomWithUnfinishedBookingsCannotBeDeactivated() throws Exception {
        long room = TestDataBuilder.room().name("З бронюванням").insert();

        // Створюємо бронювання на завтра
        api("POST", "/api/v1/bookings", employee,
                mapper.writeValueAsString(Map.of(
                        "roomId", room, "title", "Завтра",
                        "startAt", "2026-10-28T10:00", "endAt", "2026-10-28T11:00"
                )));

        // Спроба деактивувати відхиляється
        var deactivate = api("POST", "/api/v1/rooms/" + room + "/deactivate", admin, "");
        problem(deactivate, 409, "ROOM_HAS_UNFINISHED_BOOKINGS");

        // Після скасування бронювання деактивація проходить
        var bookings = api("GET", "/api/v1/bookings?roomId=" + room, admin, "");
        long bookingId = json(bookings).get("items").get(0).get("id").asLong();
        api("POST", "/api/v1/bookings/" + bookingId + "/cancel", admin, "");

        var deactivateAfterCancel = api("POST", "/api/v1/rooms/" + room + "/deactivate", admin, "");
        assertEquals(200, deactivateAfterCancel.statusCode());

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("FR-19")
    void roomFieldsValidation() throws Exception {
        // Місткість 0 відхиляється
        var zeroCapacity = api("POST", "/api/v1/rooms", admin,
                mapper.writeValueAsString(Map.of(
                        "name", "Нульова", "floor", 1, "capacity", 0
                )));
        problem(zeroCapacity, 400, "INVALID_INPUT");

        // Обладнання понад 500 символів
        var longEquipment = api("POST", "/api/v1/rooms", admin,
                mapper.writeValueAsString(Map.of(
                        "name", "Довге", "floor", 1, "capacity", 4,
                        "equipment", "А".repeat(501)
                )));
        problem(longEquipment, 400, "INVALID_INPUT");

        // 500 символів приймається
        var maxEquipment = api("POST", "/api/v1/rooms", admin,
                mapper.writeValueAsString(Map.of(
                        "name", "Межа", "floor", 1, "capacity", 4,
                        "equipment", "Б".repeat(500)
                )));
        assertEquals(201, maxEquipment.statusCode());

        InvariantAudit.assertAllInvariantsHold();
    }
}

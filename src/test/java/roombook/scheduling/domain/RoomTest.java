package roombook.scheduling.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import roombook.kernel.DomainException;
import roombook.kernel.FieldError;
import roombook.kernel.FieldRule;
import roombook.kernel.Reason;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тести сутності Room (T-09, TS-03, INV-11, INV-12, FR-19...FR-22, A-06, A-14, A-15, A-17, A-25, A-36).
 */
@Tag("TS-03")
@Tag("INV-11")
@Tag("INV-12")
@Tag("FR-19")
@Tag("FR-20")
@Tag("FR-21")
@Tag("FR-22")
@Tag("A-06")
@Tag("A-14")
@Tag("A-15")
@Tag("A-17")
@Tag("A-25")
@Tag("A-36")
class RoomTest {

    @Test
    @DisplayName("Успішне створення кімнати: active за замовчуванням true, ключ розраховано, equipment нормалізовано")
    void createRoomSuccess() {
        Room room = new Room("  Конференц-зал 1  ", 3, 25, "  Проектор, фліпчарт  ");

        assertEquals("Конференц-зал 1", room.getName());
        assertEquals("конференц-зал 1", room.getNameKey());
        assertEquals(3, room.getFloor());
        assertEquals(25, room.getCapacity());
        assertEquals("Проектор, фліпчарт", room.getEquipment());
        assertTrue(room.isActive(), "Нова кімната активна за замовчуванням");
    }

    @Test
    @DisplayName("TS-03: Граничні значення floor (-5, 100 — валідні; -6, 101 — відхиляються)")
    void floorBoundaries() {
        // -5 (нижня межа) — валідно
        Room rMin = new Room("Room Low", -5, 10, null);
        assertEquals(-5, rMin.getFloor());

        // 100 (верхня межа) — валідно
        Room rMax = new Room("Room High", 100, 10, null);
        assertEquals(100, rMax.getFloor());

        // -6 — відхиляється
        DomainException exLow = assertThrows(DomainException.class, () ->
                new Room("Room Invalid", -6, 10, null));
        assertEquals(Reason.INVALID_INPUT, exLow.getReason());
        assertTrue(exLow.getFieldErrors().stream()
                .anyMatch(fe -> fe.field().equals("floor") && fe.rule() == FieldRule.RANGE));

        // 101 — відхиляється
        DomainException exHigh = assertThrows(DomainException.class, () ->
                new Room("Room Invalid", 101, 10, null));
        assertEquals(Reason.INVALID_INPUT, exHigh.getReason());
        assertTrue(exHigh.getFieldErrors().stream()
                .anyMatch(fe -> fe.field().equals("floor") && fe.rule() == FieldRule.RANGE));
    }

    @Test
    @DisplayName("TS-03: Граничні значення capacity (1, 1000 — валідні; 0, 1001 — відхиляються)")
    void capacityBoundaries() {
        // 1 (нижня межа) — валідно
        Room rMin = new Room("Booth", 1, 1, null);
        assertEquals(1, rMin.getCapacity());

        // 1000 (верхня межа) — валідно
        Room rMax = new Room("Auditorium", 1, 1000, null);
        assertEquals(1000, rMax.getCapacity());

        // 0 — відхиляється
        DomainException exZero = assertThrows(DomainException.class, () ->
                new Room("Invalid Room", 1, 0, null));
        assertEquals(Reason.INVALID_INPUT, exZero.getReason());
        assertTrue(exZero.getFieldErrors().stream()
                .anyMatch(fe -> fe.field().equals("capacity") && fe.rule() == FieldRule.RANGE));

        // 1001 — відхиляється
        DomainException exHigh = assertThrows(DomainException.class, () ->
                new Room("Invalid Room", 1, 1001, null));
        assertEquals(Reason.INVALID_INPUT, exHigh.getReason());
        assertTrue(exHigh.getFieldErrors().stream()
                .anyMatch(fe -> fe.field().equals("capacity") && fe.rule() == FieldRule.RANGE));
    }

    @Test
    @DisplayName("TS-03: Граничні значення name (100 символів — валідно; 101 або порожня після strip — відхиляється)")
    void nameBoundaries() {
        // Порожня або лише пробіли -> REQUIRED
        DomainException exBlank = assertThrows(DomainException.class, () ->
                new Room("   \u00A0  ", 1, 10, null));
        assertEquals(Reason.INVALID_INPUT, exBlank.getReason());
        assertTrue(exBlank.getFieldErrors().stream()
                .anyMatch(fe -> fe.field().equals("name") && fe.rule() == FieldRule.REQUIRED));

        // 100 символів -> валідно
        String name100 = "К".repeat(100);
        Room r100 = new Room(name100, 1, 10, null);
        assertEquals(100, r100.getName().length());

        // 101 символ -> LENGTH
        String name101 = "К".repeat(101);
        DomainException ex101 = assertThrows(DomainException.class, () ->
                new Room(name101, 1, 10, null));
        assertEquals(Reason.INVALID_INPUT, ex101.getReason());
        assertTrue(ex101.getFieldErrors().stream()
                .anyMatch(fe -> fe.field().equals("name") && fe.rule() == FieldRule.LENGTH));

        // Керувальні символи у name відхиляються
        DomainException exCtrl = assertThrows(DomainException.class, () ->
                new Room("Room\u0007Bell", 1, 10, null));
        assertEquals(Reason.INVALID_INPUT, exCtrl.getReason());
        assertTrue(exCtrl.getFieldErrors().stream()
                .anyMatch(fe -> fe.field().equals("name") && fe.rule() == FieldRule.ILLEGAL_CHARACTERS));
    }

    @Test
    @DisplayName("TS-03: Граничні значення equipment (500 символів — валідно, 501 — відхиляється, порожнє дає NULL, перенос рядка дозволений)")
    void equipmentBoundaries() {
        // Порожнє або лише пробіли стає null (A-25)
        Room rEmpty1 = new Room("Room 1", 1, 10, "");
        assertNull(rEmpty1.getEquipment());

        Room rEmpty2 = new Room("Room 2", 1, 10, "   \t  \n  ");
        assertNull(rEmpty2.getEquipment());

        // 500 символів -> валідно
        String eq500 = "E".repeat(500);
        Room r500 = new Room("Room 500", 1, 10, eq500);
        assertEquals(500, r500.getEquipment().length());

        // 501 символ -> LENGTH
        String eq501 = "E".repeat(501);
        DomainException ex501 = assertThrows(DomainException.class, () ->
                new Room("Room 501", 1, 10, eq501));
        assertEquals(Reason.INVALID_INPUT, ex501.getReason());
        assertTrue(ex501.getFieldErrors().stream()
                .anyMatch(fe -> fe.field().equals("equipment") && fe.rule() == FieldRule.LENGTH));

        // Перенос рядка в equipment дозволений (A-36)
        Room rMultiline = new Room("Room Multiline", 1, 10, "First line\nSecond line\r\nThird line");
        assertEquals("First line\nSecond line\r\nThird line", rMultiline.getEquipment());

        // Заборонені керувальні символи (крім \n і \r) відхиляються
        DomainException exCtrl = assertThrows(DomainException.class, () ->
                new Room("Room Eq Ctrl", 1, 10, "TV\u0000Screen"));
        assertEquals(Reason.INVALID_INPUT, exCtrl.getReason());
        assertTrue(exCtrl.getFieldErrors().stream()
                .anyMatch(fe -> fe.field().equals("equipment") && fe.rule() == FieldRule.ILLEGAL_CHARACTERS));
    }

    @Test
    @DisplayName("TS-03: Оновлення кімнати перераховує name_key і не змінює active")
    void updateRecalculatesNameKeyAndPreservesActive() {
        Room room = new Room("Alpha", 1, 10, "TV");
        assertEquals("alpha", room.getNameKey());
        assertTrue(room.isActive());

        // Деактивуємо кімнату
        room.deactivate();
        assertFalse(room.isActive());

        // Оновлюємо назву та параметри
        room.update("  БЕТА  ", 2, 20, "Projector");
        assertEquals("БЕТА", room.getName());
        assertEquals("бета", room.getNameKey(), "Ключ назви перераховано при зміні назви");
        assertEquals(2, room.getFloor());
        assertEquals(20, room.getCapacity());
        assertEquals("Projector", room.getEquipment());
        assertFalse(room.isActive(), "Поле active не змінилося при виклику update (FR-20)");
    }

    @Test
    @DisplayName("TS-03: Повторні activate та deactivate є безпечними і не змінюють стан (A-17)")
    void activateAndDeactivateAreIdempotent() {
        Room room = new Room("Omega", 1, 10, null);
        assertTrue(room.isActive());

        // Повторна активація вже активної кімнати
        room.activate();
        assertTrue(room.isActive());

        // Деактивація
        room.deactivate();
        assertFalse(room.isActive());

        // Повторна деактивація вже неактивної кімнати
        room.deactivate();
        assertFalse(room.isActive());

        // Активація знову
        room.activate();
        assertTrue(room.isActive());
    }

    @Test
    @DisplayName("Помилки полів накопичуються при створенні та оновленні (FieldError)")
    void fieldErrorsAccumulatedInRoom() {
        DomainException ex = assertThrows(DomainException.class, () ->
                new Room("   ", -10, 0, "A".repeat(600)));

        assertEquals(Reason.INVALID_INPUT, ex.getReason());
        List<FieldError> errors = ex.getFieldErrors();
        assertTrue(errors.stream().anyMatch(e -> e.field().equals("name") && e.rule() == FieldRule.REQUIRED));
        assertTrue(errors.stream().anyMatch(e -> e.field().equals("floor") && e.rule() == FieldRule.RANGE));
        assertTrue(errors.stream().anyMatch(e -> e.field().equals("capacity") && e.rule() == FieldRule.RANGE));
        assertTrue(errors.stream().anyMatch(e -> e.field().equals("equipment") && e.rule() == FieldRule.LENGTH));
    }
}

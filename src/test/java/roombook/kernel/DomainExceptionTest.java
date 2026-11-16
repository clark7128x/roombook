package roombook.kernel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("A-30")
@Tag("A-33")
@Tag("A-56")
class DomainExceptionTest {

    @Test
    @DisplayName("A-30: Створення DomainException з кодом Reason")
    void createWithReasonOnly() {
        DomainException ex = new DomainException(Reason.ROOM_NOT_FOUND);

        assertEquals(Reason.ROOM_NOT_FOUND, ex.getReason());
        assertNull(ex.getRule());
        assertTrue(ex.getErrors().isEmpty());
        assertTrue(ex.getMessageParams().isEmpty());
        assertEquals("ROOM_NOT_FOUND", ex.getMessage());
    }

    @Test
    @DisplayName("A-33: Створення DomainException для INVALID_TIME з TimeRule та параметрами")
    void createInvalidTimeWithRuleAndParams() {
        Map<String, Object> params = Map.of("workDayStart", "08:00", "workDayEnd", "20:00");
        DomainException ex = DomainException.invalidTime(TimeRule.OUTSIDE_WORK_HOURS, params);

        assertEquals(Reason.INVALID_TIME, ex.getReason());
        assertEquals("OUTSIDE_WORK_HOURS", ex.getRule());
        assertTrue(ex.getErrors().isEmpty());
        assertEquals(params, ex.getMessageParams());
        assertTrue(ex.getMessage().contains("INVALID_TIME"));
        assertTrue(ex.getMessage().contains("OUTSIDE_WORK_HOURS"));
    }

    @Test
    @DisplayName("A-56: Створення DomainException для INVALID_INPUT зі списком FieldError")
    void createInvalidInputWithFieldErrors() {
        FieldError error1 = new FieldError("title", FieldRule.REQUIRED);
        FieldError error2 = new FieldError("capacity", FieldRule.RANGE, Map.of("min", 1, "max", 100));

        DomainException ex = DomainException.invalidInput(List.of(error1, error2));

        assertEquals(Reason.INVALID_INPUT, ex.getReason());
        assertNull(ex.getRule());
        assertEquals(2, ex.getErrors().size());
        assertEquals("title", ex.getErrors().get(0).field());
        assertEquals(FieldRule.REQUIRED, ex.getErrors().get(0).rule());
        assertTrue(ex.getErrors().get(0).params().isEmpty());

        assertEquals("capacity", ex.getErrors().get(1).field());
        assertEquals(FieldRule.RANGE, ex.getErrors().get(1).rule());
        assertEquals(1, ex.getErrors().get(1).params().get("min"));
        assertEquals(100, ex.getErrors().get(1).params().get("max"));
    }

    @Test
    @DisplayName("A-56: Фабричні методи для одного поля invalidInput")
    void createInvalidInputSingleField() {
        DomainException ex = DomainException.invalidInput("name", FieldRule.LENGTH, Map.of("max", 200));

        assertEquals(Reason.INVALID_INPUT, ex.getReason());
        assertEquals(1, ex.getErrors().size());
        assertEquals("name", ex.getErrors().get(0).field());
        assertEquals(FieldRule.LENGTH, ex.getErrors().get(0).rule());
        assertEquals(200, ex.getErrors().get(0).params().get("max"));
    }

    @Test
    @DisplayName("DomainException вимагає ненульовий Reason")
    void nullReasonThrowsException() {
        assertThrows(NullPointerException.class, () -> new DomainException(null));
    }
}

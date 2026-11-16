package roombook.scheduling.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тести валідації параметрів BookingRules (T-08, OPS-09, INV-03...INV-05).
 */
@Tag("OPS-09")
@Tag("INV-03")
@Tag("INV-04")
@Tag("INV-05")
class BookingRulesTest {

    @Test
    @DisplayName("Конфігурація за замовчуванням є повністю валідною")
    void defaultRulesAreValid() {
        BookingRules rules = BookingRules.defaultRules();
        assertNotNull(rules);
        assertEquals(LocalTime.of(8, 0), rules.workDayStart());
        assertEquals(LocalTime.of(20, 0), rules.workDayEnd());
        assertEquals(Duration.ofMinutes(15), rules.slotGrid());
        assertEquals(Duration.ofMinutes(15), rules.minDuration());
        assertEquals(Duration.ofHours(4), rules.maxDuration());
        assertEquals(90, rules.horizonDays());
    }

    @Test
    @DisplayName("Приймання 4: Відхилення, якщо slotGrid нульовий або від'ємний")
    void rejectsInvalidSlotGrid() {
        IllegalArgumentException exZero = assertThrows(IllegalArgumentException.class, () ->
                new BookingRules(LocalTime.of(8, 0), LocalTime.of(20, 0), Duration.ZERO,
                        Duration.ofMinutes(15), Duration.ofHours(4), 90));
        assertTrue(exZero.getMessage().contains("slotGrid"));

        IllegalArgumentException exNeg = assertThrows(IllegalArgumentException.class, () ->
                new BookingRules(LocalTime.of(8, 0), LocalTime.of(20, 0), Duration.ofMinutes(-15),
                        Duration.ofMinutes(15), Duration.ofHours(4), 90));
        assertTrue(exNeg.getMessage().contains("slotGrid"));
    }

    @Test
    @DisplayName("Приймання 4: Відхилення, якщо workDayStart пізніше або дорівнює workDayEnd")
    void rejectsWorkDayStartNotBeforeEnd() {
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
                new BookingRules(LocalTime.of(20, 0), LocalTime.of(8, 0), Duration.ofMinutes(15),
                        Duration.ofMinutes(15), Duration.ofHours(4), 90));
        assertTrue(ex1.getMessage().contains("workDayStart"));

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () ->
                new BookingRules(LocalTime.of(12, 0), LocalTime.of(12, 0), Duration.ofMinutes(15),
                        Duration.ofMinutes(15), Duration.ofHours(4), 90));
        assertTrue(ex2.getMessage().contains("workDayStart"));
    }

    @Test
    @DisplayName("Приймання 4: Відхилення, якщо межі робочого дня не кратні slotGrid")
    void rejectsWorkDayNotAlignedWithSlotGrid() {
        // slotGrid 15 хв, але старт о 08:07
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
                new BookingRules(LocalTime.of(8, 7), LocalTime.of(20, 0), Duration.ofMinutes(15),
                        Duration.ofMinutes(15), Duration.ofHours(4), 90));
        assertTrue(ex1.getMessage().contains("workDayStart"));

        // slotGrid 15 хв, але кінець о 20:05
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () ->
                new BookingRules(LocalTime.of(8, 0), LocalTime.of(20, 5), Duration.ofMinutes(15),
                        Duration.ofMinutes(15), Duration.ofHours(4), 90));
        assertTrue(ex2.getMessage().contains("workDayEnd"));
    }

    @Test
    @DisplayName("Приймання 4: Відхилення, якщо minDuration більше maxDuration або не додатна")
    void rejectsInvalidMinMaxDuration() {
        // minDuration (5 годин) > maxDuration (4 години)
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
                new BookingRules(LocalTime.of(8, 0), LocalTime.of(20, 0), Duration.ofMinutes(15),
                        Duration.ofHours(5), Duration.ofHours(4), 90));
        assertTrue(ex1.getMessage().contains("minDuration"));

        // minDuration = 0
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () ->
                new BookingRules(LocalTime.of(8, 0), LocalTime.of(20, 0), Duration.ofMinutes(15),
                        Duration.ZERO, Duration.ofHours(4), 90));
        assertTrue(ex2.getMessage().contains("minDuration"));
    }

    @Test
    @DisplayName("Приймання 4: Відхилення, якщо minDuration або maxDuration не кратні slotGrid")
    void rejectsDurationNotAlignedWithSlotGrid() {
        // slotGrid 15 хв, але minDuration 10 хв
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () ->
                new BookingRules(LocalTime.of(8, 0), LocalTime.of(20, 0), Duration.ofMinutes(15),
                        Duration.ofMinutes(10), Duration.ofHours(4), 90));
        assertTrue(ex1.getMessage().contains("minDuration"));

        // slotGrid 15 хв, але maxDuration 70 хв (1 год 10 хв)
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () ->
                new BookingRules(LocalTime.of(8, 0), LocalTime.of(20, 0), Duration.ofMinutes(15),
                        Duration.ofMinutes(15), Duration.ofMinutes(70), 90));
        assertTrue(ex2.getMessage().contains("maxDuration"));
    }

    @Test
    @DisplayName("Відхилення від'ємного горизонту планування horizonDays")
    void rejectsNegativeHorizonDays() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new BookingRules(LocalTime.of(8, 0), LocalTime.of(20, 0), Duration.ofMinutes(15),
                        Duration.ofMinutes(15), Duration.ofHours(4), -1));
        assertTrue(ex.getMessage().contains("horizonDays"));
    }
}

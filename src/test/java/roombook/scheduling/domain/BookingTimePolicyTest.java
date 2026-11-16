package roombook.scheduling.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import roombook.kernel.DomainException;
import roombook.kernel.OfficeClock;
import roombook.kernel.Reason;
import roombook.kernel.TimeRule;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Тести політики часу бронювання (T-08, TS-01, TS-05, INV-02...INV-05, FR-11, FR-13, FR-14, TR-04, TR-06).
 */
@Tag("TS-01")
@Tag("TS-05")
@Tag("INV-02")
@Tag("INV-03")
@Tag("INV-04")
@Tag("INV-05")
@Tag("FR-11")
@Tag("FR-13")
@Tag("FR-14")
@Tag("TR-04")
class BookingTimePolicyTest {

    private final BookingRules rules = BookingRules.defaultRules(); // 08:00-20:00, grid 15m, min 15m, max 4h, horizon 90d
    private final BookingTimePolicy policy = new BookingTimePolicy(rules);
    private final ZoneId kyivZone = ZoneId.of("Europe/Kyiv");

    private OfficeClock.Snapshot createSnapshot(LocalDateTime localDateTime) {
        Instant instant = localDateTime.atZone(kyivZone).toInstant();
        Clock clock = Clock.fixed(instant, kyivZone);
        OfficeClock officeClock = new OfficeClock(clock, kyivZone);
        return officeClock.snapshot();
    }

    @Test
    @DisplayName("Приймання 1: Приклади FR-11 дають відповідні підкоди INVALID_TIME")
    void fr11ExamplesProduceExactTimeRules() {
        LocalDate date = LocalDate.of(2026, 11, 2);

        // 1. INTERVAL_SHAPE: 10:00 - 10:00 (start >= end)
        DomainException exShape1 = assertThrows(DomainException.class, () ->
                policy.validateForSearch(date.atTime(10, 0), date.atTime(10, 0)));
        assertEquals(Reason.INVALID_TIME, exShape1.getReason());
        assertEquals(TimeRule.INTERVAL_SHAPE, exShape1.getTimeRule());

        // INTERVAL_SHAPE: кінець раніше початку (11:00 - 10:00)
        DomainException exShape2 = assertThrows(DomainException.class, () ->
                policy.validateForSearch(date.atTime(11, 0), date.atTime(10, 0)));
        assertEquals(TimeRule.INTERVAL_SHAPE, exShape2.getTimeRule());

        // INTERVAL_SHAPE: перехід через північ (різні дати)
        DomainException exShape3 = assertThrows(DomainException.class, () ->
                policy.validateForSearch(date.atTime(23, 0), date.plusDays(1).atTime(1, 0)));
        assertEquals(TimeRule.INTERVAL_SHAPE, exShape3.getTimeRule());

        // 2. OUTSIDE_WORK_HOURS: 07:45 - 08:15 (робочий день з 08:00)
        DomainException exWorkHours1 = assertThrows(DomainException.class, () ->
                policy.validateForSearch(date.atTime(7, 45), date.atTime(8, 15)));
        assertEquals(TimeRule.OUTSIDE_WORK_HOURS, exWorkHours1.getTimeRule());

        // OUTSIDE_WORK_HOURS: 19:45 - 20:15 (робочий день до 20:00)
        DomainException exWorkHours2 = assertThrows(DomainException.class, () ->
                policy.validateForSearch(date.atTime(19, 45), date.atTime(20, 15)));
        assertEquals(TimeRule.OUTSIDE_WORK_HOURS, exWorkHours2.getTimeRule());

        // 3. NOT_ON_GRID: 10:07 - 10:22 (не кратно сітці 15 хвилин)
        DomainException exGrid = assertThrows(DomainException.class, () ->
                policy.validateForSearch(date.atTime(10, 7), date.atTime(10, 22)));
        assertEquals(TimeRule.NOT_ON_GRID, exGrid.getTimeRule());

        // 4. BAD_DURATION: 10:00 - 14:15 (4 години 15 хвилин, перевищує maxDuration 4 години)
        DomainException exDurationMax = assertThrows(DomainException.class, () ->
                policy.validateForSearch(date.atTime(10, 0), date.atTime(14, 15)));
        assertEquals(TimeRule.BAD_DURATION, exDurationMax.getTimeRule());

        // Валідний інтервал: 10:00 - 11:00
        assertDoesNotThrow(() -> policy.validateForSearch(date.atTime(10, 0), date.atTime(11, 0)));
    }

    @Test
    @DisplayName("Приймання 1 і TS-05 Випадок 4: Момент 10:07 — початок 10:00 приймається, 09:45 відхиляється з IN_THE_PAST")
    void ts05Case4CurrentSlotStartAllowsTenAmRejectsNineFortyFive() {
        LocalDate date = LocalDate.of(2026, 11, 2);
        // Поточний час 10:07
        OfficeClock.Snapshot snapshot = createSnapshot(date.atTime(10, 7));

        // Початок 10:00 (поточний слот 10:00-10:15) — успішно приймається (FR-13)
        assertDoesNotThrow(() ->
                policy.validateForCreation(date.atTime(10, 0), date.atTime(11, 0), snapshot));

        // Початок 09:45 (попередній слот, минуле) — відхиляється з IN_THE_PAST (FR-13)
        DomainException exPast = assertThrows(DomainException.class, () ->
                policy.validateForCreation(date.atTime(9, 45), date.atTime(10, 45), snapshot));
        assertEquals(Reason.INVALID_TIME, exPast.getReason());
        assertEquals(TimeRule.IN_THE_PAST, exPast.getTimeRule());
    }

    @Test
    @DisplayName("Приймання 1: Перевірка горизонту планування FR-14 (день 90 приймається, день 91 відхиляється з TOO_FAR)")
    void fr14HorizonValidation() {
        LocalDate today = LocalDate.of(2026, 11, 2);
        OfficeClock.Snapshot snapshot = createSnapshot(today.atTime(10, 0));

        // День 90 (today + 90) приймається
        LocalDate day90 = today.plusDays(90);
        assertDoesNotThrow(() ->
                policy.validateForCreation(day90.atTime(10, 0), day90.atTime(11, 0), snapshot));

        // День 91 (today + 91) відхиляється з TOO_FAR
        LocalDate day91 = today.plusDays(91);
        DomainException exTooFar = assertThrows(DomainException.class, () ->
                policy.validateForCreation(day91.atTime(10, 0), day91.atTime(11, 0), snapshot));
        assertEquals(Reason.INVALID_TIME, exTooFar.getReason());
        assertEquals(TimeRule.TOO_FAR, exTooFar.getTimeRule());
    }

    @Test
    @DisplayName("Приймання 3 і TS-05 Випадок 5: Доба весняного переходу 29 березня 2026 — 08:00-09:00 приймається, тривалість 1 година")
    void ts05Case5SpringTransitionDay() {
        LocalDate springTransitionDate = LocalDate.of(2026, 3, 29);
        OfficeClock.Snapshot snapshot = createSnapshot(springTransitionDate.atTime(7, 30));

        // Бронювання 08:00 - 09:00 у добу весняного переходу
        LocalDateTime start = springTransitionDate.atTime(8, 0);
        LocalDateTime end = springTransitionDate.atTime(9, 0);

        assertDoesNotThrow(() -> policy.validateForCreation(start, end, snapshot));

        BookingInterval interval = new BookingInterval(start, end);
        assertEquals(Duration.ofHours(1), interval.duration(), "Тривалість інтервалу безпосередньо за різницею годин");
    }
}

package roombook.scheduling.domain;

import roombook.kernel.DomainException;
import roombook.kernel.OfficeClock;
import roombook.kernel.TimeRule;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Політика валідації часу бронювання (INV-02...INV-05, FR-13, FR-14, TR-04, TR-05, A-32).
 * Має два входи:
 * 1. Форма інтервалу (INV-02...INV-05) для пошуку вільних кімнат (validateForSearch).
 * 2. Створення додає перевірки IN_THE_PAST та TOO_FAR (validateForCreation).
 */
public final class BookingTimePolicy {

    private final BookingRules rules;

    public BookingTimePolicy(BookingRules rules) {
        this.rules = Objects.requireNonNull(rules, "BookingRules must not be null");
    }

    public BookingRules rules() {
        return rules;
    }

    /**
     * Валідація форми інтервалу та узгодженості з робочим днем (INV-02...INV-05).
     * Повертає перше виявлене порушення за фіксованим порядком етапу 5.
     */
    public Optional<TimeRule> validateIntervalShape(LocalDateTime startAt, LocalDateTime endAt) {
        // 1. INTERVAL_SHAPE (INV-02): початок має бути раніше кінця в межах однієї дати
        if (startAt == null || endAt == null
                || !startAt.isBefore(endAt)
                || !startAt.toLocalDate().equals(endAt.toLocalDate())) {
            return Optional.of(TimeRule.INTERVAL_SHAPE);
        }

        // 2. OUTSIDE_WORK_HOURS (INV-03): у межах робочого дня
        if (startAt.toLocalTime().isBefore(rules.workDayStart())
                || endAt.toLocalTime().isAfter(rules.workDayEnd())) {
            return Optional.of(TimeRule.OUTSIDE_WORK_HOURS);
        }

        // 3. NOT_ON_GRID (INV-04): початок і кінець кратні slotGrid хвилинам, без секунд і наносекунд
        long gridMinutes = rules.slotGrid().toMinutes();
        if (startAt.getSecond() != 0 || startAt.getNano() != 0
                || endAt.getSecond() != 0 || endAt.getNano() != 0
                || startAt.getMinute() % gridMinutes != 0
                || endAt.getMinute() % gridMinutes != 0) {
            return Optional.of(TimeRule.NOT_ON_GRID);
        }

        // 4. BAD_DURATION (INV-05): тривалість від minDuration до maxDuration
        Duration duration = Duration.between(startAt, endAt);
        if (duration.compareTo(rules.minDuration()) < 0 || duration.compareTo(rules.maxDuration()) > 0) {
            return Optional.of(TimeRule.BAD_DURATION);
        }

        return Optional.empty();
    }

    /**
     * Вхід 1: Валідація інтервалу для пошуку вільних кімнат (A-32, INV-02...INV-05).
     */
    public void validateForSearch(LocalDateTime startAt, LocalDateTime endAt) {
        Optional<TimeRule> rule = validateIntervalShape(startAt, endAt);
        if (rule.isPresent()) {
            throw invalidTime(rule.get());
        }
    }

    private DomainException invalidTime(TimeRule rule) {
        return DomainException.invalidTime(rule, Map.of(
                "workDayStart", rules.workDayStart(), "workDayEnd", rules.workDayEnd(),
                "slotGrid", rules.slotGrid().toMinutes(),
                "minDuration", rules.minDuration().toMinutes(), "maxDuration", rules.maxDuration().toMinutes(),
                "horizonDays", rules.horizonDays()));
    }

    public void validateForSearch(BookingInterval interval) {
        Objects.requireNonNull(interval, "BookingInterval must not be null");
        validateForSearch(interval.startAt(), interval.endAt());
    }

    /**
     * Вхід 2: Валідація інтервалу для створення бронювання (INV-02...INV-05, FR-13, FR-14).
     */
    public void validateForCreation(LocalDateTime startAt, LocalDateTime endAt, OfficeClock.Snapshot snapshot) {
        Objects.requireNonNull(snapshot, "OfficeClock.Snapshot must not be null");

        // 1-4. Форма, робочий день, сітка, тривалість
        Optional<TimeRule> rule = validateIntervalShape(startAt, endAt);
        if (rule.isPresent()) {
            throw invalidTime(rule.get());
        }

        // 5. IN_THE_PAST (FR-13): початок не може бути раніше початку поточного слота
        LocalDateTime currentSlotStart = computeCurrentSlotStart(snapshot.localNow(), rules.slotGrid().toMinutes());
        if (startAt.isBefore(currentSlotStart)) {
            throw invalidTime(TimeRule.IN_THE_PAST);
        }

        // 6. TOO_FAR (FR-14): дата startAt не пізніше сьогодні + horizonDays
        LocalDate maxAllowedDate = snapshot.today().plusDays(rules.horizonDays());
        if (startAt.toLocalDate().isAfter(maxAllowedDate)) {
            throw invalidTime(TimeRule.TOO_FAR);
        }
    }

    public void validateForCreation(BookingInterval interval, OfficeClock.Snapshot snapshot) {
        Objects.requireNonNull(interval, "BookingInterval must not be null");
        validateForCreation(interval.startAt(), interval.endAt(), snapshot);
    }

    /**
     * Початок поточного слота: поточний місцевий час, округлений вниз до кратного slotGrid, секунди й наносекунди обнулені.
     */
    public static LocalDateTime computeCurrentSlotStart(LocalDateTime localNow, long slotGridMinutes) {
        Objects.requireNonNull(localNow, "localNow must not be null");
        int minute = localNow.getMinute();
        int alignedMinute = (int) (minute - (minute % slotGridMinutes));
        return localNow.withMinute(alignedMinute).withSecond(0).withNano(0);
    }
}

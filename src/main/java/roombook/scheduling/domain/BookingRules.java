package roombook.scheduling.domain;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Параметри правил планування без часової зони офісу за етапом 2 (INV-03...INV-05, TR-04, OPS-09).
 * Валідує узгодженість параметрів при створенні та викидає IllegalArgumentException із назвою параметра.
 */
public record BookingRules(
        LocalTime workDayStart,
        LocalTime workDayEnd,
        Duration slotGrid,
        Duration minDuration,
        Duration maxDuration,
        int horizonDays
) {

    public static final LocalTime DEFAULT_WORK_DAY_START = LocalTime.of(8, 0);
    public static final LocalTime DEFAULT_WORK_DAY_END = LocalTime.of(20, 0);
    public static final Duration DEFAULT_SLOT_GRID = Duration.ofMinutes(15);
    public static final Duration DEFAULT_MIN_DURATION = Duration.ofMinutes(15);
    public static final Duration DEFAULT_MAX_DURATION = Duration.ofHours(4);
    public static final int DEFAULT_HORIZON_DAYS = 90;

    public BookingRules {
        Objects.requireNonNull(slotGrid, "Configuration property 'slotGrid' must not be null");
        if (slotGrid.isNegative() || slotGrid.isZero() || slotGrid.toMinutes() <= 0 || slotGrid.toSeconds() % 60 != 0) {
            throw new IllegalArgumentException("Invalid configuration property 'slotGrid': must be positive full minutes");
        }
        long gridMinutes = slotGrid.toMinutes();

        Objects.requireNonNull(workDayStart, "Configuration property 'workDayStart' must not be null");
        Objects.requireNonNull(workDayEnd, "Configuration property 'workDayEnd' must not be null");

        if (!workDayStart.isBefore(workDayEnd)) {
            throw new IllegalArgumentException(
                    "Invalid configuration property 'workDayStart': must be before 'workDayEnd' (" + workDayStart + " >= " + workDayEnd + ")");
        }
        if (workDayStart.getSecond() != 0 || workDayStart.getNano() != 0 || workDayStart.getMinute() % gridMinutes != 0) {
            throw new IllegalArgumentException(
                    "Invalid configuration property 'workDayStart': must be a multiple of 'slotGrid' (" + gridMinutes + "m)");
        }
        if (workDayEnd.getSecond() != 0 || workDayEnd.getNano() != 0 || workDayEnd.getMinute() % gridMinutes != 0) {
            throw new IllegalArgumentException(
                    "Invalid configuration property 'workDayEnd': must be a multiple of 'slotGrid' (" + gridMinutes + "m)");
        }

        Objects.requireNonNull(minDuration, "Configuration property 'minDuration' must not be null");
        Objects.requireNonNull(maxDuration, "Configuration property 'maxDuration' must not be null");

        if (minDuration.isNegative() || minDuration.isZero() || minDuration.toMinutes() <= 0) {
            throw new IllegalArgumentException("Invalid configuration property 'minDuration': must be positive");
        }
        if (minDuration.compareTo(maxDuration) > 0) {
            throw new IllegalArgumentException(
                    "Invalid configuration property 'minDuration': must not exceed 'maxDuration' (" + minDuration + " > " + maxDuration + ")");
        }
        if (minDuration.toMinutes() % gridMinutes != 0) {
            throw new IllegalArgumentException(
                    "Invalid configuration property 'minDuration': must be a multiple of 'slotGrid' (" + gridMinutes + "m)");
        }
        if (maxDuration.toMinutes() % gridMinutes != 0) {
            throw new IllegalArgumentException(
                    "Invalid configuration property 'maxDuration': must be a multiple of 'slotGrid' (" + gridMinutes + "m)");
        }

        if (horizonDays < 0) {
            throw new IllegalArgumentException(
                    "Invalid configuration property 'horizonDays': must be non-negative (" + horizonDays + ")");
        }
    }

    public static BookingRules defaultRules() {
        return new BookingRules(
                DEFAULT_WORK_DAY_START,
                DEFAULT_WORK_DAY_END,
                DEFAULT_SLOT_GRID,
                DEFAULT_MIN_DURATION,
                DEFAULT_MAX_DURATION,
                DEFAULT_HORIZON_DAYS
        );
    }
}

package roombook.kernel;

/**
 * Підкоди помилки INVALID_TIME (етап 5: підкоди INVALID_TIME, A-33).
 */
public enum TimeRule {
    INTERVAL_SHAPE,
    OUTSIDE_WORK_HOURS,
    NOT_ON_GRID,
    BAD_DURATION,
    IN_THE_PAST,
    TOO_FAR
}

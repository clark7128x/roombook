package roombook.kernel;

/**
 * Правила помилок валідації окремих полів (A-56, етап 5, T-04).
 */
public enum FieldRule {
    REQUIRED,
    LENGTH,
    RANGE,
    FORMAT,
    ILLEGAL_CHARACTERS,
    ILLEGAL_STATE
}

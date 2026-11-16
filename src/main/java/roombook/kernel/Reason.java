package roombook.kernel;

/**
 * Причини відмови бізнес-операцій ядра (етап 5: таблиця кодів, A-30, A-33).
 * Чотири транспортні коди (UNAUTHENTICATED, CSRF_INVALID, UNSUPPORTED_REQUEST, INTERNAL_ERROR)
 * знаходяться в platform.
 */
public enum Reason {
    INVALID_INPUT,
    INVALID_TIME,
    ROOM_NOT_FOUND,
    ROOM_INACTIVE,
    SLOT_TAKEN,
    BOOKING_NOT_FOUND,
    NOT_PERMITTED,
    BOOKING_FINISHED,
    ROOM_NAME_TAKEN,
    ROOM_HAS_UNFINISHED_BOOKINGS,
    USERNAME_TAKEN,
    USER_NOT_FOUND,
    LAST_ADMIN,
    INVITATION_INVALID
}

package roombook.scheduling.domain;

/**
 * Фаза ACTIVE-бронювання за специфікацією етапу 2 (TR-05).
 * Обчислюється динамічно з інтервалу та місцевого часу офісу, не зберігається в БД.
 */
public enum BookingPhase {
    UPCOMING,
    IN_PROGRESS,
    FINISHED
}

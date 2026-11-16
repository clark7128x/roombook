package roombook.scheduling.application;

/**
 * Команда оновлення атрибутів кімнати (FR-20, EP-04).
 * active цим методом не змінюється.
 */
public record UpdateRoomCommand(
        String name,
        Integer floor,
        Integer capacity,
        String equipment
) {
}

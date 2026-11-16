package roombook.scheduling.application;

/**
 * Команда створення нової кімнати (FR-19, EP-03).
 */
public record CreateRoomCommand(
        String name,
        Integer floor,
        Integer capacity,
        String equipment
) {
}

package roombook.scheduling.application;

/**
 * Відображення кімнати для відповідей застосунку (FR-03, FR-19...FR-22, EP-01...EP-05).
 */
public record RoomView(
        long id,
        String name,
        int floor,
        int capacity,
        String equipment,
        boolean active
) {
}

package roombook.scheduling.application;

import java.time.LocalDateTime;

/**
 * Команда створення нового бронювання (FR-09...FR-16, EP-08).
 * Автор береться з Actor, а не з цієї команди (A-08).
 */
public record CreateBookingCommand(
        Long roomId,
        String title,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
}

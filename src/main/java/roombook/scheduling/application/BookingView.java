package roombook.scheduling.application;

import roombook.scheduling.domain.BookingPhase;
import roombook.scheduling.domain.BookingStatus;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Відображення бронювання для відповідей застосунку (FR-07...FR-10, EP-08...EP-12, A-57).
 */
public record BookingView(
        long id,
        RoomRef room,
        UserRef author,
        String title,
        LocalDateTime startAt,
        LocalDateTime endAt,
        BookingStatus status,
        BookingPhase phase,
        Instant createdAt,
        Instant cancelledAt,
        UserRef cancelledBy,
        boolean cancellable
) {
}

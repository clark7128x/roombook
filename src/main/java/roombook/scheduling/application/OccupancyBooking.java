package roombook.scheduling.application;

import java.time.LocalDateTime;

public record OccupancyBooking(long id, LocalDateTime startAt, LocalDateTime endAt, String title, UserRef author) {
}

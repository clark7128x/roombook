package roombook.scheduling.application;

import java.time.LocalDate;
import roombook.scheduling.domain.BookingStatus;

public record BookingFilterQuery(LocalDate dateFrom, LocalDate dateTo, BookingStatus status, Long roomId, Long authorId, String order, Integer page, Integer size) {
}

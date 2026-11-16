package roombook.scheduling.persistence;

import roombook.scheduling.domain.BookingStatus;
import java.time.LocalDateTime;
import java.util.List;

public interface BookingReadOperations {
    List<BookingRow> findPage(LocalDateTime from, LocalDateTime toExclusive,
            BookingStatus status, Long roomId, Long authorId, boolean ascending, long offset, int limit);
}

package roombook.scheduling.persistence;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import roombook.scheduling.domain.Booking;
import roombook.scheduling.domain.BookingStatus;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BookingReadOperationsImpl implements BookingReadOperations {
    private final EntityManager entityManager;

    public BookingReadOperationsImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<BookingRow> findPage(LocalDateTime from, LocalDateTime toExclusive,
            BookingStatus status, Long roomId, Long authorId, boolean ascending, long offset, int limit) {
        // Native OFFSET supports the full long product of the public integer page and size.
        // Hibernate's HQL/JPA pagination narrows offsets to Integer.
        StringBuilder sql = new StringBuilder("select {b.*}, r.name as room_name "
                + "from booking b join room r on r.id = b.room_id where 1=1");
        Map<String, Object> parameters = new HashMap<>();
        add(sql, parameters, "from", from, "b.start_at >= :from");
        add(sql, parameters, "to", toExclusive, "b.start_at < :to");
        add(sql, parameters, "status", status == null ? null : status.name(), "b.status = :status");
        add(sql, parameters, "roomId", roomId, "b.room_id = :roomId");
        add(sql, parameters, "authorId", authorId, "b.author_id = :authorId");
        String direction = ascending ? " asc" : " desc";
        sql.append(" order by b.start_at").append(direction).append(", b.id").append(direction)
                .append(" offset :offset rows fetch first :limit rows only");
        var query = entityManager.unwrap(Session.class).createNativeQuery(sql.toString(), Object[].class)
                .addEntity("b", Booking.class).addScalar("room_name", String.class);
        parameters.forEach(query::setParameter);
        return query.setParameter("offset", offset).setParameter("limit", limit).getResultList().stream()
                .map(row -> new BookingRow((Booking) row[0], (String) row[1])).toList();
    }

    private static void add(StringBuilder sql, Map<String, Object> parameters,
            String name, Object value, String predicate) {
        if (value != null) {
            sql.append(" and ").append(predicate);
            parameters.put(name, value);
        }
    }
}

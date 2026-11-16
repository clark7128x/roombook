package roombook.scheduling.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import roombook.scheduling.domain.Booking;
import roombook.scheduling.domain.BookingInterval;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторій агрегату Booking для операцій запису (D-04, D-09, INV-02, INV-08, INV-09, AR-04, AR-12).
 * Розширює базовий Repository без методів delete*.
 */
public interface BookingRepository extends Repository<Booking, Long>, BookingReadOperations {

    @Query("""
        SELECT b FROM Booking b JOIN Room r ON r.id = b.roomId
        WHERE r.active = true AND b.status = roombook.scheduling.domain.BookingStatus.ACTIVE
          AND b.startAt >= :start AND b.startAt < :end
          AND (:floor IS NULL OR r.floor = :floor)
          AND (:capacity IS NULL OR r.capacity >= :capacity)
        ORDER BY b.startAt, b.id
    """)
    List<Booking> findOccupancy(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end,
            @Param("floor") Integer floor, @Param("capacity") Integer capacity);

    Optional<Booking> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> lockById(@Param("id") Long id);

    Booking save(Booking booking);

    Booking saveAndFlush(Booking booking);

    void flush();

    /**
     * Пошук ACTIVE-бронювань кімнати, що перетинаються з інтервалом [startAt, endAt),
     * обмежений добою початку бронювання (INV-02, INV-08, D-04).
     */
    @Query("""
        SELECT b FROM Booking b
        WHERE b.roomId = :roomId
          AND b.status = roombook.scheduling.domain.BookingStatus.ACTIVE
          AND b.startAt >= :dayStart
          AND b.startAt < :dayEnd
          AND b.startAt < :endAt
          AND b.endAt > :startAt
        ORDER BY b.startAt ASC, b.id ASC
    """)
    List<Booking> findActiveOverlapping(
            @Param("roomId") Long roomId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd
    );

    default List<Booking> findActiveOverlapping(Long roomId, LocalDateTime startAt, LocalDateTime endAt) {
        LocalDateTime dayStart = startAt.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        return findActiveOverlapping(roomId, startAt, endAt, dayStart, dayEnd);
    }

    default List<Booking> findActiveOverlapping(Long roomId, BookingInterval interval) {
        return findActiveOverlapping(roomId, interval.startAt(), interval.endAt());
    }

    /**
     * Перевірка наявності незавершених ACTIVE-бронювань кімнати (INV-09, D-04).
     * Бронювання вважається незавершеним, якщо його кінець строго пізніше заданого моменту now,
     * а початок не раніше початку поточної доби dayStart (завдяки INV-02).
     */
    @Query("""
        SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM Booking b
        WHERE b.roomId = :roomId
          AND b.status = roombook.scheduling.domain.BookingStatus.ACTIVE
          AND b.startAt >= :dayStart
          AND b.endAt > :now
    """)
    boolean existsUnfinished(
            @Param("roomId") Long roomId,
            @Param("now") LocalDateTime now,
            @Param("dayStart") LocalDateTime dayStart
    );

    default boolean existsUnfinished(Long roomId, LocalDateTime now) {
        return existsUnfinished(roomId, now, now.toLocalDate().atStartOfDay());
    }
}

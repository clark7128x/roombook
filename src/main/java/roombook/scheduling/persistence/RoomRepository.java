package roombook.scheduling.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import roombook.scheduling.domain.Room;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторій агрегату Room для операцій запису (D-04, D-09, AR-04, AR-12).
 * Розширює базовий Repository без методів delete*.
 */
public interface RoomRepository extends Repository<Room, Long> {

    List<Room> findAll();

    @Query("""
        SELECT r FROM Room r
        WHERE (:includeInactive = true OR r.active = true)
          AND (:floor IS NULL OR r.floor = :floor)
          AND (:capacity IS NULL OR r.capacity >= :capacity)
          AND (:free = false OR NOT EXISTS (
              SELECT b.id FROM Booking b WHERE b.roomId = r.id
                AND b.status = roombook.scheduling.domain.BookingStatus.ACTIVE
                AND b.startAt >= cast(:freeFrom as date)
                AND b.startAt < :freeTo AND b.endAt > :freeFrom))
    """)
    List<Room> findMatching(@Param("floor") Integer floor, @Param("capacity") Integer capacity,
            @Param("includeInactive") boolean includeInactive, @Param("free") boolean free,
            @Param("freeFrom") LocalDateTime freeFrom,
            @Param("freeTo") LocalDateTime freeTo);

    Optional<Room> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.id = :id")
    Optional<Room> lockById(@Param("id") Long id);

    Room save(Room room);

    Room saveAndFlush(Room room);

    void flush();
}

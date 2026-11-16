package roombook.scheduling.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roombook.kernel.Actor;
import roombook.kernel.DomainException;
import roombook.kernel.OfficeClock;
import roombook.kernel.Reason;
import roombook.scheduling.domain.Room;
import roombook.scheduling.persistence.BookingRepository;
import roombook.scheduling.persistence.RoomRepository;
import roombook.scheduling.persistence.SchedulingConstraints;

import java.util.Objects;

/**
 * Сервіс операцій керування кімнатами (FR-19...FR-22, D-04, INV-09, INV-11, INV-12, A-17, OPS-05).
 * Усі методи вимагають ролі ADMIN та перевіряють її до звернення до БД (TS-06).
 */
@Service
@Transactional
public class RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomService.class);

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final OfficeClock officeClock;

    public RoomService(
            RoomRepository roomRepository,
            BookingRepository bookingRepository,
            OfficeClock officeClock
    ) {
        this.roomRepository = Objects.requireNonNull(roomRepository, "roomRepository must not be null");
        this.bookingRepository = Objects.requireNonNull(bookingRepository, "bookingRepository must not be null");
        this.officeClock = Objects.requireNonNull(officeClock, "officeClock must not be null");
    }

    /**
     * Створення нової кімнати (FR-19, EP-03).
     * Доступ лише для ADMIN. Без блокування рядків (етап 3).
     */
    public RoomView createRoom(Actor actor, CreateRoomCommand command) {
        Objects.requireNonNull(actor, "actor must not be null");
        actor.requireAdmin();

        if (command == null) {
            throw new DomainException(Reason.INVALID_INPUT);
        }

        Room room = new Room(command.name(), command.floor(), command.capacity(), command.equipment());

        try {
            Room saved = roomRepository.saveAndFlush(room);
            log.atInfo().addKeyValue("event", "ROOM_CREATED").addKeyValue("roomId", saved.getId()).addKeyValue("actorId", actor.userId()).addKeyValue("action", "CREATED").log("Room created");
            return toView(saved);
        } catch (Throwable t) {
            throw SchedulingConstraints.translate(t);
        }
    }

    /**
     * Оновлення атрибутів кімнати (FR-20, EP-04).
     * Доступ лише для ADMIN. Блокує рядок кімнати через lockById (D-04).
     */
    public RoomView updateRoom(Actor actor, long roomId, UpdateRoomCommand command) {
        Objects.requireNonNull(actor, "actor must not be null");
        actor.requireAdmin();

        if (command == null) {
            throw new DomainException(Reason.INVALID_INPUT);
        }

        // 1. Перше звернення до БД - блокуюче читання рядка кімнати (D-04)
        Room room = roomRepository.lockById(roomId)
                .orElseThrow(() -> new DomainException(Reason.ROOM_NOT_FOUND));

        // 2. Валідація та оновлення полів
        room.update(command.name(), command.floor(), command.capacity(), command.equipment());

        try {
            Room saved = roomRepository.saveAndFlush(room);
            log.atInfo().addKeyValue("event", "ROOM_UPDATED").addKeyValue("roomId", saved.getId()).addKeyValue("actorId", actor.userId()).addKeyValue("action", "UPDATED").log("Room updated");
            return toView(saved);
        } catch (Throwable t) {
            throw SchedulingConstraints.translate(t);
        }
    }

    /**
     * Активація кімнати (FR-22, EP-06).
     * Доступ лише для ADMIN. Блокує рядок кімнати через lockById (D-04).
     */
    public RoomView activateRoom(Actor actor, long roomId) {
        Objects.requireNonNull(actor, "actor must not be null");
        actor.requireAdmin();

        // 1. Перше звернення до БД - блокуюче читання рядка кімнати (D-04)
        Room room = roomRepository.lockById(roomId)
                .orElseThrow(() -> new DomainException(Reason.ROOM_NOT_FOUND));

        // 2. Безпечний повтор для вже активної кімнати (A-17)
        if (room.isActive()) {
            return toView(room);
        }

        // 3. Активація та збереження
        room.activate();
        Room saved = roomRepository.saveAndFlush(room);
        log.atInfo().addKeyValue("event", "ROOM_ACTIVATED").addKeyValue("roomId", saved.getId()).addKeyValue("actorId", actor.userId()).addKeyValue("action", "ACTIVATED").log("Room activated");
        return toView(saved);
    }

    /**
     * Деактивація кімнати (FR-21, EP-05).
     * Доступ лише для ADMIN. Блокує рядок кімнати через lockById (D-04).
     * Перевіряє відсутність незавершених ACTIVE-бронювань (INV-09).
     */
    public RoomView deactivateRoom(Actor actor, long roomId) {
        Objects.requireNonNull(actor, "actor must not be null");
        actor.requireAdmin();

        // 1. Перше звернення до БД - блокуюче читання рядка кімнати (D-04)
        Room room = roomRepository.lockById(roomId)
                .orElseThrow(() -> new DomainException(Reason.ROOM_NOT_FOUND));

        // 2. Безпечний повтор для вже неактивної кімнати (A-17) - до перевірки бронювань
        if (!room.isActive()) {
            return toView(room);
        }

        // 3. Перевірка незавершених ACTIVE-бронювань (INV-09)
        OfficeClock.Snapshot snapshot = officeClock.snapshot();
        boolean hasUnfinished = bookingRepository.existsUnfinished(roomId, snapshot.localNow());
        if (hasUnfinished) {
            throw new DomainException(Reason.ROOM_HAS_UNFINISHED_BOOKINGS);
        }

        // 4. Деактивація та збереження
        room.deactivate();
        Room saved = roomRepository.saveAndFlush(room);
        log.atInfo().addKeyValue("event", "ROOM_DEACTIVATED").addKeyValue("roomId", saved.getId()).addKeyValue("actorId", actor.userId()).addKeyValue("action", "DEACTIVATED").log("Room deactivated");
        return toView(saved);
    }

    private RoomView toView(Room room) {
        return new RoomView(
                room.getId(),
                room.getName(),
                room.getFloor(),
                room.getCapacity(),
                room.getEquipment(),
                room.isActive()
        );
    }
}

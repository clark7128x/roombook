package roombook.scheduling.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roombook.kernel.Actor;
import roombook.kernel.DomainException;
import roombook.kernel.FieldError;
import roombook.kernel.FieldRule;
import roombook.kernel.OfficeClock;
import roombook.kernel.Reason;
import roombook.kernel.TextRules;
import roombook.scheduling.domain.Booking;
import roombook.scheduling.domain.BookingInterval;
import roombook.scheduling.domain.BookingStatus;
import roombook.scheduling.domain.BookingTimePolicy;
import roombook.scheduling.domain.Room;
import roombook.scheduling.persistence.BookingRepository;
import roombook.scheduling.persistence.RoomRepository;
import roombook.scheduling.persistence.SchedulingConstraints;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Сервіс створення та скасування бронювань (FR-09...FR-18, D-02, D-03, D-04, D-10, INV-08, A-17, A-57, OPS-05).
 * Суворо дотримується порядку перевірок етапу 2 (TS-06).
 */
@Service
@Transactional
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final BookingTimePolicy bookingTimePolicy;
    private final OfficeClock officeClock;
    private final BookingQueries bookingQueries;

    public BookingService(
            RoomRepository roomRepository,
            BookingRepository bookingRepository,
            BookingTimePolicy bookingTimePolicy,
            OfficeClock officeClock,
            BookingQueries bookingQueries
    ) {
        this.roomRepository = Objects.requireNonNull(roomRepository, "roomRepository must not be null");
        this.bookingRepository = Objects.requireNonNull(bookingRepository, "bookingRepository must not be null");
        this.bookingTimePolicy = Objects.requireNonNull(bookingTimePolicy, "bookingTimePolicy must not be null");
        this.officeClock = Objects.requireNonNull(officeClock, "officeClock must not be null");
        this.bookingQueries = Objects.requireNonNull(bookingQueries, "bookingQueries must not be null");
    }

    /**
     * Створення нового бронювання (FR-09...FR-16, EP-08).
     * Порядок перевірок за специфікацією етапу 2 (TS-06):
     * 1. Автентифікація й роль (Actor не null).
     * 2. Обов'язкові поля й назва (INV-01): INVALID_INPUT.
     * 3. Правила часу: INV-02...INV-05, FR-13, FR-14: INVALID_TIME.
     * 4. Кімната існує й активна (D-04 lockById): ROOM_NOT_FOUND, ROOM_INACTIVE.
     * 5. Перетину немає: SLOT_TAKEN.
     * 6. Вставка й flush із перехопленням ex_booking_no_overlap -> SLOT_TAKEN.
     */
    public BookingView createBooking(Actor actor, CreateBookingCommand command) {
        // 1. Автентифікація й роль
        Objects.requireNonNull(actor, "actor must not be null");

        if (command == null) {
            throw new DomainException(Reason.INVALID_INPUT);
        }

        // 2. Обов'язкові поля й назва зустрічі (INV-01)
        List<FieldError> errors = new ArrayList<>();
        if (command.roomId() == null || command.roomId() <= 0) {
            errors.add(new FieldError("roomId", FieldRule.REQUIRED));
        }

        String strippedTitle = null;
        if (command.title() == null) {
            errors.add(new FieldError("title", FieldRule.REQUIRED));
        } else {
            strippedTitle = TextRules.strip(command.title());
            if (strippedTitle.isEmpty()) {
                errors.add(new FieldError("title", FieldRule.REQUIRED));
            } else if (TextRules.codePointLength(strippedTitle) > 200) {
                errors.add(new FieldError("title", FieldRule.LENGTH));
            } else if (TextRules.hasControlCharacters(strippedTitle)) {
                errors.add(new FieldError("title", FieldRule.ILLEGAL_CHARACTERS));
            }
        }

        if (command.startAt() == null) {
            errors.add(new FieldError("startAt", FieldRule.REQUIRED));
        }
        if (command.endAt() == null) {
            errors.add(new FieldError("endAt", FieldRule.REQUIRED));
        }

        if (!errors.isEmpty()) {
            throw DomainException.invalidInput(errors);
        }

        // 3. Правила часу (INV-02...INV-05, FR-13, FR-14)
        OfficeClock.Snapshot snapshot = officeClock.snapshot();
        BookingInterval interval = new BookingInterval(command.startAt(), command.endAt());
        bookingTimePolicy.validateForCreation(interval, snapshot);

        // 4. Перше звернення до БД: блокуюче читання рядка кімнати (D-04)
        Room room = roomRepository.lockById(command.roomId())
                .orElseThrow(() -> new DomainException(Reason.ROOM_NOT_FOUND));

        if (!room.isActive()) {
            throw new DomainException(Reason.ROOM_INACTIVE);
        }

        // 5. Пошук ACTIVE-бронювань кімнати, що перетинаються
        List<Booking> overlapping = bookingRepository.findActiveOverlapping(
                command.roomId(),
                interval.startAt(),
                interval.endAt()
        );
        if (!overlapping.isEmpty()) {
            throw new DomainException(Reason.SLOT_TAKEN);
        }

        // 6. Створення та збереження сутності Booking з негайним flush
        Booking booking = new Booking(
                command.roomId(),
                actor.userId(),
                strippedTitle,
                interval,
                snapshot.instant()
        );

        Booking saved;
        try {
            saved = bookingRepository.saveAndFlush(booking);
        } catch (Throwable t) {
            throw SchedulingConstraints.translate(t);
        }

        // 7. Подія операційного журналу (OPS-05)
        log.atInfo().addKeyValue("event", "BOOKING_CREATED").addKeyValue("bookingId", saved.getId()).addKeyValue("roomId", saved.getRoomId()).addKeyValue("actorId", actor.userId()).log("Booking created");

        // 8. Побудова view-record через BookingQueries у тій самій транзакції (A-57)
        return bookingQueries.get(actor, saved.getId());
    }

    /**
     * Скасування бронювання (FR-17, EP-10).
     * Порядок перевірок за специфікацією (TS-06):
     * 1. Перше звернення до БД: блокуюче читання рядка Booking (D-04) -> BOOKING_NOT_FOUND.
     * 2. Ініціатор є автором або ADMIN -> NOT_PERMITTED.
     * 3. Безпечний повтор для вже скасованого (A-17).
     * 4. Незавершене бронювання (TR-05) -> BOOKING_FINISHED.
     */
    public BookingView cancelBooking(Actor actor, long bookingId) {
        Objects.requireNonNull(actor, "actor must not be null");

        OfficeClock.Snapshot snapshot = officeClock.snapshot();

        // 1. Перше звернення до БД - блокуюче читання рядка бронювання (D-04)
        Booking booking = bookingRepository.lockById(bookingId)
                .orElseThrow(() -> new DomainException(Reason.BOOKING_NOT_FOUND));

        // 2-4. Логіка скасування в домені з перевіркою прав, безпечним повтором та завершенням
        boolean wasCancelled = booking.getStatus() == BookingStatus.CANCELLED;
        booking.cancel(actor, snapshot);

        if (!wasCancelled) {
            Booking saved = bookingRepository.saveAndFlush(booking);
            log.atInfo().addKeyValue("event", "BOOKING_CANCELLED").addKeyValue("bookingId", bookingId).addKeyValue("actorId", actor.userId()).addKeyValue("authorId", saved.getAuthorId()).log("Booking cancelled");
        }

        return bookingQueries.get(actor, bookingId);
    }
}

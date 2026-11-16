package roombook.scheduling.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roombook.accounts.api.UserDirectory;
import roombook.kernel.Actor;
import roombook.kernel.DomainException;
import roombook.kernel.FieldRule;
import roombook.kernel.OfficeClock;
import roombook.kernel.Reason;
import roombook.scheduling.domain.Booking;
import roombook.scheduling.domain.BookingPhase;
import roombook.scheduling.domain.BookingStatus;
import roombook.scheduling.domain.Room;
import roombook.scheduling.persistence.BookingRepository;
import roombook.scheduling.persistence.BookingRow;
import roombook.scheduling.persistence.RoomRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Читаючі запити бронювань (FR-07...FR-10, EP-09, A-57, AR-03, AR-07, AR-09).
 */
@Service
@Transactional(readOnly = true)
public class BookingQueries {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final UserDirectory userDirectory;
    private final OfficeClock officeClock;

    public BookingQueries(
            BookingRepository bookingRepository,
            RoomRepository roomRepository,
            UserDirectory userDirectory,
            OfficeClock officeClock
    ) {
        this.bookingRepository = Objects.requireNonNull(bookingRepository, "bookingRepository must not be null");
        this.roomRepository = Objects.requireNonNull(roomRepository, "roomRepository must not be null");
        this.userDirectory = Objects.requireNonNull(userDirectory, "userDirectory must not be null");
        this.officeClock = Objects.requireNonNull(officeClock, "officeClock must not be null");
    }

    /**
     * Отримання бронювання за id (EP-09, A-57).
     * Перший параметр Actor (AR-07).
     */
    public BookingView get(Actor actor, long bookingId) {
        Objects.requireNonNull(actor, "actor must not be null");

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new DomainException(Reason.BOOKING_NOT_FOUND));

        return toView(actor, booking, officeClock.snapshot());
    }

    /**
     * Побудова BookingView для сутності Booking.
     */
    public BookingView toView(Actor actor, Booking booking, OfficeClock.Snapshot snapshot) {
        Objects.requireNonNull(actor, "actor must not be null");
        Objects.requireNonNull(booking, "booking must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");

        // 1. Отримуємо назву кімнати
        String roomName = roomRepository.findById(booking.getRoomId())
                .map(Room::getName)
                .orElse("Room #" + booking.getRoomId());
        RoomRef roomRef = new RoomRef(booking.getRoomId(), roomName);

        // 2. Отримуємо імена автора та скасувальника через UserDirectory
        Set<Long> userIds = new HashSet<>();
        userIds.add(booking.getAuthorId());
        if (booking.getCancelledById() != null) {
            userIds.add(booking.getCancelledById());
        }
        Map<Long, String> names = userDirectory.displayNames(userIds);

        return toView(actor, booking, snapshot, roomRef, names);
    }

    private BookingView toView(Actor actor, Booking booking, OfficeClock.Snapshot snapshot,
            RoomRef roomRef, Map<Long, String> names) {
        String authorName = names.getOrDefault(booking.getAuthorId(), "User #" + booking.getAuthorId());
        UserRef authorRef = new UserRef(booking.getAuthorId(), authorName);

        UserRef cancelledByRef = null;
        if (booking.getCancelledById() != null) {
            String cancellerName = names.getOrDefault(booking.getCancelledById(), "User #" + booking.getCancelledById());
            cancelledByRef = new UserRef(booking.getCancelledById(), cancellerName);
        }

        // 3. Розрахунок phase та cancellable (TR-05)
        BookingPhase phase = null;
        boolean cancellable = false;

        if (booking.getStatus() == BookingStatus.ACTIVE) {
            phase = booking.phase(snapshot.localNow());
            boolean isAuthorOrAdmin = actor.isAdmin() || booking.getAuthorId().equals(actor.userId());
            cancellable = isAuthorOrAdmin && phase != BookingPhase.FINISHED;
        }

        return new BookingView(
                booking.getId(),
                roomRef,
                authorRef,
                booking.getTitle(),
                booking.getStartAt(),
                booking.getEndAt(),
                booking.getStatus(),
                phase,
                booking.getCreatedAt(),
                booking.getCancelledAt(),
                cancelledByRef,
                cancellable
        );
    }

    public Page<BookingView> myBookings(Actor actor, BookingFilterQuery query) {
        Objects.requireNonNull(actor);
        return list(actor, query, actor.userId());
    }

    public Page<BookingView> allBookings(Actor actor, BookingFilterQuery query) {
        Objects.requireNonNull(actor).requireAdmin();
        if (query == null) throw new DomainException(Reason.INVALID_INPUT);
        return list(actor, query, query.authorId());
    }

    public BookingFilterOptions filterOptions(Actor actor) {
        Objects.requireNonNull(actor).requireAdmin();
        return new BookingFilterOptions(QuerySupport.rooms(roomRepository.findAll()), userDirectory.selectableUsers());
    }

    private Page<BookingView> list(Actor actor, BookingFilterQuery query, Long authorId) {
        if (query == null) throw new DomainException(Reason.INVALID_INPUT);
        int page = query.page() == null ? 0 : query.page();
        int size = query.size() == null ? 20 : query.size();
        if (page < 0) throw DomainException.invalidInput("page", FieldRule.RANGE);
        if (size < 1 || size > 100) throw DomainException.invalidInput("size", FieldRule.RANGE);
        QuerySupport.date(query.dateFrom(), "dateFrom");
        QuerySupport.date(query.dateTo(), "dateTo");
        if (query.dateFrom() != null && query.dateTo() != null && query.dateFrom().isAfter(query.dateTo())) {
            throw DomainException.invalidInput("dateFrom", FieldRule.RANGE);
        }
        String order = query.order() == null ? "desc" : query.order();
        if (!order.equalsIgnoreCase("asc") && !order.equalsIgnoreCase("desc")) {
            throw DomainException.invalidInput("order", FieldRule.FORMAT);
        }
        OfficeClock.Snapshot snapshot = officeClock.snapshot();
        List<BookingRow> rows = bookingRepository.findPage(
                query.dateFrom() == null ? null : query.dateFrom().atStartOfDay(),
                query.dateTo() == null ? null : query.dateTo().plusDays(1).atStartOfDay(),
                query.status(), query.roomId(), authorId, order.equalsIgnoreCase("asc"), (long) page * size, size + 1);
        boolean hasNext = rows.size() > size;
        List<BookingRow> visible = rows.subList(0, Math.min(rows.size(), size));
        Set<Long> userIds = new HashSet<>();
        for (BookingRow row : visible) {
            userIds.add(row.booking().getAuthorId());
            if (row.booking().getCancelledById() != null) userIds.add(row.booking().getCancelledById());
        }
        Map<Long, String> names = userDirectory.displayNames(userIds);
        return new Page<>(visible.stream().map(row -> toView(actor, row.booking(), snapshot,
                new RoomRef(row.booking().getRoomId(), row.roomName()), names)).toList(), page, size, hasNext);
    }
}

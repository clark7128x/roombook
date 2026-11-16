package roombook.scheduling.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import roombook.accounts.api.UserDirectory;
import roombook.kernel.Actor;
import roombook.kernel.DomainException;
import roombook.kernel.OfficeClock;
import roombook.kernel.Reason;
import roombook.kernel.Role;
import roombook.scheduling.domain.Booking;
import roombook.scheduling.domain.BookingPhase;
import roombook.scheduling.domain.BookingStatus;
import roombook.scheduling.domain.Room;
import roombook.scheduling.persistence.BookingRepository;
import roombook.scheduling.persistence.RoomRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Тести BookingQueries (T-14, TS-06, FR-09, TR-05).
 */
@Tag("TS-06")
@Tag("FR-09")
@Tag("TR-05")
class BookingQueriesTest {

    private BookingRepository bookingRepository;
    private RoomRepository roomRepository;
    private UserDirectory userDirectory;
    private OfficeClock officeClock;
    private BookingQueries bookingQueries;

    private final ZoneId zone = ZoneId.of("Europe/Kyiv");
    private final Actor authorActor = new Actor(10L, Role.EMPLOYEE);
    private final Actor otherActor = new Actor(20L, Role.EMPLOYEE);
    private final Actor adminActor = new Actor(99L, Role.ADMIN);

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        roomRepository = mock(RoomRepository.class);
        userDirectory = mock(UserDirectory.class);

        // Фіксований час: 2026-11-02 10:15
        LocalDateTime fixedNow = LocalDateTime.of(2026, 11, 2, 10, 15);
        Instant instant = fixedNow.atZone(zone).toInstant();
        officeClock = new OfficeClock(Clock.fixed(instant, zone), zone);

        bookingQueries = new BookingQueries(bookingRepository, roomRepository, userDirectory, officeClock);
    }

    private Booking createBooking(long id, long roomId, long authorId, LocalDateTime start, LocalDateTime end, BookingStatus status) {
        Booking b = new Booking(roomId, authorId, "Test Meeting", start, end, Instant.now());
        ReflectionTestUtils.setField(b, "id", id);
        if (status == BookingStatus.CANCELLED) {
            b.cancel(new Actor(authorId, Role.EMPLOYEE), officeClock.snapshot());
        }
        return b;
    }

    @Test
    @DisplayName("get: повертає повний BookingView з RoomRef, UserRef, phase та cancellable")
    void getReturnsCompleteBookingView() {
        long bookingId = 1L;
        long roomId = 5L;
        long authorId = 10L;

        // Бронювання 10:00-11:00, поточний час 10:15 -> IN_PROGRESS
        Booking b = createBooking(bookingId, roomId, authorId,
                LocalDateTime.of(2026, 11, 2, 10, 0),
                LocalDateTime.of(2026, 11, 2, 11, 0),
                BookingStatus.ACTIVE);

        Room room = new Room("Green Room", 2, 10, "Projector");
        ReflectionTestUtils.setField(room, "id", roomId);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(b));
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(userDirectory.displayNames(Set.of(authorId))).thenReturn(Map.of(authorId, "Taras Shevchenko"));

        BookingView view = bookingQueries.get(authorActor, bookingId);

        assertEquals(bookingId, view.id());
        assertEquals("Green Room", view.room().name());
        assertEquals(roomId, view.room().id());
        assertEquals("Taras Shevchenko", view.author().displayName());
        assertEquals(authorId, view.author().id());
        assertEquals(BookingPhase.IN_PROGRESS, view.phase());
        assertTrue(view.cancellable(), "Автор може скасувати поточне бронювання");
    }

    @Test
    @DisplayName("get: для іншого EMPLOYEE поле cancellable є false, для ADMIN - true")
    void getCancellableDependsOnActor() {
        long bookingId = 1L;
        Booking b = createBooking(bookingId, 5L, 10L,
                LocalDateTime.of(2026, 11, 2, 10, 0),
                LocalDateTime.of(2026, 11, 2, 11, 0),
                BookingStatus.ACTIVE);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(b));
        when(roomRepository.findById(5L)).thenReturn(Optional.empty());
        when(userDirectory.displayNames(any())).thenReturn(Map.of());

        BookingView otherView = bookingQueries.get(otherActor, bookingId);
        assertFalse(otherView.cancellable(), "Чужий EMPLOYEE не може скасувати бронювання");

        BookingView adminView = bookingQueries.get(adminActor, bookingId);
        assertTrue(adminView.cancellable(), "ADMIN може скасувати чуже незавершене бронювання");
    }

    @Test
    @DisplayName("get: для завершеного або скасованого бронювання cancellable є false")
    void getFinishedOrCancelledNotCancellable() {
        long bookingId = 1L;
        // Завершене: 08:00-09:00 при поточному часі 10:15
        Booking finished = createBooking(bookingId, 5L, 10L,
                LocalDateTime.of(2026, 11, 2, 8, 0),
                LocalDateTime.of(2026, 11, 2, 9, 0),
                BookingStatus.ACTIVE);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(finished));
        when(roomRepository.findById(5L)).thenReturn(Optional.empty());
        when(userDirectory.displayNames(any())).thenReturn(Map.of());

        BookingView finishedView = bookingQueries.get(authorActor, bookingId);
        assertEquals(BookingPhase.FINISHED, finishedView.phase());
        assertFalse(finishedView.cancellable(), "Завершене бронювання не можна скасувати");

        // Скасоване
        Booking cancelled = createBooking(2L, 5L, 10L,
                LocalDateTime.of(2026, 11, 2, 12, 0),
                LocalDateTime.of(2026, 11, 2, 13, 0),
                BookingStatus.CANCELLED);
        when(bookingRepository.findById(2L)).thenReturn(Optional.of(cancelled));

        BookingView cancelledView = bookingQueries.get(authorActor, 2L);
        assertNull(cancelledView.phase(), "Для скасованого бронювання фаза null");
        assertFalse(cancelledView.cancellable(), "Скасоване бронювання не можна скасувати повторно");
    }

    @Test
    @DisplayName("get: неіснуюче бронювання кидає BOOKING_NOT_FOUND")
    void getNotFoundThrows() {
        when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class, () -> bookingQueries.get(authorActor, 999L));
        assertEquals(Reason.BOOKING_NOT_FOUND, ex.getReason());
    }

    @Test
    @Tag("TS-06")
    void listRejectsInvalidParametersBeforeReading() {
        var date = java.time.LocalDate.of(2026, 11, 2);
        var invalid = java.util.List.of(
                new BookingFilterQuery(null, null, null, null, null, null, -1, 20),
                new BookingFilterQuery(null, null, null, null, null, null, 0, 0),
                new BookingFilterQuery(null, null, null, null, null, null, 0, 101),
                new BookingFilterQuery(date, date.minusDays(1), null, null, null, null, 0, 20),
                new BookingFilterQuery(date.withYear(1999), null, null, null, null, null, 0, 20),
                new BookingFilterQuery(null, date.withYear(2101), null, null, null, null, 0, 20),
                new BookingFilterQuery(null, null, null, null, null, "wrong", 0, 20));
        var fields = java.util.List.of("page", "size", "size", "dateFrom", "dateFrom", "dateTo", "order");
        for (int i = 0; i < invalid.size(); i++) {
            var query = invalid.get(i);
            var ex = assertThrows(DomainException.class, () -> bookingQueries.myBookings(authorActor, query));
            assertEquals(Reason.INVALID_INPUT, ex.getReason());
            assertEquals(fields.get(i), ex.getErrors().getFirst().field());
        }
        verifyNoInteractions(bookingRepository, roomRepository, userDirectory);
    }

    @Test
    @Tag("TS-06")
    void adminReadsRejectEmployeeBeforeReading() {
        assertEquals(Reason.NOT_PERMITTED, assertThrows(DomainException.class,
                () -> bookingQueries.allBookings(authorActor, null)).getReason());
        assertEquals(Reason.NOT_PERMITTED, assertThrows(DomainException.class,
                () -> bookingQueries.filterOptions(authorActor)).getReason());
        verifyNoInteractions(bookingRepository, roomRepository, userDirectory);
    }

    @Test
    @Tag("A-31")
    @Tag("D-12")
    void listBatchesAuthorsAndCancellersAndUsesActorAuthor() {
        Booking active = createBooking(1, 5, 10, LocalDateTime.of(2026, 11, 2, 11, 0),
                LocalDateTime.of(2026, 11, 2, 12, 0), BookingStatus.ACTIVE);
        Booking cancelled = createBooking(2, 5, 10, LocalDateTime.of(2026, 11, 2, 12, 0),
                LocalDateTime.of(2026, 11, 2, 13, 0), BookingStatus.ACTIVE);
        cancelled.cancel(adminActor, officeClock.snapshot());
        when(bookingRepository.findPage(null, null, null, null, 10L, false, 0L, 21))
                .thenReturn(java.util.List.of(new roombook.scheduling.persistence.BookingRow(active, "Room"),
                        new roombook.scheduling.persistence.BookingRow(cancelled, "Room")));
        when(userDirectory.displayNames(Set.of(10L, 99L))).thenReturn(Map.of(10L, "Author", 99L, "Admin"));
        var page = bookingQueries.myBookings(authorActor,
                new BookingFilterQuery(null, null, null, null, 999L, null, null, null));
        assertEquals(20, page.size());
        assertFalse(page.hasNext());
        assertEquals(BookingPhase.UPCOMING, page.items().getFirst().phase());
        assertTrue(page.items().getFirst().cancellable());
        assertNull(page.items().getLast().phase());
        assertFalse(page.items().getLast().cancellable());
        assertEquals("Admin", page.items().getLast().cancelledBy().displayName());
        verify(userDirectory).displayNames(Set.of(10L, 99L));
        verifyNoInteractions(roomRepository);
    }
}

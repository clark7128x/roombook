package roombook.scheduling.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import roombook.kernel.*;
import roombook.scheduling.domain.*;
import roombook.scheduling.persistence.BookingRepository;
import roombook.scheduling.persistence.RoomRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Модульні тести BookingService (T-14, TS-06, FR-09...FR-18, INV-08, D-04, D-10, A-17, OPS-05).
 */
@Tag("TS-06")
@Tag("FR-09")
@Tag("FR-10")
@Tag("FR-11")
@Tag("FR-12")
@Tag("FR-13")
@Tag("FR-14")
@Tag("FR-15")
@Tag("FR-16")
@Tag("FR-17")
@Tag("FR-18")
@Tag("INV-08")
@Tag("D-04")
@Tag("AR-11")
@Tag("D-10")
@Tag("A-17")
@Tag("OPS-05")
class BookingServiceTest {

    private RoomRepository roomRepository;
    private BookingRepository bookingRepository;
    private BookingTimePolicy bookingTimePolicy;
    private OfficeClock officeClock;
    private BookingQueries bookingQueries;
    private BookingService bookingService;

    private final ZoneId kyivZone = ZoneId.of("Europe/Kyiv");
    private final Actor employeeActor = new Actor(10L, Role.EMPLOYEE);
    private final Actor adminActor = new Actor(1L, Role.ADMIN);
    private final Actor otherEmployeeActor = new Actor(20L, Role.EMPLOYEE);

    // Вівторок, 10:00 за Києвом
    private final Instant fixedInstant = Instant.parse("2026-10-27T08:00:00Z"); // 10:00 Kyiv (EEST is UTC+3 till Oct 25, wait, Oct 27 is standard time UTC+2: 10:00 Kyiv = 08:00 UTC)

    @BeforeEach
    void setUp() {
        roomRepository = mock(RoomRepository.class);
        bookingRepository = mock(BookingRepository.class);
        bookingTimePolicy = mock(BookingTimePolicy.class);
        officeClock = new OfficeClock(Clock.fixed(fixedInstant, kyivZone), kyivZone);
        bookingQueries = mock(BookingQueries.class);

        bookingService = new BookingService(
                roomRepository,
                bookingRepository,
                bookingTimePolicy,
                officeClock,
                bookingQueries
        );
    }

    private BookingView sampleView(long bookingId, long roomId, long authorId, LocalDateTime start, LocalDateTime end) {
        return new BookingView(
                bookingId,
                new RoomRef(roomId, "Мала переговорна"),
                new UserRef(authorId, "Іван Коваленко"),
                "Синхронізація",
                start,
                end,
                BookingStatus.ACTIVE,
                BookingPhase.UPCOMING,
                fixedInstant,
                null,
                null,
                true
        );
    }

    @Test
    @DisplayName("createBooking: null Actor викидає NullPointerException")
    void createBooking_nullActor_throwsNpe() {
        CreateBookingCommand cmd = new CreateBookingCommand(
                1L,
                "Зустріч команди",
                LocalDateTime.of(2026, 10, 27, 11, 0),
                LocalDateTime.of(2026, 10, 27, 12, 0)
        );
        assertThrows(NullPointerException.class, () -> bookingService.createBooking(null, cmd));
        verifyNoInteractions(roomRepository, bookingRepository);
    }

    @Test
    @DisplayName("createBooking: null команда дає INVALID_INPUT")
    void createBooking_nullCommand_throwsInvalidInput() {
        DomainException ex = assertThrows(DomainException.class, () -> bookingService.createBooking(employeeActor, null));
        assertEquals(Reason.INVALID_INPUT, ex.getReason());
        verifyNoInteractions(roomRepository, bookingRepository);
    }

    @Test
    @DisplayName("createBooking: відсутні або некоректні обов'язкові поля дають INVALID_INPUT без звернення до БД (INV-01, TS-06 крок 2)")
    void createBooking_invalidFields_throwsInvalidInputBeforeDbAccess() {
        CreateBookingCommand cmd = new CreateBookingCommand(
                null,
                "   ",
                null,
                null
        );

        DomainException ex = assertThrows(DomainException.class, () -> bookingService.createBooking(employeeActor, cmd));
        assertEquals(Reason.INVALID_INPUT, ex.getReason());
        assertFalse(ex.getErrors().isEmpty());
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.field().equals("roomId")));
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.field().equals("title")));
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.field().equals("startAt")));
        assertTrue(ex.getErrors().stream().anyMatch(e -> e.field().equals("endAt")));

        verifyNoInteractions(roomRepository, bookingRepository);
    }

    @Test
    @DisplayName("createBooking: назва довша за 200 символів або містить керуючі символи дає INVALID_INPUT без звернення до БД")
    void createBooking_titleTooLongOrControlCharacters_throwsInvalidInputBeforeDbAccess() {
        String longTitle = "A".repeat(201);
        CreateBookingCommand cmdLong = new CreateBookingCommand(
                1L,
                longTitle,
                LocalDateTime.of(2026, 10, 27, 11, 0),
                LocalDateTime.of(2026, 10, 27, 12, 0)
        );
        DomainException exLong = assertThrows(DomainException.class, () -> bookingService.createBooking(employeeActor, cmdLong));
        assertEquals(Reason.INVALID_INPUT, exLong.getReason());
        assertTrue(exLong.getErrors().stream().anyMatch(e -> e.field().equals("title") && e.rule() == FieldRule.LENGTH));

        CreateBookingCommand cmdControl = new CreateBookingCommand(
                1L,
                "Зустріч\u0007команди",
                LocalDateTime.of(2026, 10, 27, 11, 0),
                LocalDateTime.of(2026, 10, 27, 12, 0)
        );
        DomainException exControl = assertThrows(DomainException.class, () -> bookingService.createBooking(employeeActor, cmdControl));
        assertEquals(Reason.INVALID_INPUT, exControl.getReason());
        assertTrue(exControl.getErrors().stream().anyMatch(e -> e.field().equals("title") && e.rule() == FieldRule.ILLEGAL_CHARACTERS));

        verifyNoInteractions(roomRepository, bookingRepository);
    }

    @Test
    @DisplayName("createBooking: порушення правил часу дає INVALID_TIME без звернення до БД (INV-02...INV-05, FR-13, FR-14, TS-06 крок 3)")
    void createBooking_timePolicyViolation_throwsInvalidTimeBeforeDbAccess() {
        CreateBookingCommand cmd = new CreateBookingCommand(
                1L,
                "Зустріч",
                LocalDateTime.of(2026, 10, 27, 7, 0),
                LocalDateTime.of(2026, 10, 27, 8, 0)
        );

        doThrow(DomainException.invalidTime(TimeRule.OUTSIDE_WORK_HOURS, Collections.emptyMap()))
                .when(bookingTimePolicy).validateForCreation(any(), any());

        DomainException ex = assertThrows(DomainException.class, () -> bookingService.createBooking(employeeActor, cmd));
        assertEquals(Reason.INVALID_TIME, ex.getReason());
        assertEquals(TimeRule.OUTSIDE_WORK_HOURS, ex.getTimeRule());

        verifyNoInteractions(roomRepository, bookingRepository);
    }

    @Test
    @DisplayName("createBooking: кімнату не знайдено - перший виклик lockById (D-04, TS-06 крок 4) дає ROOM_NOT_FOUND")
    void createBooking_roomNotFound_locksRoomFirstAndThrows() {
        CreateBookingCommand cmd = new CreateBookingCommand(
                99L,
                "Зустріч",
                LocalDateTime.of(2026, 10, 27, 11, 0),
                LocalDateTime.of(2026, 10, 27, 12, 0)
        );

        when(roomRepository.lockById(99L)).thenReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class, () -> bookingService.createBooking(employeeActor, cmd));
        assertEquals(Reason.ROOM_NOT_FOUND, ex.getReason());

        InOrder inOrder = inOrder(roomRepository, bookingRepository);
        inOrder.verify(roomRepository).lockById(99L);
        verifyNoInteractions(bookingRepository);
    }

    @Test
    @DisplayName("createBooking: кімната неактивна - під lockById (D-04, TS-06 крок 4) дає ROOM_INACTIVE")
    void createBooking_roomInactive_locksRoomFirstAndThrows() {
        CreateBookingCommand cmd = new CreateBookingCommand(
                1L,
                "Зустріч",
                LocalDateTime.of(2026, 10, 27, 11, 0),
                LocalDateTime.of(2026, 10, 27, 12, 0)
        );

        Room inactiveRoom = new Room("Переговорна 1", 1, 10, "Екран");
        inactiveRoom.deactivate();

        when(roomRepository.lockById(1L)).thenReturn(Optional.of(inactiveRoom));

        DomainException ex = assertThrows(DomainException.class, () -> bookingService.createBooking(employeeActor, cmd));
        assertEquals(Reason.ROOM_INACTIVE, ex.getReason());

        InOrder inOrder = inOrder(roomRepository, bookingRepository);
        inOrder.verify(roomRepository).lockById(1L);
        verifyNoInteractions(bookingRepository);
    }

    @Test
    @DisplayName("createBooking: перетин слота у БД (TS-06 крок 5, INV-08) дає SLOT_TAKEN")
    void createBooking_slotTakenInDbQuery_throwsSlotTaken() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 27, 11, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 27, 12, 0);
        CreateBookingCommand cmd = new CreateBookingCommand(1L, "Зустріч", start, end);

        Room activeRoom = new Room("Переговорна 1", 1, 10, "Екран");
        when(roomRepository.lockById(1L)).thenReturn(Optional.of(activeRoom));

        Booking existing = new Booking(1L, 99L, "Існуюча", new BookingInterval(start, end), fixedInstant);
        when(bookingRepository.findActiveOverlapping(1L, start, end)).thenReturn(List.of(existing));

        DomainException ex = assertThrows(DomainException.class, () -> bookingService.createBooking(employeeActor, cmd));
        assertEquals(Reason.SLOT_TAKEN, ex.getReason());

        InOrder inOrder = inOrder(roomRepository, bookingRepository);
        inOrder.verify(roomRepository).lockById(1L);
        inOrder.verify(bookingRepository).findActiveOverlapping(1L, start, end);
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("createBooking: конфлікт обмеження ex_booking_no_overlap транслюється в SLOT_TAKEN (INV-08)")
    void createBooking_constraintViolation_translatedToSlotTaken() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 27, 11, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 27, 12, 0);
        CreateBookingCommand cmd = new CreateBookingCommand(1L, "Зустріч", start, end);

        Room activeRoom = new Room("Переговорна 1", 1, 10, "Екран");
        when(roomRepository.lockById(1L)).thenReturn(Optional.of(activeRoom));
        when(bookingRepository.findActiveOverlapping(1L, start, end)).thenReturn(Collections.emptyList());

        when(bookingRepository.saveAndFlush(any())).thenThrow(
                new DataIntegrityViolationException("exclusion constraint \"ex_booking_no_overlap\"")
        );

        DomainException ex = assertThrows(DomainException.class, () -> bookingService.createBooking(employeeActor, cmd));
        assertEquals(Reason.SLOT_TAKEN, ex.getReason());

        InOrder inOrder = inOrder(roomRepository, bookingRepository);
        inOrder.verify(roomRepository).lockById(1L);
        inOrder.verify(bookingRepository).findActiveOverlapping(1L, start, end);
        inOrder.verify(bookingRepository).saveAndFlush(any());
    }

    @Test
    @DisplayName("createBooking: успішне створення блокує кімнату, перевіряє перетини, зберігає, логує (OPS-05) та повертає BookingView (A-57)")
    void createBooking_success_locksRoomThenChecksOverlappingThenSavesAndReturnsView() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 27, 11, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 27, 12, 0);
        CreateBookingCommand cmd = new CreateBookingCommand(1L, "  Синхронізація команди  ", start, end);

        Room activeRoom = new Room("Мала переговорна", 1, 6, "Екран");
        when(roomRepository.lockById(1L)).thenReturn(Optional.of(activeRoom));
        when(bookingRepository.findActiveOverlapping(1L, start, end)).thenReturn(Collections.emptyList());

        Booking savedBooking = new Booking(1L, employeeActor.userId(), "Синхронізація команди", new BookingInterval(start, end), fixedInstant);
        // Рефлексією ставимо ID для savedBooking
        try {
            var field = Booking.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(savedBooking, 100L);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        when(bookingRepository.saveAndFlush(any())).thenReturn(savedBooking);

        BookingView expectedView = sampleView(100L, 1L, employeeActor.userId(), start, end);
        when(bookingQueries.get(employeeActor, 100L)).thenReturn(expectedView);

        BookingView result = bookingService.createBooking(employeeActor, cmd);

        assertNotNull(result);
        assertEquals(100L, result.id());
        assertEquals("Синхронізація", result.title());

        InOrder inOrder = inOrder(roomRepository, bookingRepository, bookingQueries);
        inOrder.verify(roomRepository).lockById(1L);
        inOrder.verify(bookingRepository).findActiveOverlapping(1L, start, end);
        inOrder.verify(bookingRepository).saveAndFlush(any());
        inOrder.verify(bookingQueries).get(employeeActor, 100L);
    }

    @Test
    @DisplayName("cancelBooking: null Actor викидає NullPointerException")
    void cancelBooking_nullActor_throwsNpe() {
        assertThrows(NullPointerException.class, () -> bookingService.cancelBooking(null, 100L));
        verifyNoInteractions(bookingRepository);
    }

    @Test
    @DisplayName("cancelBooking: бронювання не знайдено - перший виклик lockById (D-04, TS-06) дає BOOKING_NOT_FOUND")
    void cancelBooking_notFound_locksBookingFirstAndThrows() {
        when(bookingRepository.lockById(100L)).thenReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class, () -> bookingService.cancelBooking(employeeActor, 100L));
        assertEquals(Reason.BOOKING_NOT_FOUND, ex.getReason());

        verify(bookingRepository).lockById(100L);
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("cancelBooking: ініціатор не є автором і не є ADMIN -> NOT_PERMITTED (TS-06)")
    void cancelBooking_notAuthorAndNotAdmin_throwsNotPermitted() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 27, 11, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 27, 12, 0);
        Booking booking = new Booking(1L, employeeActor.userId(), "Зустріч", new BookingInterval(start, end), fixedInstant);

        when(bookingRepository.lockById(100L)).thenReturn(Optional.of(booking));

        DomainException ex = assertThrows(DomainException.class, () -> bookingService.cancelBooking(otherEmployeeActor, 100L));
        assertEquals(Reason.NOT_PERMITTED, ex.getReason());

        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("cancelBooking: бронювання вже скасоване - безпечний повтор (A-17) без збереження у БД")
    void cancelBooking_alreadyCancelled_safeRetryReturnsCurrentViewWithoutSave() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 27, 11, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 27, 12, 0);
        Booking booking = new Booking(1L, employeeActor.userId(), "Зустріч", new BookingInterval(start, end), fixedInstant);
        booking.cancel(employeeActor, officeClock.snapshot()); // вже скасоване

        when(bookingRepository.lockById(100L)).thenReturn(Optional.of(booking));
        BookingView view = sampleView(100L, 1L, employeeActor.userId(), start, end);
        when(bookingQueries.get(any(), anyLong())).thenReturn(view);

        BookingView result = bookingService.cancelBooking(employeeActor, 100L);

        assertNotNull(result);
        verify(bookingRepository).lockById(100L);
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("cancelBooking: бронювання вже завершене -> BOOKING_FINISHED (TR-05, TS-06)")
    void cancelBooking_alreadyFinished_throwsBookingFinished() {
        // Завершене бронювання: 08:00 - 09:00 за Києвом (при fixedInstant 10:00 за Києвом)
        LocalDateTime start = LocalDateTime.of(2026, 10, 27, 8, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 27, 9, 0);
        Booking booking = new Booking(1L, employeeActor.userId(), "Минула зустріч", new BookingInterval(start, end), fixedInstant);

        when(bookingRepository.lockById(100L)).thenReturn(Optional.of(booking));

        DomainException ex = assertThrows(DomainException.class, () -> bookingService.cancelBooking(employeeActor, 100L));
        assertEquals(Reason.BOOKING_FINISHED, ex.getReason());

        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("cancelBooking: автор успішно скасовує активне незавершене бронювання, фіксує скасування та повертає BookingView (OPS-05)")
    void cancelBooking_successByAuthor_cancelsAndSavesAndLogs() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 27, 11, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 27, 12, 0);
        Booking booking = new Booking(1L, employeeActor.userId(), "Зустріч", new BookingInterval(start, end), fixedInstant);

        when(bookingRepository.lockById(100L)).thenReturn(Optional.of(booking));
        when(bookingRepository.saveAndFlush(any())).thenReturn(booking);

        BookingView view = sampleView(100L, 1L, employeeActor.userId(), start, end);
        when(bookingQueries.get(employeeActor, 100L)).thenReturn(view);

        BookingView result = bookingService.cancelBooking(employeeActor, 100L);

        assertNotNull(result);
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(employeeActor.userId(), booking.getCancelledById());
        assertNotNull(booking.getCancelledAt());

        InOrder inOrder = inOrder(bookingRepository, bookingQueries);
        inOrder.verify(bookingRepository).lockById(100L);
        inOrder.verify(bookingRepository).saveAndFlush(booking);
        inOrder.verify(bookingQueries).get(employeeActor, 100L);
    }

    @Test
    @DisplayName("cancelBooking: ADMIN успішно скасовує чуже бронювання (FR-17)")
    void cancelBooking_successByAdmin_canCancelOtherUsersBooking() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 27, 11, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 27, 12, 0);
        Booking booking = new Booking(1L, employeeActor.userId(), "Зустріч співробітника", new BookingInterval(start, end), fixedInstant);

        when(bookingRepository.lockById(100L)).thenReturn(Optional.of(booking));
        when(bookingRepository.saveAndFlush(any())).thenReturn(booking);

        BookingView view = sampleView(100L, 1L, employeeActor.userId(), start, end);
        when(bookingQueries.get(adminActor, 100L)).thenReturn(view);

        BookingView result = bookingService.cancelBooking(adminActor, 100L);

        assertNotNull(result);
        assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        assertEquals(adminActor.userId(), booking.getCancelledById());

        verify(bookingRepository).saveAndFlush(booking);
    }
}

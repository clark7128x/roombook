package roombook.scheduling.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import roombook.kernel.*;
import roombook.scheduling.domain.Room;
import roombook.scheduling.persistence.BookingRepository;
import roombook.scheduling.persistence.RoomRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Модульні тести RoomService (T-14, TS-06, FR-19...FR-22, INV-09, INV-11, INV-12, D-04, D-10, A-17, OPS-05).
 */
@Tag("TS-06")
@Tag("FR-19")
@Tag("FR-20")
@Tag("FR-21")
@Tag("FR-22")
@Tag("INV-09")
@Tag("INV-11")
@Tag("INV-12")
@Tag("D-04")
@Tag("D-10")
@Tag("A-17")
@Tag("OPS-05")
class RoomServiceTest {

    private RoomRepository roomRepository;
    private BookingRepository bookingRepository;
    private OfficeClock officeClock;
    private RoomService roomService;

    private final ZoneId kyivZone = ZoneId.of("Europe/Kyiv");
    private final Actor adminActor = new Actor(1L, Role.ADMIN);
    private final Actor employeeActor = new Actor(2L, Role.EMPLOYEE);

    private final Instant fixedInstant = Instant.parse("2026-10-27T08:00:00Z");

    @BeforeEach
    void setUp() {
        roomRepository = mock(RoomRepository.class);
        bookingRepository = mock(BookingRepository.class);
        officeClock = new OfficeClock(Clock.fixed(fixedInstant, kyivZone), kyivZone);

        roomService = new RoomService(roomRepository, bookingRepository, officeClock);
    }

    private void setRoomId(Room room, long id) {
        try {
            var field = Room.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(room, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---------------- requireAdmin checks (TS-06) ----------------

    @Test
    @DisplayName("Всі операції RoomService вимагають ролі ADMIN до першого звернення до БД (TS-06)")
    void allOperationsRequireAdminBeforeDbAccess() {
        CreateRoomCommand createCmd = new CreateRoomCommand("Кімната 1", 2, 8, "Проєктор");
        UpdateRoomCommand updateCmd = new UpdateRoomCommand("Кімната 1", 2, 8, "Проєктор");

        assertThrows(DomainException.class, () -> roomService.createRoom(employeeActor, createCmd));
        assertThrows(DomainException.class, () -> roomService.updateRoom(employeeActor, 1L, updateCmd));
        assertThrows(DomainException.class, () -> roomService.activateRoom(employeeActor, 1L));
        assertThrows(DomainException.class, () -> roomService.deactivateRoom(employeeActor, 1L));

        assertThrows(NullPointerException.class, () -> roomService.createRoom(null, createCmd));
        assertThrows(NullPointerException.class, () -> roomService.updateRoom(null, 1L, updateCmd));
        assertThrows(NullPointerException.class, () -> roomService.activateRoom(null, 1L));
        assertThrows(NullPointerException.class, () -> roomService.deactivateRoom(null, 1L));

        verifyNoInteractions(roomRepository, bookingRepository);
    }

    // ---------------- createRoom (FR-19) ----------------

    @Test
    @DisplayName("createRoom: null команда дає INVALID_INPUT")
    void createRoom_nullCommand_throwsInvalidInput() {
        DomainException ex = assertThrows(DomainException.class, () -> roomService.createRoom(adminActor, null));
        assertEquals(Reason.INVALID_INPUT, ex.getReason());
        verifyNoInteractions(roomRepository);
    }

    @Test
    @DisplayName("createRoom: конфлікт унікальності назви кімнати (uq_room_name_key) транслюється в ROOM_NAME_TAKEN")
    void createRoom_duplicateName_translatedToRoomNameTaken() {
        CreateRoomCommand cmd = new CreateRoomCommand("Велика переговорна", 3, 20, "Відеопанель");
        when(roomRepository.saveAndFlush(any())).thenThrow(
                new DataIntegrityViolationException("unique constraint \"uq_room_name_key\"")
        );

        DomainException ex = assertThrows(DomainException.class, () -> roomService.createRoom(adminActor, cmd));
        assertEquals(Reason.ROOM_NAME_TAKEN, ex.getReason());
    }

    @Test
    @DisplayName("createRoom: успішне створення зберігає кімнату, логує (OPS-05) та повертає RoomView")
    void createRoom_success_savesAndReturnsView() {
        CreateRoomCommand cmd = new CreateRoomCommand("  Велика переговорна  ", 3, 20, "  Відеопанель  ");

        when(roomRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Room r = invocation.getArgument(0);
            setRoomId(r, 10L);
            return r;
        });

        RoomView view = roomService.createRoom(adminActor, cmd);

        assertNotNull(view);
        assertEquals(10L, view.id());
        assertEquals("Велика переговорна", view.name());
        assertEquals(3, view.floor());
        assertEquals(20, view.capacity());
        assertEquals("Відеопанель", view.equipment());
        assertTrue(view.active());

        verify(roomRepository).saveAndFlush(any(Room.class));
    }

    // ---------------- updateRoom (FR-20) ----------------

    @Test
    @DisplayName("updateRoom: null команда дає INVALID_INPUT")
    void updateRoom_nullCommand_throwsInvalidInput() {
        DomainException ex = assertThrows(DomainException.class, () -> roomService.updateRoom(adminActor, 1L, null));
        assertEquals(Reason.INVALID_INPUT, ex.getReason());
        verifyNoInteractions(roomRepository);
    }

    @Test
    @DisplayName("updateRoom: кімнату не знайдено - перший виклик lockById (D-04, TS-06) дає ROOM_NOT_FOUND")
    void updateRoom_roomNotFound_locksRoomFirstAndThrows() {
        UpdateRoomCommand cmd = new UpdateRoomCommand("Нова назва", 2, 10, null);
        when(roomRepository.lockById(99L)).thenReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class, () -> roomService.updateRoom(adminActor, 99L, cmd));
        assertEquals(Reason.ROOM_NOT_FOUND, ex.getReason());

        verify(roomRepository).lockById(99L);
        verify(roomRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("updateRoom: конфлікт унікальності назви кімнати (uq_room_name_key) транслюється в ROOM_NAME_TAKEN")
    void updateRoom_duplicateName_translatedToRoomNameTaken() {
        Room room = new Room("Стара назва", 2, 10, null);
        setRoomId(room, 5L);

        when(roomRepository.lockById(5L)).thenReturn(Optional.of(room));
        when(roomRepository.saveAndFlush(any())).thenThrow(
                new DataIntegrityViolationException("unique constraint \"uq_room_name_key\"")
        );

        UpdateRoomCommand cmd = new UpdateRoomCommand("Зайнята назва", 2, 10, null);
        DomainException ex = assertThrows(DomainException.class, () -> roomService.updateRoom(adminActor, 5L, cmd));
        assertEquals(Reason.ROOM_NAME_TAKEN, ex.getReason());

        verify(roomRepository).lockById(5L);
        verify(roomRepository).saveAndFlush(room);
    }

    @Test
    @DisplayName("updateRoom: успішне оновлення атрибутів кімнати під блокуванням рядка (D-04, OPS-05)")
    void updateRoom_success_locksThenUpdatesAndSaves() {
        Room room = new Room("Стара назва", 2, 10, "Дошка");
        setRoomId(room, 5L);

        when(roomRepository.lockById(5L)).thenReturn(Optional.of(room));
        when(roomRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateRoomCommand cmd = new UpdateRoomCommand("  Нова назва  ", 4, 15, "  Екран, Камера  ");
        RoomView view = roomService.updateRoom(adminActor, 5L, cmd);

        assertNotNull(view);
        assertEquals(5L, view.id());
        assertEquals("Нова назва", view.name());
        assertEquals(4, view.floor());
        assertEquals(15, view.capacity());
        assertEquals("Екран, Камера", view.equipment());

        InOrder inOrder = inOrder(roomRepository);
        inOrder.verify(roomRepository).lockById(5L);
        inOrder.verify(roomRepository).saveAndFlush(room);
    }

    // ---------------- activateRoom (FR-22) ----------------

    @Test
    @DisplayName("activateRoom: кімнату не знайдено - перший виклик lockById (D-04) дає ROOM_NOT_FOUND")
    void activateRoom_notFound_locksRoomFirstAndThrows() {
        when(roomRepository.lockById(99L)).thenReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class, () -> roomService.activateRoom(adminActor, 99L));
        assertEquals(Reason.ROOM_NOT_FOUND, ex.getReason());

        verify(roomRepository).lockById(99L);
        verify(roomRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("activateRoom: кімната вже активна - безпечний повтор (A-17) повертає стан без збереження у БД")
    void activateRoom_alreadyActive_safeRetryReturnsWithoutSave() {
        Room room = new Room("Кімната", 2, 10, null);
        setRoomId(room, 5L);
        assertTrue(room.isActive());

        when(roomRepository.lockById(5L)).thenReturn(Optional.of(room));

        RoomView view = roomService.activateRoom(adminActor, 5L);

        assertNotNull(view);
        assertTrue(view.active());
        verify(roomRepository).lockById(5L);
        verify(roomRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("activateRoom: неактивна кімната активується, зберігається та логується (OPS-05)")
    void activateRoom_inactive_locksThenActivatesAndSaves() {
        Room room = new Room("Кімната", 2, 10, null);
        setRoomId(room, 5L);
        room.deactivate();
        assertFalse(room.isActive());

        when(roomRepository.lockById(5L)).thenReturn(Optional.of(room));
        when(roomRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        RoomView view = roomService.activateRoom(adminActor, 5L);

        assertNotNull(view);
        assertTrue(view.active());
        assertTrue(room.isActive());

        InOrder inOrder = inOrder(roomRepository);
        inOrder.verify(roomRepository).lockById(5L);
        inOrder.verify(roomRepository).saveAndFlush(room);
    }

    // ---------------- deactivateRoom (FR-21) ----------------

    @Test
    @DisplayName("deactivateRoom: кімнату не знайдено - перший виклик lockById (D-04) дає ROOM_NOT_FOUND")
    void deactivateRoom_notFound_locksRoomFirstAndThrows() {
        when(roomRepository.lockById(99L)).thenReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class, () -> roomService.deactivateRoom(adminActor, 99L));
        assertEquals(Reason.ROOM_NOT_FOUND, ex.getReason());

        verify(roomRepository).lockById(99L);
        verify(roomRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("deactivateRoom: кімната вже неактивна - безпечний повтор (A-17) без перевірки бронювань та збереження")
    void deactivateRoom_alreadyInactive_safeRetryReturnsWithoutSave() {
        Room room = new Room("Кімната", 2, 10, null);
        setRoomId(room, 5L);
        room.deactivate();
        assertFalse(room.isActive());

        when(roomRepository.lockById(5L)).thenReturn(Optional.of(room));

        RoomView view = roomService.deactivateRoom(adminActor, 5L);

        assertNotNull(view);
        assertFalse(view.active());
        verify(roomRepository).lockById(5L);
        verifyNoInteractions(bookingRepository);
        verify(roomRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("deactivateRoom: наявність незавершених бронювань (INV-09) дає ROOM_HAS_UNFINISHED_BOOKINGS без зміни стану")
    void deactivateRoom_hasUnfinishedBookings_throwsRoomHasUnfinishedBookings() {
        Room room = new Room("Кімната", 2, 10, null);
        setRoomId(room, 5L);
        assertTrue(room.isActive());

        when(roomRepository.lockById(5L)).thenReturn(Optional.of(room));
        when(bookingRepository.existsUnfinished(eq(5L), any())).thenReturn(true);

        DomainException ex = assertThrows(DomainException.class, () -> roomService.deactivateRoom(adminActor, 5L));
        assertEquals(Reason.ROOM_HAS_UNFINISHED_BOOKINGS, ex.getReason());
        assertTrue(room.isActive());

        verify(roomRepository).lockById(5L);
        verify(bookingRepository).existsUnfinished(eq(5L), any());
        verify(roomRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("deactivateRoom: активна кімната без незавершених бронювань деактивується, зберігається та логується (OPS-05)")
    void deactivateRoom_success_locksThenDeactivatesAndSaves() {
        Room room = new Room("Кімната", 2, 10, null);
        setRoomId(room, 5L);
        assertTrue(room.isActive());

        when(roomRepository.lockById(5L)).thenReturn(Optional.of(room));
        when(bookingRepository.existsUnfinished(eq(5L), any())).thenReturn(false);
        when(roomRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        RoomView view = roomService.deactivateRoom(adminActor, 5L);

        assertNotNull(view);
        assertFalse(view.active());
        assertFalse(room.isActive());

        InOrder inOrder = inOrder(roomRepository, bookingRepository);
        inOrder.verify(roomRepository).lockById(5L);
        inOrder.verify(bookingRepository).existsUnfinished(eq(5L), any());
        inOrder.verify(roomRepository).saveAndFlush(room);
    }
}

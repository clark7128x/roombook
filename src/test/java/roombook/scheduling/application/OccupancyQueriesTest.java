package roombook.scheduling.application;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import roombook.accounts.api.UserDirectory;
import roombook.kernel.*;
import roombook.scheduling.domain.*;
import roombook.scheduling.persistence.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OccupancyQueriesTest {
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final UserDirectory users = mock(UserDirectory.class);
    private final Actor employee = new Actor(1L, Role.EMPLOYEE);
    private final Actor admin = new Actor(2L, Role.ADMIN);
    private final LocalDate date = LocalDate.of(2026, 11, 2);
    private final OfficeClock clock = new OfficeClock(Clock.fixed(Instant.parse("2026-11-01T23:00:00Z"),
            ZoneOffset.UTC), ZoneId.of("Europe/Kyiv"));
    private final OccupancyQueries queries = new OccupancyQueries(rooms, bookings, users,
            new BookingTimePolicy(BookingRules.defaultRules()), clock);

    @Test
    @Tag("TS-06")
    void rejectsPermissionsAndInvalidFieldsBeforeReading() {
        assertEquals(Reason.NOT_PERMITTED, assertThrows(DomainException.class, () -> queries.findRooms(employee,
                new RoomFilterQuery(null, null, null, null, true))).getReason());
        var start = date.atTime(10, 0);
        var invalid = List.of(new RoomFilterQuery(null, null, start, null, false),
                new RoomFilterQuery(null, null, null, start, false),
                new RoomFilterQuery(null, null, start, start.plusHours(1), true),
                new RoomFilterQuery(null, 0, null, null, false),
                new RoomFilterQuery(null, null, start.withYear(1999), start.withYear(1999).plusHours(1), false),
                new RoomFilterQuery(null, null, start.withYear(2101), start.withYear(2101).plusHours(1), false));
        var fields = List.of("freeTo", "freeFrom", "includeInactive", "minCapacity", "freeFrom", "freeFrom");
        for (int i = 0; i < invalid.size(); i++) {
            var query = invalid.get(i);
            var ex = assertThrows(DomainException.class, () -> queries.findRooms(admin, query));
            assertEquals(Reason.INVALID_INPUT, ex.getReason());
            assertEquals(fields.get(i), ex.getErrors().getFirst().field());
            if (i == 2) assertEquals(FieldRule.ILLEGAL_STATE, ex.getErrors().getFirst().rule());
        }
        for (int year : List.of(1999, 2101)) {
            assertEquals(Reason.INVALID_INPUT, assertThrows(DomainException.class,
                    () -> queries.getOccupancy(employee, new OccupancyQuery(date.withYear(year), null, null))).getReason());
        }
        assertEquals(Reason.INVALID_INPUT, assertThrows(DomainException.class,
                () -> queries.getOccupancy(employee, new OccupancyQuery(date, null, 0))).getReason());
        verifyNoInteractions(rooms, bookings, users);
    }

    @Test
    @Tag("A-32")
    void searchChecksShapeHoursGridDurationButNotCreationHorizon() {
        var starts = List.of(date.atTime(10, 0), date.atTime(7, 45), date.atTime(10, 7), date.atTime(10, 0));
        var ends = List.of(date.atTime(10, 0), date.atTime(8, 15), date.atTime(10, 22), date.atTime(14, 15));
        var rules = List.of(TimeRule.INTERVAL_SHAPE, TimeRule.OUTSIDE_WORK_HOURS, TimeRule.NOT_ON_GRID, TimeRule.BAD_DURATION);
        for (int i = 0; i < starts.size(); i++) {
            var query = new RoomFilterQuery(null, null, starts.get(i), ends.get(i), false);
            var ex = assertThrows(DomainException.class, () -> queries.findRooms(employee, query));
            assertEquals(Reason.INVALID_TIME, ex.getReason());
            assertEquals(rules.get(i), ex.getTimeRule());
        }
        for (int year : List.of(2000, 2100)) {
            assertDoesNotThrow(() -> queries.findRooms(employee,
                    new RoomFilterQuery(null, null, date.withYear(year).atTime(10, 0), date.withYear(year).atTime(11, 0), false)));
        }
    }

    @Test
    @Tag("A-48")
    @Tag("A-38")
    void sortsUkrainianNamesAfterFloorAndReadsInactiveRoom() {
        List<Room> data = new ArrayList<>();
        for (String name : List.of("Ї", "І", "Є", "Ґ", "Г", "Е", "И", "Й")) {
            Room room = new Room(name, 1, 10, null);
            ReflectionTestUtils.setField(room, "id", (long) data.size() + 1);
            data.add(room);
        }
        Room lower = new Room("Я", 0, 10, null);
        ReflectionTestUtils.setField(lower, "id", 99L);
        data.add(lower);
        when(rooms.findMatching(null, null, false, false, null, null)).thenReturn(data);
        assertEquals(List.of("Я", "Г", "Ґ", "Е", "Є", "И", "І", "Ї", "Й"),
                queries.findRooms(employee, new RoomFilterQuery(null, null, null, null, null))
                        .stream().map(RoomView::name).toList());
        when(rooms.findById(99L)).thenReturn(Optional.of(lower));
        assertTrue(queries.getRoom(employee, 99).active());
        lower.deactivate();
        assertFalse(queries.getRoom(employee, 99).active());
        assertEquals(Reason.ROOM_NOT_FOUND, assertThrows(DomainException.class,
                () -> queries.getRoom(employee, 100)).getReason());
    }

    @Test
    @Tag("A-31")
    void defaultDateUsesOfficeDayAndRetainsEmptyRooms() {
        Room room = new Room("Room", 1, 10, null);
        ReflectionTestUtils.setField(room, "id", 1L);
        when(rooms.findMatching(null, null, false, false, null, null)).thenReturn(List.of(room));
        var view = queries.getOccupancy(employee, new OccupancyQuery(null, null, null));
        assertEquals(date, view.date());
        assertEquals(1, view.rooms().size());
        assertTrue(view.rooms().getFirst().bookings().isEmpty());
        verify(bookings).findOccupancy(date.atStartOfDay(), date.plusDays(1).atStartOfDay(), null, null);
    }
}

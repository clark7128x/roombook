package roombook.scheduling.application;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import roombook.kernel.Actor;
import roombook.kernel.Role;
import roombook.scheduling.domain.BookingStatus;
import roombook.testsupport.PostgresContainerHolder;
import roombook.testsupport.TestDataBuilder;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SchedulingQueriesTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        var container = PostgresContainerHolder.getInstance();
        String url = "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/roombook";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "roombook_app");
        registry.add("spring.datasource.password", () -> "app_pass");
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", () -> "roombook_owner");
        registry.add("spring.flyway.password", () -> "owner_pass");
    }

    @Autowired BookingQueries bookings;
    @Autowired OccupancyQueries occupancy;
    @Autowired EntityManagerFactory entityManagerFactory;
    private final LocalDate date = LocalDate.of(2026, 11, 2);
    private Actor author;
    private Actor admin;
    private long room;
    private long emptyRoom;
    private long inactiveRoom;

    @BeforeEach
    void setup() {
        PostgresContainerHolder.cleanDatabase();
        author = new Actor(TestDataBuilder.appUser().username("author").displayName("Автор").insert(), Role.EMPLOYEE);
        admin = new Actor(TestDataBuilder.appUser().username("admin").displayName("Адмін").role("ADMIN").insert(), Role.ADMIN);
        room = TestDataBuilder.room().name("Ґанок").floor(2).capacity(10).insert();
        emptyRoom = TestDataBuilder.room().name("Галерея").floor(2).capacity(20).insert();
        inactiveRoom = TestDataBuilder.room().name("Архів").floor(1).active(false).insert();
    }

    private long booking(long roomId, long authorId, LocalDate day, int hour, int minute, boolean cancelled) {
        var start = day.atTime(hour, minute);
        var builder = TestDataBuilder.booking().roomId(roomId).authorId(authorId)
                .startAt(start).endAt(start.plusMinutes(15));
        if (cancelled) builder.status("CANCELLED").cancelledById(admin.userId()).cancelledAt(Instant.parse("2026-11-01T08:00:00Z"));
        return builder.insert();
    }

    @Test
    @Tag("TS-15")
    @Tag("A-31")
    void occupancyExcludesCancelledInactiveAndOtherDaysAndKeepsEmptyRooms() {
        long late = booking(room, author.userId(), date, 19, 45, false);
        long early = booking(room, author.userId(), date, 8, 0, false);
        booking(room, author.userId(), date, 9, 0, true);
        booking(room, author.userId(), date.minusDays(1), 19, 45, false);
        booking(room, author.userId(), date.plusDays(1), 8, 0, false);
        booking(inactiveRoom, author.userId(), date, 10, 0, false);
        var result = occupancy.getOccupancy(author, new OccupancyQuery(date, null, null));
        assertEquals(List.of(emptyRoom, room), result.rooms().stream().map(r -> r.room().id()).toList());
        assertTrue(result.rooms().getFirst().bookings().isEmpty());
        assertEquals(List.of(early, late), result.rooms().getLast().bookings().stream().map(OccupancyBooking::id).toList());
        assertEquals("Автор", result.rooms().getLast().bookings().getFirst().author().displayName());
        assertEquals(List.of(emptyRoom), occupancy.getOccupancy(author, new OccupancyQuery(date, 2, 11))
                .rooms().stream().map(r -> r.room().id()).toList());
        assertTrue(occupancy.getOccupancy(author, new OccupancyQuery(date, 3, null)).rooms().isEmpty());
    }

    @Test
    @Tag("TS-15")
    @Tag("A-32")
    void freeSearchUsesHalfOpenOverlapAndCancelledDoesNotBlock() {
        booking(room, author.userId(), date, 10, 0, false);
        booking(emptyRoom, author.userId(), date, 10, 0, true);
        assertEquals(List.of(emptyRoom), occupancy.findRooms(author,
                new RoomFilterQuery(2, 10, date.atTime(10, 0), date.atTime(10, 15), false))
                .stream().map(RoomView::id).toList());
        assertEquals(2, occupancy.findRooms(author,
                new RoomFilterQuery(null, null, date.atTime(10, 15), date.atTime(10, 30), false)).size());
        assertEquals(2, occupancy.findRooms(author,
                new RoomFilterQuery(null, null, date.atTime(9, 45), date.atTime(10, 0), false)).size());
        assertEquals(3, occupancy.findRooms(admin, new RoomFilterQuery(null, null, null, null, true)).size());
        assertFalse(occupancy.getRoom(author, inactiveRoom).active());
    }

    @Test
    @Tag("TS-15")
    @Tag("D-12")
    @Tag("A-31")
    void pagesUseTwoSelectsInclusiveDatesAndStableOrdering() {
        long first = booking(room, author.userId(), date, 10, 0, false);
        long second = booking(emptyRoom, author.userId(), date, 10, 0, false);
        long last = booking(room, author.userId(), date, 19, 45, true);
        booking(room, author.userId(), date.minusDays(1), 19, 45, false);
        booking(room, author.userId(), date.plusDays(1), 8, 0, false);
        booking(emptyRoom, admin.userId(), date, 11, 0, false);
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean previouslyEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            var firstPage = bookings.myBookings(author, new BookingFilterQuery(date, date, null, null, admin.userId(), "ASC", 0, 2));
            assertEquals(2, statistics.getPrepareStatementCount(), "JOIN page plus batched user names; no COUNT or N+1");
            assertEquals(List.of(first, second), firstPage.items().stream().map(BookingView::id).toList());
            assertTrue(firstPage.hasNext());
            statistics.clear();
            var lastPage = bookings.allBookings(admin, new BookingFilterQuery(date, date, null, null, author.userId(), "asc", 1, 2));
            assertEquals(2, statistics.getPrepareStatementCount());
            assertEquals(List.of(last), lastPage.items().stream().map(BookingView::id).toList());
            assertFalse(lastPage.hasNext());
            assertEquals("Адмін", lastPage.items().getFirst().cancelledBy().displayName());
            assertNull(lastPage.items().getFirst().phase());
            assertFalse(lastPage.items().getFirst().cancellable());
            var descending = bookings.allBookings(admin, new BookingFilterQuery(date, date, null, null, author.userId(), "DeSc", 0, 3));
            assertEquals(List.of(last, second, first), descending.items().stream().map(BookingView::id).toList());
            assertFalse(descending.hasNext());
            var filtered = bookings.allBookings(admin, new BookingFilterQuery(date, date, BookingStatus.ACTIVE, room, author.userId(), null, null, null));
            assertEquals(List.of(first), filtered.items().stream().map(BookingView::id).toList());
            assertTrue(bookings.allBookings(admin, new BookingFilterQuery(null, null, null, Long.MAX_VALUE, null, null, 0, 20)).items().isEmpty());
            assertTrue(bookings.allBookings(admin, new BookingFilterQuery(null, null, null, null, Long.MAX_VALUE, null, 0, 20)).items().isEmpty());
            assertTrue(bookings.myBookings(author, new BookingFilterQuery(null, null, null, null, null, null, Integer.MAX_VALUE, 100)).items().isEmpty());
        } finally {
            statistics.setStatisticsEnabled(previouslyEnabled);
        }
    }

    @Test
    @Tag("A-34")
    @Tag("A-48")
    void filterOptionsIncludeInactiveRoomsAndUsers() {
        long disabled = TestDataBuilder.appUser().username("disabled").displayName("Ярина").enabled(false).insert();
        var options = bookings.filterOptions(admin);
        assertEquals(List.of(inactiveRoom, emptyRoom, room), options.rooms().stream().map(RoomView::id).toList());
        assertTrue(options.users().stream().anyMatch(u -> u.id() == disabled));
    }
}

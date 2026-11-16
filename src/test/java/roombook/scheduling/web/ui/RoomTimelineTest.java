package roombook.scheduling.web.ui;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import roombook.kernel.*;
import roombook.scheduling.application.*;
import roombook.scheduling.configuration.*;
import roombook.scheduling.domain.BookingRules;
import static org.junit.jupiter.api.Assertions.*;

class RoomTimelineTest {
    private final LocalDate date = LocalDate.of(2026, 10, 27);
    private final Actor actor = new Actor(7, Role.EMPLOYEE);
    private OfficeClock.Snapshot now() {
        return new OfficeClock(Clock.fixed(Instant.parse("2026-10-27T08:07:00Z"), ZoneOffset.UTC), ZoneId.of("Europe/Kyiv")).snapshot();
    }
    private OccupancyBooking booking(long id, String start, String end, long author) {
        return new OccupancyBooking(id, date.atTime(LocalTime.parse(start)), date.atTime(LocalTime.parse(end)), "Назва " + id, new UserRef(author, "Автор " + author));
    }
    private OccupancyView view(LocalDate day, OccupancyBooking... bookings) {
        return new OccupancyView(day, List.of(new OccupancyRoom(new RoomView(1, "Альфа", 2, 8, "Екран", true), List.of(bookings))));
    }
    @Test @Tag("TS-11") @Tag("UI-12") @Tag("A-65")
    void rowsSpansOwnershipAndCurrentSlotAreDeterministic() {
        var timeline = RoomTimeline.build(view(date, booking(1, "10:00", "10:15", 7), booking(2, "11:00", "15:00", 9)), now(), BookingRules.defaultRules(), actor);
        assertEquals(48, timeline.rows().size()); assertEquals(4, timeline.rows().getFirst().timeSpan());
        assertTrue(timeline.rows().get(7).past()); assertTrue(timeline.rows().get(8).now());
        assertFalse(timeline.rows().get(8).past());
        var mine = timeline.rows().get(8).cells().getFirst();
        assertEquals(1, mine.span()); assertTrue(mine.mine()); assertFalse(mine.covered());
        var longBooking = timeline.rows().get(12).cells().getFirst();
        assertEquals(16, longBooking.span()); assertEquals(16, longBooking.cssSpan()); assertFalse(longBooking.mine());
        assertTrue(timeline.rows().get(13).cells().getFirst().covered());
        assertEquals("11:00–15:00, Назва 2, Автор 9", longBooking.tooltip());
        assertFalse(timeline.rows().getFirst().cells().getFirst().link());
        assertTrue(timeline.rows().get(9).cells().getFirst().link());
        assertEquals("10:07", timeline.asOf());
    }
    @Test @Tag("TS-11") @Tag("R-18")
    void legacyBookingsExtendHoursRoundOutwardAndOverlapsWarnWithoutCrashing() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(RoomTimeline.class);
        var capture = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        capture.start(); logger.addAppender(capture);
        try {
            var timeline = RoomTimeline.build(view(date, booking(1, "07:10", "08:05", 7), booking(2, "07:30", "08:00", 9),
                    booking(3, "20:15", "20:45", 9)), now(), BookingRules.defaultRules(), actor);
            assertEquals(56, timeline.rows().size()); assertEquals("07:00", timeline.rows().getFirst().time());
            assertEquals(5, timeline.rows().getFirst().cells().getFirst().span());
            assertTrue(timeline.rows().getFirst().cells().getFirst().tooltip().startsWith("07:10–08:05"));
            assertTrue(capture.list.stream().anyMatch(event -> event.getLevel() == ch.qos.logback.classic.Level.WARN));
            assertFalse(timeline.rows().getLast().cells().getFirst().link());
        } finally { logger.detachAppender(capture); capture.stop(); }
    }
    @Test @Tag("TS-11") @Tag("UI-12")
    void pastDaysAndOutsideOfficeHoursHaveNoCurrentLine() {
        var past = RoomTimeline.build(view(date.minusDays(1)), now(), BookingRules.defaultRules(), actor);
        assertTrue(past.empty()); assertFalse(past.bookableDate());
        assertTrue(past.rows().stream().allMatch(row -> row.past() && !row.now() && !row.cells().getFirst().link()));
        var late = new OfficeClock.Snapshot(Instant.parse("2026-10-27T19:00:00Z"), date.atTime(21, 0), date);
        assertTrue(RoomTimeline.build(view(date), late, BookingRules.defaultRules(), actor).rows().stream().noneMatch(RoomTimeline.Row::now));
    }
    @Test @Tag("OPS-09") @Tag("A-65")
    void startupRejectsFiftySixSlotsAndBindsConfiguredBusinessRules() {
        var runner = new ApplicationContextRunner().withUserConfiguration(SchedulingConfiguration.class, TimelineConfiguration.class);
        runner.withPropertyValues("roombook.business.work-day-start=06:00", "roombook.business.work-day-end=20:00")
                .run(context -> {
                    assertNotNull(context.getStartupFailure());
                    assertTrue(context.getStartupFailure().toString().contains("48 slots"));
                    assertTrue(context.getStartupFailure().toString().contains("slot-grid"));
                });
        runner.withPropertyValues("roombook.business.work-day-start=09:00", "roombook.business.work-day-end=18:00")
                .run(context -> { assertNull(context.getStartupFailure()); assertEquals(LocalTime.of(9, 0), context.getBean(BookingRules.class).workDayStart()); });
    }
}

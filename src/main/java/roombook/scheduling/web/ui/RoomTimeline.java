package roombook.scheduling.web.ui;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.slf4j.LoggerFactory;
import roombook.kernel.Actor;
import roombook.kernel.OfficeClock;
import roombook.scheduling.application.*;
import roombook.scheduling.domain.BookingRules;

/** Immutable presentation model. Rounded display cells never change booking times. */
public record RoomTimeline(LocalDate date, List<Column> columns, List<Floor> floors, List<Row> rows,
                           boolean empty, boolean bookableDate, String asOf) {
    public record Floor(int number, int count) {}
    public record Column(RoomView room, boolean floorStart) {}
    public record Row(String time, int timeSpan, boolean hour, boolean past, boolean now, List<Cell> cells) {}
    public record Cell(long roomId, long bookingId, boolean floorStart, boolean covered, boolean free, boolean link,
                       int span, int cssSpan, boolean mine, String interval, String title, String author, String tooltip) {}
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public static RoomTimeline build(OccupancyView occupancy, OfficeClock.Snapshot clock, BookingRules rules, Actor actor) {
        long grid = rules.slotGrid().toNanos();
        LocalDate date = occupancy.date();
        LocalDateTime base = date.atTime(rules.workDayStart()), limit = date.atTime(rules.workDayEnd());
        var valid = new ArrayList<List<OccupancyBooking>>();
        var log = LoggerFactory.getLogger(RoomTimeline.class);
        for (var room : occupancy.rooms()) {
            var bookings = new ArrayList<OccupancyBooking>();
            for (var booking : room.bookings()) {
                if (!booking.startAt().toLocalDate().equals(date) || !booking.endAt().toLocalDate().equals(date)
                        || !booking.startAt().isBefore(booking.endAt())) {
                    log.warn("Invalid timeline interval ignored: bookingId={}", booking.id()); continue;
                }
                bookings.add(booking);
                if (booking.startAt().isBefore(base)) base = booking.startAt().withMinute(0).withSecond(0).withNano(0);
                if (booking.endAt().isAfter(limit)) {
                    limit = booking.endAt().withMinute(0).withSecond(0).withNano(0);
                    if (limit.isBefore(booking.endAt())) limit = limit.plusHours(1);
                }
            }
            bookings.sort(Comparator.comparing(OccupancyBooking::startAt).thenComparingLong(OccupancyBooking::id));
            valid.add(bookings);
        }
        int count = Math.toIntExact((Duration.between(base, limit).toNanos() + grid - 1) / grid);
        var columns = new ArrayList<Column>(); var floors = new ArrayList<Floor>();
        for (var item : occupancy.rooms()) {
            boolean first = columns.isEmpty() || columns.getLast().room().floor() != item.room().floor();
            columns.add(new Column(item.room(), first));
            if (first) floors.add(new Floor(item.room().floor(), 1));
            else { var floor = floors.removeLast(); floors.add(new Floor(floor.number(), floor.count() + 1)); }
        }
        Cell[][] cells = new Cell[count][columns.size()];
        boolean empty = true;
        for (int column = 0; column < columns.size(); column++) {
            var room = columns.get(column);
            for (var booking : valid.get(column)) {
                int first = Math.toIntExact(Duration.between(base, booking.startAt()).toNanos() / grid);
                int end = Math.toIntExact((Duration.between(base, booking.endAt()).toNanos() + grid - 1) / grid);
                boolean overlap = false;
                for (int slot = first; slot < end; slot++) if (cells[slot][column] != null) overlap = true;
                if (overlap) { log.warn("Overlapping timeline booking ignored: bookingId={} roomId={}", booking.id(), room.room().id()); continue; }
                int span = end - first;
                String interval = TIME.format(booking.startAt()) + "–" + TIME.format(booking.endAt());
                String tooltip = interval + ", " + booking.title() + ", " + booking.author().displayName();
                for (int slot = first; slot < end; slot++) cells[slot][column] = new Cell(room.room().id(), booking.id(), room.floorStart(),
                        slot != first, false, false, span, Math.min(span, 48), booking.author().id() == actor.userId(),
                        interval, booking.title(), booking.author().displayName(), tooltip);
                empty = false;
            }
        }
        var rows = new ArrayList<Row>();
        for (int slot = 0; slot < count; slot++) {
            LocalDateTime time = base.plusNanos(grid * slot), next = time.plusNanos(grid);
            boolean past = !next.isAfter(clock.localNow());
            boolean now = date.equals(clock.today()) && !clock.localNow().isBefore(time) && clock.localNow().isBefore(next)
                    && !clock.localTime().isBefore(rules.workDayStart()) && clock.localTime().isBefore(rules.workDayEnd());
            int timeSpan = 0;
            if (slot == 0 || time.getHour() != base.plusNanos(grid * (slot - 1)).getHour()) {
                timeSpan = 1;
                while (slot + timeSpan < count && base.plusNanos(grid * (slot + timeSpan)).getHour() == time.getHour()) timeSpan++;
            }
            var row = new ArrayList<Cell>();
            for (int column = 0; column < columns.size(); column++) {
                var room = columns.get(column);
                boolean withinWork = !time.toLocalTime().isBefore(rules.workDayStart()) && next.toLocalDate().equals(date)
                        && !next.toLocalTime().isAfter(rules.workDayEnd());
                row.add(cells[slot][column] == null ? new Cell(room.room().id(), 0, room.floorStart(), false, true,
                        !past && withinWork && room.room().active(), 1, 1, false, "", "", "", "") : cells[slot][column]);
            }
            rows.add(new Row(TIME.format(time), timeSpan, time.getMinute() == 0, past, now, List.copyOf(row)));
        }
        return new RoomTimeline(date, List.copyOf(columns), List.copyOf(floors), List.copyOf(rows), empty,
                !date.isBefore(clock.today()), TIME.format(clock.localNow()));
    }
}

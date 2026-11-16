package roombook.scheduling.web.ui;

import java.util.ArrayList;
import java.util.List;
import roombook.scheduling.application.RoomView;

/** Transposes the validated slot model; no duplicated interval or legacy-overlap policy. */
public record HorizontalTimeline(List<Hour> hours, List<RoomRow> rooms, int slots) {
    public record Hour(String time, int span, boolean now) {}
    public record RoomRow(RoomView room, List<Block> blocks) {}
    public record Block(RoomTimeline.Cell cell, int span, String time, boolean past, boolean now) {}
    public static HorizontalTimeline from(RoomTimeline timeline) {
        var hours = new ArrayList<Hour>();
        for (var row : timeline.rows()) if (row.timeSpan() > 0) hours.add(new Hour(row.time(), row.timeSpan(), row.now()));
        var rooms = new ArrayList<RoomRow>();
        for (int column = 0; column < timeline.columns().size(); column++) {
            var blocks = new ArrayList<Block>();
            for (int slot = 0; slot < timeline.rows().size();) {
                var row = timeline.rows().get(slot); var cell = row.cells().get(column);
                if (cell.covered()) { slot++; continue; }
                int span = cell.span();
                if (cell.free()) {
                    while (slot + span < timeline.rows().size()) {
                        var next = timeline.rows().get(slot + span); var nextCell = next.cells().get(column);
                        if (!nextCell.free() || nextCell.link() != cell.link() || next.past() != row.past() || next.now() || row.now()) break;
                        span++;
                    }
                }
                blocks.add(new Block(cell, span, row.time(), row.past(), row.now())); slot += span;
            }
            rooms.add(new RoomRow(timeline.columns().get(column).room(), List.copyOf(blocks)));
        }
        return new HorizontalTimeline(List.copyOf(hours), List.copyOf(rooms), timeline.rows().size());
    }
}

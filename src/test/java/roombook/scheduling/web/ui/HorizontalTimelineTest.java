package roombook.scheduling.web.ui;

import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import roombook.kernel.*;
import roombook.scheduling.application.*;
import roombook.scheduling.domain.BookingRules;
import static org.junit.jupiter.api.Assertions.*;

@Tag("TS-11") @Tag("UI-12")
class HorizontalTimelineTest {
    @Test void transposePreservesEverySlotOwnershipAndExactLegacyInterval() {
        var date=LocalDate.of(2026,11,6);
        var view=new OccupancyView(date,List.of(new OccupancyRoom(new RoomView(1,"Альфа",1,6,"Екран",true),List.of(
            new OccupancyBooking(10,date.atTime(7,10),date.atTime(8,5),"Ранкова",new UserRef(4,"Автор")),
            new OccupancyBooking(11,date.atTime(14,0),date.atTime(15,0),"Моя",new UserRef(7,"Я"))))));
        var now=new OfficeClock.Snapshot(Instant.parse("2026-11-06T09:15:00Z"),date.atTime(11,15),date);
        var source=RoomTimeline.build(view,now,BookingRules.defaultRules(),new Actor(7,Role.EMPLOYEE));
        var horizontal=HorizontalTimeline.from(source);
        assertEquals(source.rows().size(),horizontal.slots());
        assertEquals(horizontal.slots(),horizontal.hours().stream().mapToInt(HorizontalTimeline.Hour::span).sum());
        var blocks=horizontal.rooms().getFirst().blocks();
        assertEquals(horizontal.slots(),blocks.stream().mapToInt(HorizontalTimeline.Block::span).sum());
        assertEquals(2,blocks.stream().filter(b->!b.cell().free()).count());
        assertEquals("07:10–08:05",blocks.getFirst().cell().interval());
        assertTrue(blocks.stream().anyMatch(b->b.cell().bookingId()==11&&b.cell().mine()&&b.span()==4));
        assertEquals(1,blocks.stream().filter(HorizontalTimeline.Block::now).count());
        assertTrue(blocks.stream().filter(HorizontalTimeline.Block::past).noneMatch(b->b.cell().link()));
        assertTrue(blocks.size()<horizontal.slots(),"Adjacent empty cells should be compacted");
    }
}

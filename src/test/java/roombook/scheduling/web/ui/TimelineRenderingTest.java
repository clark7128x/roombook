package roombook.scheduling.web.ui;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import roombook.kernel.*;
import roombook.scheduling.application.*;
import roombook.scheduling.domain.BookingRules;
import roombook.testsupport.HttpTestSupport;
import static org.junit.jupiter.api.Assertions.*;

@Import(TimelineRenderingTest.Routes.class)
class TimelineRenderingTest extends HttpTestSupport {
    @TestConfiguration static class Routes { @Bean TimelineProbe timelineProbe(OfficeClock clock, BookingRules rules) { return new TimelineProbe(clock, rules); } }
    @Controller static class TimelineProbe {
        private final OfficeClock clock; private final BookingRules rules;
        TimelineProbe(OfficeClock clock, BookingRules rules) { this.clock = clock; this.rules = rules; }
        @GetMapping("/timeline-test") String timeline(Actor actor, @RequestParam(defaultValue = "20") int count,
                @RequestParam(defaultValue = "false") boolean horizontal, Model model) {
            var date = clock.today(); var rooms = new ArrayList<OccupancyRoom>();
            for (int i = 0; i < count; i++) rooms.add(new OccupancyRoom(new RoomView(i + 1, "Кімната " + (i + 1), i / 10 + 1, 8, "Екран", true),
                    List.of(new OccupancyBooking(i + 1, date.atTime(10, 0), date.atTime(11, 0), "<script>unsafe</script>", new UserRef(actor.userId(), "Автор")))));
            var timeline = RoomTimeline.build(new OccupancyView(date, rooms), clock.snapshot(), rules, actor);
            model.addAttribute("timeline", timeline);
            model.addAttribute("horizontal", HorizontalTimeline.from(timeline)); model.addAttribute("date", date);
            model.addAttribute("single", false);
            return horizontal ? "components/horizontal-timeline :: timeline" : "components/timeline :: timeline";
        }
    }
    @Test @Tag("TS-11") @Tag("R-17") @Tag("UI-12")
    void horizontalFragmentEscapesAndCompactsTwentyAndFiftyRooms() throws Exception {
        var measurements = new ArrayList<String>();
        for (int count : new int[]{20, 50}) {
            var response = api("GET", "/timeline-test?horizontal=true&count=" + count, employee, "");
            assertEquals(200, response.statusCode(), response.body());
            assertTrue(response.body().contains("horizontal-table"));
            assertTrue(response.body().contains("colspan=\"4\""));
            assertTrue(response.body().contains("&lt;script&gt;unsafe&lt;/script&gt;"));
            assertFalse(response.body().contains("<script>unsafe"));
            assertTrue(response.body().contains("Ваше бронювання"));
            int bytes = response.body().getBytes(StandardCharsets.UTF_8).length;
            assertTrue(bytes < 300_000, "Horizontal fragment exceeds R-17 limit: " + bytes);
            measurements.add(count + " rooms: " + bytes + " bytes");
        }
        Files.write(Path.of("target/horizontal-size-" + ZoneId.systemDefault().getId().replace('/', '-') + ".txt"), measurements);
    }
    @Test @Tag("TS-11") @Tag("R-17") @Tag("UI-05")
    void fragmentEscapesTextAndRecordsHtmlSizeForTwentyAndFiftyRooms() throws Exception {
        var measurements = new ArrayList<String>();
        for (int count : new int[]{20, 50}) {
            var response = api("GET", "/timeline-test?count=" + count, employee, "");
            assertEquals(200, response.statusCode(), response.body());
            assertTrue(response.body().contains("<caption>Зайнятість кімнат на 27.10.2026</caption>"));
            assertTrue(response.body().contains("scope=\"colgroup\""));
            assertTrue(response.body().contains("rowspan=\"4\""));
            assertTrue(response.body().contains("tl-len-4")); assertTrue(response.body().contains("tl-mine"));
            assertTrue(response.body().contains("Ваше бронювання"));
            assertTrue(response.body().contains("&lt;script&gt;unsafe&lt;/script&gt;"));
            assertFalse(response.body().contains("<script>unsafe"));
            int bytes = response.body().getBytes(StandardCharsets.UTF_8).length;
            assertTrue(bytes <= 300_000, "R-17: timeline fragment exceeds 300 KB: " + bytes);
            assertFalse(response.body().contains("tl-slot-link"));
            assertTrue(response.body().contains("Забронювати"));
            measurements.add(count + " rooms: " + bytes + " bytes (uncompressed UTF-8 fragment)");
        }
        Files.write(Path.of("target/timeline-size-" + ZoneId.systemDefault().getId().replace('/', '-') + ".txt"), measurements);
    }
}

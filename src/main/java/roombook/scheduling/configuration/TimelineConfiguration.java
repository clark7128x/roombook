package roombook.scheduling.configuration;
import java.time.Duration;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.*;
import roombook.scheduling.domain.BookingRules;
@Configuration
public class TimelineConfiguration {
    @Bean SmartInitializingSingleton validateTimelineSlotCount(BookingRules rules) {
        return () -> {
            long slots = Duration.between(rules.workDayStart(), rules.workDayEnd()).toMinutes() / rules.slotGrid().toMinutes();
            if (slots > 48) throw new IllegalStateException("roombook.business.work-day-start, work-day-end and slot-grid must produce at most 48 slots; got " + slots);
        };
    }
}

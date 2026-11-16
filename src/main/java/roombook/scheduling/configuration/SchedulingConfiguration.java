package roombook.scheduling.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import roombook.scheduling.domain.BookingRules;
import roombook.scheduling.domain.BookingTimePolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.convert.DurationStyle;
import java.time.LocalTime;

/**
 * Конфігурація бінів планування (BookingRules, BookingTimePolicy).
 */
@Configuration
public class SchedulingConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public BookingRules bookingRules(
            @Value("${roombook.business.work-day-start:08:00}") String start,
            @Value("${roombook.business.work-day-end:20:00}") String end,
            @Value("${roombook.business.slot-grid:15m}") String grid,
            @Value("${roombook.business.min-duration:15m}") String minimum,
            @Value("${roombook.business.max-duration:4h}") String maximum,
            @Value("${roombook.business.horizon-days:90}") int horizon) {
        try {
            return new BookingRules(time(start, "work-day-start"), time(end, "work-day-end"), duration(grid, "slot-grid"),
                    duration(minimum, "min-duration"), duration(maximum, "max-duration"), horizon);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid roombook.business configuration: work-day-start, work-day-end, slot-grid, min-duration, max-duration, horizon-days. " + exception.getMessage(), exception);
        }
    }

    private static LocalTime time(String value, String property) {
        try { return LocalTime.parse(value); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Invalid roombook.business." + property); }
    }
    private static java.time.Duration duration(String value, String property) {
        try {
            var duration = DurationStyle.detectAndParse(value);
            if (duration.getNano() != 0 || duration.toSeconds() % 60 != 0) throw new IllegalArgumentException();
            return duration;
        } catch (RuntimeException exception) { throw new IllegalArgumentException("Invalid roombook.business." + property + ": expected whole minutes"); }
    }

    @Bean
    @ConditionalOnMissingBean
    public BookingTimePolicy bookingTimePolicy(BookingRules bookingRules) {
        return new BookingTimePolicy(bookingRules);
    }
}

package roombook.platform;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import roombook.kernel.OfficeClock;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Конфігурація системного та офісного годинників (D-19, TR-03, AR-08, OPS-09).
 */
@Configuration
public class ClockConfiguration {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public OfficeClock officeClock(Clock clock,
                                   @Value("${roombook.business.office-zone:}") String officeZone) {
        if (officeZone == null || officeZone.isBlank()) {
            throw new IllegalStateException("Missing required configuration property 'roombook.business.office-zone'");
        }
        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(officeZone.trim());
        } catch (Exception e) {
            throw new IllegalStateException("Invalid configuration property 'roombook.business.office-zone': " + officeZone, e);
        }
        return new OfficeClock(clock, zoneId);
    }
}

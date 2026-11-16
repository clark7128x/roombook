package roombook.kernel;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Єдине джерело системного часу в застосунку за правилом TR-03 та AR-08.
 * Поза цим класом пряме читання системного часу (now(), System.currentTimeMillis()) заборонено.
 */
public final class OfficeClock {

    /**
     * Незмінний знімок поточного моменту часу в офісній часовій зоні (TR-03, T-04).
     */
    public record Snapshot(Instant instant, LocalDateTime localNow, LocalDate today) {
        public Snapshot {
            Objects.requireNonNull(instant, "instant must not be null");
            Objects.requireNonNull(localNow, "localNow must not be null");
            Objects.requireNonNull(today, "today must not be null");
        }

        public LocalTime localTime() {
            return localNow.toLocalTime();
        }
    }

    private final Clock clock;
    private final ZoneId officeZone;

    public OfficeClock(Clock clock, ZoneId officeZone) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.officeZone = Objects.requireNonNull(officeZone, "officeZone must not be null");
    }

    public Instant instant() {
        return clock.instant();
    }

    public LocalDateTime localNow() {
        return LocalDateTime.ofInstant(clock.instant(), officeZone);
    }

    public LocalDate today() {
        return localNow().toLocalDate();
    }

    /**
     * Повертає знімок моменту часу (Instant, місцевий час, сьогодні).
     * Операція бере один знімок і передає його далі (T-04, TR-03).
     */
    public Snapshot snapshot() {
        Instant now = clock.instant();
        LocalDateTime localNow = LocalDateTime.ofInstant(now, officeZone);
        return new Snapshot(now, localNow, localNow.toLocalDate());
    }

    public ZoneId getOfficeZone() {
        return officeZone;
    }

    public Clock getClock() {
        return clock;
    }
}

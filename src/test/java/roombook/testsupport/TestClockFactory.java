package roombook.testsupport;

import roombook.kernel.OfficeClock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Фабрика керованого Clock та OfficeClock для тестів.
 */
public final class TestClockFactory {

    public static final ZoneId DEFAULT_OFFICE_ZONE = ZoneId.of("Europe/Kyiv");

    private TestClockFactory() {
    }

    public static MutableClock fixed(Instant instant, ZoneId zone) {
        return new MutableClock(instant, zone);
    }

    public static MutableClock fixed(Instant instant) {
        return fixed(instant, DEFAULT_OFFICE_ZONE);
    }

    public static OfficeClock createOfficeClock(MutableClock mutableClock) {
        return new OfficeClock(mutableClock, mutableClock.getZone());
    }

    public static OfficeClock createOfficeClock(Instant instant, ZoneId zone) {
        return new OfficeClock(fixed(instant, zone), zone);
    }

    public static OfficeClock createOfficeClock(Instant instant) {
        return createOfficeClock(instant, DEFAULT_OFFICE_ZONE);
    }

    /**
     * Керований Clock, який дозволяє змінювати та пересувати тестовий час.
     */
    public static class MutableClock extends Clock {
        private final AtomicReference<Instant> currentInstant;
        private final ZoneId zone;

        public MutableClock(Instant initialInstant, ZoneId zone) {
            this.currentInstant = new AtomicReference<>(Objects.requireNonNull(initialInstant, "initialInstant must not be null"));
            this.zone = Objects.requireNonNull(zone, "zone must not be null");
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(currentInstant.get(), zone);
        }

        @Override
        public Instant instant() {
            return currentInstant.get();
        }

        public void setInstant(Instant instant) {
            this.currentInstant.set(Objects.requireNonNull(instant, "instant must not be null"));
        }

        public void advanceBy(Duration duration) {
            this.currentInstant.updateAndGet(i -> i.plus(duration));
        }
    }
}

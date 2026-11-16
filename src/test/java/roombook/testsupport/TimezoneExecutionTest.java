package roombook.testsupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Перевірка зони JVM (T-03, Критерій 3, TR-01):
 * Друк поточної системної зони JVM для підтвердження прогону в різних зонах (UTC та America/Los_Angeles).
 */
class TimezoneExecutionTest {

    @Test
    @Tag("TR-01")
    @DisplayName("JVM Timezone Output (TR-01)")
    void printJvmTimezone() {
        ZoneId currentZone = ZoneId.systemDefault();
        System.out.println("=================================================");
        System.out.println("[TIMEZONE] Active JVM default timezone: " + currentZone);
        System.out.println("=================================================");
        assertNotNull(currentZone);
    }
}

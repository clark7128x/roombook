package roombook.kernel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("TS-05")
@Tag("TR-01")
@Tag("TR-02")
@Tag("TR-03")
@Tag("TR-04")
@Tag("TR-06")
class OfficeClockTest {

    private static final ZoneId KYIV_ZONE = ZoneId.of("Europe/Kyiv");

    @Test
    @DisplayName("TS-05 Випадок 1: 25 жовтня 2026, 00:30 UTC і 01:30 UTC (обидва мають місцевий час 03:30)")
    void case1_autumnDstTransitionTwoInstantsSameLocalTime() {
        // Осінній перехід на зимовий час: о 04:00 стрілки переводять на 03:00 назад.
        // Момент 1: 00:30 UTC = 03:30 EEST (літній час, UTC+3)
        Instant instantSummer = Instant.parse("2026-10-25T00:30:00Z");
        Clock clockSummer = Clock.fixed(instantSummer, ZoneId.of("UTC"));
        OfficeClock officeClockSummer = new OfficeClock(clockSummer, KYIV_ZONE);

        // Момент 2: 01:30 UTC = 03:30 EET (зимовий час, UTC+2)
        Instant instantWinter = Instant.parse("2026-10-25T01:30:00Z");
        Clock clockWinter = Clock.fixed(instantWinter, ZoneId.of("UTC"));
        OfficeClock officeClockWinter = new OfficeClock(clockWinter, KYIV_ZONE);

        // Перевірка моменту 1 (до переведення годинника, UTC+3)
        LocalDate today1 = officeClockSummer.today();
        LocalDateTime localNow1 = officeClockSummer.localNow();
        OfficeClock.Snapshot snapshot1 = officeClockSummer.snapshot();

        assertEquals(LocalDate.of(2026, 10, 25), today1, "Дата в Києві має бути 25 жовтня 2026");
        assertEquals(LocalDateTime.of(2026, 10, 25, 3, 30, 0), localNow1);
        assertEquals(instantSummer, snapshot1.instant());
        assertEquals(today1, snapshot1.today());
        assertEquals(localNow1, snapshot1.localNow());
        assertEquals(LocalTime.of(3, 30, 0), snapshot1.localTime());

        // Перевірка моменту 2 (після переведення годинника назад, UTC+2)
        LocalDate today2 = officeClockWinter.today();
        LocalDateTime localNow2 = officeClockWinter.localNow();
        OfficeClock.Snapshot snapshot2 = officeClockWinter.snapshot();

        assertEquals(LocalDate.of(2026, 10, 25), today2, "Дата в Києві має бути 25 жовтня 2026");
        assertEquals(LocalDateTime.of(2026, 10, 25, 3, 30, 0), localNow2);
        assertEquals(instantWinter, snapshot2.instant());
        assertEquals(today2, snapshot2.today());
        assertEquals(localNow2, snapshot2.localNow());
        assertEquals(LocalTime.of(3, 30, 0), snapshot2.localTime());

        // Місцевий час і дата однакові в обох моментах
        assertEquals(localNow1, localNow2);
        assertEquals(today1, today2);
    }

    @Test
    @DisplayName("TS-05 Випадок 2: 01:00 за Києвом у зимовий час (23:00 UTC попередньої дати)")
    void case2_winterTimeNightTransitionMatchesKyivDateNotUtc() {
        // 23:00 UTC 10 листопада 2026 року
        Instant utcInstant = Instant.parse("2026-11-10T23:00:00Z");
        Clock fixedClock = Clock.fixed(utcInstant, ZoneId.of("UTC"));
        OfficeClock officeClock = new OfficeClock(fixedClock, KYIV_ZONE);

        // В UTC дата — 2026-11-10, але в Києві (UTC+2) це вже 2026-11-11 01:00:00
        LocalDate todayInKyiv = officeClock.today();
        LocalDateTime localNowInKyiv = officeClock.localNow();
        OfficeClock.Snapshot snapshot = officeClock.snapshot();

        assertEquals(LocalDate.of(2026, 11, 11), todayInKyiv, "«Сьогодні» має дорівнювати даті за Києвом (2026-11-11)");
        assertEquals(LocalDateTime.of(2026, 11, 11, 1, 0, 0), localNowInKyiv);
        assertEquals(todayInKyiv, snapshot.today());
        assertEquals(localNowInKyiv, snapshot.localNow());

        // Перевірка горизонту 90 днів від дати в Києві (TR-04)
        LocalDate day90 = todayInKyiv.plusDays(90);
        LocalDate day91 = todayInKyiv.plusDays(91);

        assertEquals(LocalDate.of(2027, 2, 9), day90, "День 90 від 2026-11-11 має бути 2027-02-09");
        assertEquals(LocalDate.of(2027, 2, 10), day91, "День 91 від 2026-11-11 має бути 2027-02-10");
    }

    @Test
    @DisplayName("TR-03: OfficeClock зберігає передану офісну зону та Clock")
    void gettersReturnProvidedClockAndZone() {
        Instant now = Instant.parse("2026-10-29T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneId.of("UTC"));
        OfficeClock officeClock = new OfficeClock(clock, KYIV_ZONE);

        assertEquals(KYIV_ZONE, officeClock.getOfficeZone());
        assertEquals(clock, officeClock.getClock());
        assertEquals(now, officeClock.instant());
        assertNotNull(officeClock.snapshot());
    }
}

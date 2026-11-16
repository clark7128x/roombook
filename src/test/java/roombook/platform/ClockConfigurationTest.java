package roombook.platform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import roombook.kernel.OfficeClock;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("OPS-09")
@Tag("TR-03")
@Tag("AR-08")
class ClockConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ClockConfiguration.class);

    @Test
    @DisplayName("TR-03, AR-08: Коректна зона в конфігурації успішно створює біни Clock та OfficeClock")
    void validOfficeZoneCreatesBeans() {
        runner.withPropertyValues("roombook.business.office-zone=Europe/Kyiv")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(Clock.class);
                    assertThat(context).hasSingleBean(OfficeClock.class);

                    Clock clock = context.getBean(Clock.class);
                    assertEquals(ZoneOffset.UTC, clock.getZone(), "Системний годинник має бути в UTC");

                    OfficeClock officeClock = context.getBean(OfficeClock.class);
                    assertEquals(ZoneId.of("Europe/Kyiv"), officeClock.getOfficeZone(), "Офісна зона має бути Europe/Kyiv");
                    assertNotNull(officeClock.today());
                    assertNotNull(officeClock.localNow());
                });
    }

    @Test
    @DisplayName("OPS-09: Невірна зона в конфігурації завершує старт помилкою з назвою параметра roombook.business.office-zone")
    void invalidOfficeZoneFailsStartupWithParameterName() {
        runner.withPropertyValues("roombook.business.office-zone=Invalid/Unknown_Zone")
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable failure = context.getStartupFailure();
                    assertThat(failure).isNotNull();

                    // Перевіряємо, що в ланцюгу повідомлень про помилку є точна назва параметра
                    String fullErrorChain = getFullExceptionMessage(failure);
                    assertThat(fullErrorChain)
                            .contains("roombook.business.office-zone")
                            .contains("Invalid configuration property");
                });
    }

    @Test
    @DisplayName("OPS-09: Відсутня або порожня зона завершує старт помилкою з назвою параметра")
    void emptyOfficeZoneFailsStartupWithParameterName() {
        runner.withPropertyValues("roombook.business.office-zone=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable failure = context.getStartupFailure();
                    assertThat(failure).isNotNull();

                    String fullErrorChain = getFullExceptionMessage(failure);
                    assertThat(fullErrorChain)
                            .contains("roombook.business.office-zone")
                            .contains("Missing required configuration property");
                });
    }

    private String getFullExceptionMessage(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = throwable;
        while (cur != null) {
            sb.append(cur.getMessage()).append(" ");
            cur = cur.getCause();
        }
        return sb.toString();
    }
}

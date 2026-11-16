package roombook.platform;

import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import roombook.scheduling.configuration.SchedulingConfiguration;
import static org.junit.jupiter.api.Assertions.*;

class ProductionConfigurationTest {
    private final String[] environment = {"ROOMBOOK_DB_URL=jdbc:postgresql://db/roombook", "ROOMBOOK_DB_USERNAME=roombook_app",
            "ROOMBOOK_DB_PASSWORD=test-secret", "ROOMBOOK_FLYWAY_USERNAME=roombook_owner", "ROOMBOOK_FLYWAY_PASSWORD=test-secret"};
    @Test @Tag("OPS-09") @Tag("SEC-16") @Tag("TS-20")
    void everyRequiredSecretFailsFastByNameAndManagementMustBeSeparate() {
        for (int missing = 0; missing < environment.length; missing++) {
            int index = missing;
            String[] partial = java.util.stream.IntStream.range(0, environment.length).filter(i -> i != index).mapToObj(i -> environment[i]).toArray(String[]::new);
            new ApplicationContextRunner().withUserConfiguration(ProductionConfiguration.class).withPropertyValues(partial)
                    .run(context -> { assertNotNull(context.getStartupFailure()); assertTrue(context.getStartupFailure().toString().contains(environment[index].split("=")[0])); });
        }
        for (String property : new String[]{"management.server.port=8080", "server.servlet.session.cookie.secure=false"})
            new ApplicationContextRunner().withUserConfiguration(ProductionConfiguration.class).withPropertyValues(environment).withPropertyValues(property)
                    .run(context -> assertNotNull(context.getStartupFailure()));
        new ApplicationContextRunner().withUserConfiguration(ProductionConfiguration.class).withPropertyValues(environment)
                .run(context -> assertNull(context.getStartupFailure()));
    }
    @Test @Tag("OPS-09") @Tag("TS-20")
    void malformedBusinessPropertiesAndReversedWorkingHoursNameTheirConfiguration() {
        for (String property : new String[]{"work-day-start=bad", "work-day-end=07:00", "slot-grid=bad", "min-duration=15s", "max-duration=bad", "horizon-days=-1"})
            new ApplicationContextRunner().withUserConfiguration(SchedulingConfiguration.class)
                    .withPropertyValues("roombook.business." + property).run(context -> {
                        assertNotNull(context.getStartupFailure());
                        assertTrue(context.getStartupFailure().toString().contains("roombook.business"), context.getStartupFailure().toString());
                    });
    }
}

package roombook.accounts.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import roombook.accounts.application.AdminBootstrap;

/**
 * Запуск AdminBootstrap під час старту застосунку після міграцій БД (D-22, етап 6).
 */
@Component
public class BootstrapRunner implements ApplicationRunner {

    private final AdminBootstrap adminBootstrap;
    private final String adminUsername;
    private final String adminPassword;
    private final org.flywaydb.core.Flyway flyway;
    private final String version;

    public BootstrapRunner(
            AdminBootstrap adminBootstrap,
            @Value("${roombook.bootstrap.admin-username:}") String adminUsername,
            @Value("${roombook.bootstrap.admin-password:}") String adminPassword,
            org.flywaydb.core.Flyway flyway,
            @Value("${roombook.version:development}") String version
    ) {
        this.adminBootstrap = adminBootstrap;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.flyway = flyway;
        this.version = version;
    }

    @Override
    public void run(ApplicationArguments args) {
        var result = adminBootstrap.bootstrap(adminUsername, adminPassword);
        org.slf4j.LoggerFactory.getLogger(BootstrapRunner.class).atInfo()
                .addKeyValue("event", "START").addKeyValue("version", version)
                .addKeyValue("schemaVersion", flyway.info().current().getVersion().toString())
                .addKeyValue("bootstrap", result.name()).log("Startup initialization completed");
    }
}

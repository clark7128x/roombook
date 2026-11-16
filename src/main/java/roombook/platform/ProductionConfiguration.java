package roombook.platform;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** Fail before opening pools when production secrets or network isolation are missing. */
@Configuration
@Profile("!test & !dev")
public class ProductionConfiguration {
    @Bean static BeanFactoryPostProcessor productionConfigurationValidation(Environment environment) {
        return factory -> {
            for (String key : new String[]{"ROOMBOOK_DB_URL", "ROOMBOOK_DB_USERNAME", "ROOMBOOK_DB_PASSWORD",
                    "ROOMBOOK_FLYWAY_USERNAME", "ROOMBOOK_FLYWAY_PASSWORD"}) {
                String value = environment.getProperty(key);
                if (value == null || value.isBlank()) throw new IllegalStateException("Missing required environment variable " + key);
            }
            if (environment.getProperty("server.port", Integer.class, 8080)
                    .equals(environment.getProperty("management.server.port", Integer.class, 8081)))
                throw new IllegalStateException("management.server.port must differ from server.port");
            if (!environment.getProperty("server.servlet.session.cookie.secure", Boolean.class, true))
                throw new IllegalStateException("server.servlet.session.cookie.secure must be true in production");
        };
    }
}

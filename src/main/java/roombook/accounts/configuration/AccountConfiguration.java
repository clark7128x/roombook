package roombook.accounts.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

/**
 * Конфігурація модуля accounts (D-15).
 */
@Configuration
public class AccountConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder(@Value("${roombook.security.password-hash-cost:12}") int cost) {
        String idForEncode = "bcrypt";
        Map<String, PasswordEncoder> encoders = Map.of(
                idForEncode, new BCryptPasswordEncoder(cost)
        );
        return new DelegatingPasswordEncoder(idForEncode, encoders);
    }
}

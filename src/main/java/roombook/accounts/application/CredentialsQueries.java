package roombook.accounts.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roombook.accounts.persistence.AppUserRepository;
import roombook.kernel.ComparisonKey;

import java.util.Objects;
import java.util.Optional;

/**
 * Читання облікових даних користувачів для автентифікації та сесій (D-16, D-17, SEC-01, AR-07).
 */
@Service
@Transactional(readOnly = true)
public class CredentialsQueries {

    private final AppUserRepository appUserRepository;

    public CredentialsQueries(AppUserRepository appUserRepository) {
        this.appUserRepository = Objects.requireNonNull(appUserRepository, "appUserRepository must not be null");
    }

    /**
     * Завантаження облікових даних для перевірки пароля під час входу (SEC-01).
     * Пошук здійснюється без урахування регістру за нормалізованим ключем (D-05, A-15).
     */
    public Optional<LoginCredentials> loadForLogin(String rawUsername) {
        if (rawUsername == null || rawUsername.isBlank()) {
            return Optional.empty();
        }
        String key = ComparisonKey.calculate(rawUsername);
        return appUserRepository.findByUsernameKey(key)
                .map(u -> new LoginCredentials(u.getId(), u.getUsername(), u.getPasswordHash(), u.getRole(), u.isEnabled()));
    }

    /**
     * Завантаження актуальних даних користувача для поточної сесії (D-16).
     */
    public Optional<CurrentUser> loadForSession(long userId) {
        return appUserRepository.findById(userId)
                .map(u -> new CurrentUser(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.isEnabled()));
    }
}

package roombook.accounts.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roombook.accounts.domain.AppUser;
import roombook.accounts.domain.PasswordPolicy;
import roombook.accounts.persistence.AppUserRepository;
import roombook.kernel.ComparisonKey;
import roombook.kernel.FieldError;
import roombook.kernel.Role;

import java.util.Objects;
import java.util.Optional;

/**
 * Ініціалізація першого адміністратора під час старту застосунку (D-22, A-19, SEC-04).
 * Конкуренції не має, тому протокол D-04 тут не застосовується (виняток із AR-11).
 */
@Service
@Transactional
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = Objects.requireNonNull(appUserRepository, "appUserRepository must not be null");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
    }

    /**
     * Виконує bootstrap першого адміністратора за правилом D-22:
     * - Активний ADMIN є: нічого не робить (ADMIN_EXISTS).
     * - Активного ADMIN немає, змінні задані: створює нового (ADMIN_CREATED) або підвищує наявного (USER_PROMOTED).
     * - Активного ADMIN немає, змінні порожні: кидає IllegalStateException.
     */
    public BootstrapResult bootstrap(String adminUsername, String adminPassword) {
        // 1. Якщо в БД уже є хоча б один активний ADMIN, bootstrap нічого не змінює
        if (appUserRepository.existsActiveAdmin()) {
            log.info("AdminBootstrap: active admin already exists in database");
            return BootstrapResult.ADMIN_EXISTS;
        }

        // 2. Якщо активного ADMIN немає, змінні обов'язкові
        if (adminUsername == null || adminUsername.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException("AdminBootstrap failed: no active admin exists and bootstrap credentials are not provided (ROOMBOOK_ADMIN_USERNAME, ROOMBOOK_ADMIN_PASSWORD)");
        }

        // 3. Валідація пароля за PasswordPolicy (SEC-04)
        Optional<FieldError> passwordError = PasswordPolicy.check(adminPassword, adminUsername);
        if (passwordError.isPresent()) {
            throw new IllegalStateException("AdminBootstrap failed: invalid bootstrap admin password: " + passwordError.get().rule());
        }

        String usernameKey = ComparisonKey.calculate(adminUsername);
        String passwordHash = passwordEncoder.encode(adminPassword);

        // 4. Якщо користувач із таким логіном уже є: підвищуємо, вмикаємо й ставимо пароль
        Optional<AppUser> existing = appUserRepository.findByUsernameKey(usernameKey);
        if (existing.isPresent()) {
            AppUser user = existing.get();
            user.changeRole(Role.ADMIN);
            user.enable();
            user.updatePasswordHash(passwordHash);
            appUserRepository.saveAndFlush(user);
            log.info("AdminBootstrap: existing user promoted to admin: username={}", adminUsername);
            return BootstrapResult.USER_PROMOTED;
        }

        // 5. Створюємо нового адміністратора
        AppUser newAdmin = new AppUser(adminUsername.strip(), adminUsername.strip(), passwordHash, Role.ADMIN);
        appUserRepository.saveAndFlush(newAdmin);
        log.info("AdminBootstrap: new admin created: username={}", adminUsername);
        return BootstrapResult.ADMIN_CREATED;
    }
}

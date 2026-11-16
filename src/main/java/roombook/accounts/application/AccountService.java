package roombook.accounts.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roombook.accounts.domain.AdminPolicy;
import roombook.accounts.domain.AppUser;
import roombook.accounts.domain.PasswordPolicy;
import roombook.accounts.persistence.AccountsConstraints;
import roombook.accounts.persistence.AppUserRepository;
import roombook.kernel.Actor;
import roombook.kernel.DomainException;
import roombook.kernel.FieldError;
import roombook.kernel.FieldRule;
import roombook.kernel.Reason;
import roombook.kernel.Role;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Сценарії керування обліковими записами за протоколами етапу 3 (D-04, D-10, INV-14, FR-23, A-17, AR-07, AR-09).
 */
@Service
@Transactional
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = Objects.requireNonNull(appUserRepository, "appUserRepository must not be null");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder must not be null");
    }

    /**
     * Створення нового облікового запису (FR-23, EP-15).
     * Доступ лише для ADMIN. Без блокування рядків (етап 3).
     */
    public UserView createUser(Actor actor, CreateUserCommand command) {
        Objects.requireNonNull(actor, "actor must not be null");
        actor.requireAdmin();

        if (command == null) {
            throw new DomainException(Reason.INVALID_INPUT);
        }

        // Перевірка політики пароля перед створенням (SEC-04)
        PasswordPolicy.validate(command.password(), command.username());

        String passwordHash = passwordEncoder.encode(command.password());
        AppUser user = new AppUser(command.username(), command.displayName(), passwordHash, command.role());

        try {
            AppUser saved = appUserRepository.saveAndFlush(user);
            log.atInfo().addKeyValue("event", "USER_CREATED").addKeyValue("targetUserId", saved.getId()).addKeyValue("actorId", actor.userId()).log("User created");
            return toView(saved);
        } catch (Throwable t) {
            throw AccountsConstraints.translate(t);
        }
    }

    /**
     * Зміна ролі користувача (FR-02, FR-23, EP-16).
     * Доступ лише для ADMIN. Блокує цільового користувача та всіх активних ADMIN (D-04).
     */
    public UserView changeUserRole(Actor actor, long targetUserId, ChangeUserRoleCommand command) {
        Objects.requireNonNull(actor, "actor must not be null");
        actor.requireAdmin();

        if (command == null || command.role() == null) {
            throw DomainException.invalidInput("role", FieldRule.REQUIRED);
        }

        // 1. Перше звернення до БД - блокуюче читання цільового користувача та активних ADMIN у порядку id
        List<AppUser> lockedUsers = appUserRepository.lockWithActiveAdmins(targetUserId);
        AppUser targetUser = lockedUsers.stream()
                .filter(u -> u.getId().equals(targetUserId))
                .findFirst()
                .orElseThrow(() -> new DomainException(Reason.USER_NOT_FOUND));

        // 2. Безпечний повтор (ідемпотентність A-17) - до перевірки політики
        if (targetUser.getRole() == command.role()) {
            return toView(targetUser);
        }

        // 3. Перевірка політики збереження щонайменше 1 активного ADMIN (INV-14)
        AdminPolicy.checkCanChangeRole(targetUser, command.role(), lockedUsers);

        // 4. Зміна ролі та збереження
        Role oldRole = targetUser.getRole();
        targetUser.changeRole(command.role());
        AppUser updated = appUserRepository.saveAndFlush(targetUser);
        log.atInfo().addKeyValue("event", "USER_ROLE_CHANGED").addKeyValue("targetUserId", updated.getId()).addKeyValue("actorId", actor.userId()).addKeyValue("oldRole", oldRole).addKeyValue("newRole", updated.getRole()).log("User role changed");
        return toView(updated);
    }

    /**
     * Деактивація користувача (FR-23, EP-17).
     * Доступ лише для ADMIN. Блокує цільового користувача та всіх активних ADMIN (D-04).
     */
    public UserView disableUser(Actor actor, long targetUserId) {
        Objects.requireNonNull(actor, "actor must not be null");
        actor.requireAdmin();

        // 1. Перше звернення до БД - блокуюче читання цільового користувача та активних ADMIN
        List<AppUser> lockedUsers = appUserRepository.lockWithActiveAdmins(targetUserId);
        AppUser targetUser = lockedUsers.stream()
                .filter(u -> u.getId().equals(targetUserId))
                .findFirst()
                .orElseThrow(() -> new DomainException(Reason.USER_NOT_FOUND));

        // 2. Безпечний повтор для вже вимкненого користувача (A-17) - до перевірки політики
        if (!targetUser.isEnabled()) {
            return toView(targetUser);
        }

        // 3. Перевірка політики збереження щонайменше 1 активного ADMIN (INV-14)
        AdminPolicy.checkCanDisable(targetUser, lockedUsers);

        // 4. Деактивація та збереження
        targetUser.disable();
        AppUser updated = appUserRepository.saveAndFlush(targetUser);
        log.atInfo().addKeyValue("event", "USER_DISABLED").addKeyValue("targetUserId", updated.getId()).addKeyValue("actorId", actor.userId()).log("User disabled");
        return toView(updated);
    }

    /**
     * Активація користувача (FR-23, EP-18).
     * Доступ лише для ADMIN. Блокує рядок цільового користувача (D-04).
     */
    public UserView enableUser(Actor actor, long targetUserId) {
        Objects.requireNonNull(actor, "actor must not be null");
        actor.requireAdmin();

        // 1. Перше звернення до БД - блокуюче читання рядка цільового користувача
        AppUser targetUser = appUserRepository.lockById(targetUserId)
                .orElseThrow(() -> new DomainException(Reason.USER_NOT_FOUND));

        // 2. Безпечний повтор для вже активного користувача (A-17)
        if (targetUser.isEnabled()) {
            return toView(targetUser);
        }

        // 3. Активація та збереження
        targetUser.enable();
        AppUser updated = appUserRepository.saveAndFlush(targetUser);
        log.atInfo().addKeyValue("event", "USER_ENABLED").addKeyValue("targetUserId", updated.getId()).addKeyValue("actorId", actor.userId()).log("User enabled");
        return toView(updated);
    }

    /**
     * Скидання пароля користувача (FR-23, EP-19).
     * Доступ лише для ADMIN. Блокує рядок цільового користувача (D-04).
     * Пароль не логується у журналах (SEC-03, OPS-04).
     */
    public UserView resetPassword(Actor actor, long targetUserId, ResetPasswordCommand command) {
        Objects.requireNonNull(actor, "actor must not be null");
        actor.requireAdmin();

        if (command == null || command.newPassword() == null) {
            throw DomainException.invalidInput("password", FieldRule.REQUIRED);
        }

        // 1. Перше звернення до БД - блокуюче читання рядка цільового користувача
        AppUser targetUser = appUserRepository.lockById(targetUserId)
                .orElseThrow(() -> new DomainException(Reason.USER_NOT_FOUND));

        // 2. Валідація нового пароля за PasswordPolicy (SEC-04)
        PasswordPolicy.validate(command.newPassword(), targetUser.getUsername());

        // 3. Хешування та оновлення
        String passwordHash = passwordEncoder.encode(command.newPassword());
        targetUser.updatePasswordHash(passwordHash);
        AppUser updated = appUserRepository.saveAndFlush(targetUser);
        log.atInfo().addKeyValue("event", "USER_PASSWORD_RESET").addKeyValue("targetUserId", updated.getId()).addKeyValue("actorId", actor.userId()).log("User password reset");
        return toView(updated);
    }

    private UserView toView(AppUser u) {
        return new UserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.isEnabled());
    }
}

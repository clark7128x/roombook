package roombook.accounts.domain;

import roombook.kernel.DomainException;
import roombook.kernel.Reason;
import roombook.kernel.Role;

import java.util.Objects;

/**
 * Політика збереження щонайменше одного активного адміністратора в системі (INV-14, FR-23, A-17, A-19).
 * Перевіряє, чи не призведе деактивація або зниження ролі користувача до порушення інваріанта.
 */
public final class AdminPolicy {

    private AdminPolicy() {
    }

    /**
     * Перевірка допустимості деактивації користувача (INV-14, A-17).
     * Випадки:
     * 1. Вже вимкнений користувач: повторний виклик безпечний, LAST_ADMIN не кидається.
     * 2. Користувач EMPLOYEE: вимкнення завжди дозволене.
     * 3. Користувач активний ADMIN: якщо activeAdminCount > 1 — дозволено, якщо <= 1 — помилка LAST_ADMIN.
     */
    public static void checkCanDisable(AppUser targetUser, long activeAdminCount) {
        Objects.requireNonNull(targetUser, "targetUser must not be null");

        if (!targetUser.isEnabled()) {
            return;
        }

        if (targetUser.getRole() != Role.ADMIN) {
            return;
        }

        if (activeAdminCount <= 1) {
            throw new DomainException(Reason.LAST_ADMIN);
        }
    }

    public static void checkCanDisable(AppUser targetUser, Iterable<AppUser> users) {
        long count = 0;
        for (AppUser u : users) {
            if (u.getRole() == Role.ADMIN && u.isEnabled()) {
                count++;
            }
        }
        checkCanDisable(targetUser, count);
    }

    /**
     * Перевірка допустимості зміни ролі користувача (INV-14).
     * Зниження останнього активного адміністратора дає помилку LAST_ADMIN.
     */
    public static void checkCanChangeRole(AppUser targetUser, Role newRole, long activeAdminCount) {
        Objects.requireNonNull(targetUser, "targetUser must not be null");
        Objects.requireNonNull(newRole, "newRole must not be null");

        if (newRole == Role.ADMIN) {
            return;
        }

        if (targetUser.getRole() != Role.ADMIN) {
            return;
        }

        if (!targetUser.isEnabled()) {
            return;
        }

        if (activeAdminCount <= 1) {
            throw new DomainException(Reason.LAST_ADMIN);
        }
    }

    public static void checkCanChangeRole(AppUser targetUser, Role newRole, Iterable<AppUser> users) {
        long count = 0;
        for (AppUser u : users) {
            if (u.getRole() == Role.ADMIN && u.isEnabled()) {
                count++;
            }
        }
        checkCanChangeRole(targetUser, newRole, count);
    }
}

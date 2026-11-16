package roombook.accounts.application;

import roombook.kernel.Role;

/**
 * Команда зміни ролі користувача (FR-02, FR-23, EP-16).
 */
public record ChangeUserRoleCommand(Role role) {
}

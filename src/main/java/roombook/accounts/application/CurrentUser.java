package roombook.accounts.application;

import roombook.kernel.Role;

/**
 * Дані користувача поточної сесії (D-16).
 */
public record CurrentUser(
        long userId,
        String username,
        String displayName,
        Role role,
        boolean enabled
) {
}

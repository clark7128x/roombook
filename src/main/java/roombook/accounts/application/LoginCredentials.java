package roombook.accounts.application;

import roombook.kernel.Role;

/**
 * Облікові дані для автентифікації користувача (D-16, SEC-01).
 * toString() маскує passwordHash для безпеки у журналах.
 */
public record LoginCredentials(
        long userId,
        String username,
        String passwordHash,
        Role role,
        boolean enabled
) {
    @Override
    public String toString() {
        return "LoginCredentials[userId=" + userId + ", username=" + username + ", passwordHash=***, role=" + role + ", enabled=" + enabled + "]";
    }
}

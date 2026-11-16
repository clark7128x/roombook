package roombook.accounts.application;

import roombook.kernel.Role;

/**
 * Команда створення нового облікового запису користувача (FR-23, SEC-03).
 * toString() маскує пароль для безпеки у журналах.
 */
public record CreateUserCommand(
        String username,
        String displayName,
        String password,
        Role role
) {
    @Override
    public String toString() {
        return "CreateUserCommand[username=" + username + ", displayName=" + displayName + ", password=***, role=" + role + "]";
    }
}

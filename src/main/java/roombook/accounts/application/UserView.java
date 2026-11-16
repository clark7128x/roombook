package roombook.accounts.application;

import roombook.kernel.Role;

/**
 * Проєкція даних користувача для перегляду без паролів та хешів (SEC-03, EP-13, EP-14).
 */
public record UserView(
        long id,
        String username,
        String displayName,
        Role role,
        boolean enabled
) {
}

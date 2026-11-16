package roombook.accounts.application;

/**
 * Команда скидання пароля користувача адміністратором (FR-23, SEC-03, EP-19).
 * toString() маскує пароль для безпеки у журналах.
 */
public record ResetPasswordCommand(String newPassword) {
    @Override
    public String toString() {
        return "ResetPasswordCommand[newPassword=***]";
    }
}

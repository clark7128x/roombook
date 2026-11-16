package roombook.accounts.domain;

import roombook.kernel.ComparisonKey;
import roombook.kernel.DomainException;
import roombook.kernel.FieldError;
import roombook.kernel.FieldRule;
import roombook.kernel.Reason;
import roombook.kernel.TextRules;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Чиста функція перевірки політики паролів (SEC-03, A-42, TS-03).
 * Вимоги:
 * 1. Від 10 кодових точок Unicode (9 відхиляється, 10 валідно).
 * 2. Не більше 72 байтів у UTF-8 (72 валідно, 73 відхиляється, обмеження bcrypt).
 * 3. Не збігається з логіном за ключем порівняння ComparisonKey (без урахування регістру).
 * 4. Пароль не обрізається й не нормалізується (пробіли на краях зараховуються).
 * Порушення повертає FieldError з полем "password".
 */
public final class PasswordPolicy {

    public static final int MIN_CODE_POINTS = 10;
    public static final int MAX_UTF8_BYTES = 72;

    private PasswordPolicy() {
    }

    public static Optional<FieldError> check(String rawPassword, String username) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            return Optional.of(new FieldError("password", FieldRule.REQUIRED));
        }

        // 1. Мінімум 10 кодових точок
        if (TextRules.codePointLength(rawPassword) < MIN_CODE_POINTS) {
            return Optional.of(new FieldError("password", FieldRule.LENGTH));
        }

        // 2. Максимум 72 байти в кодуванні UTF-8
        byte[] utf8Bytes = rawPassword.getBytes(StandardCharsets.UTF_8);
        if (utf8Bytes.length > MAX_UTF8_BYTES) {
            return Optional.of(new FieldError("password", FieldRule.LENGTH));
        }

        // 3. Не збігається з логіном за ключем порівняння ComparisonKey
        if (username != null && !username.isBlank()) {
            String passKey = ComparisonKey.calculate(rawPassword);
            String userKey = ComparisonKey.calculate(username);
            if (passKey.equals(userKey)) {
                return Optional.of(new FieldError("password", FieldRule.FORMAT));
            }
        }

        return Optional.empty();
    }

    public static void validate(String rawPassword, String username) {
        Optional<FieldError> error = check(rawPassword, username);
        if (error.isPresent()) {
            throw new DomainException(Reason.INVALID_INPUT, List.of(error.get()));
        }
    }
}

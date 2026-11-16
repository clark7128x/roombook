package roombook.accounts.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import roombook.kernel.DomainException;
import roombook.kernel.FieldError;
import roombook.kernel.FieldRule;
import roombook.kernel.Reason;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тести політики паролів (T-10, TS-03, SEC-03, A-42).
 */
@Tag("TS-03")
@Tag("SEC-03")
@Tag("A-42")
class PasswordPolicyTest {

    @Test
    @DisplayName("TS-03: Граничні значення довжини в символах (9 символів — відхиляється, 10 — валідно)")
    void codePointLengthBoundaries() {
        // 9 символів -> відхиляється з LENGTH
        Optional<FieldError> err9 = PasswordPolicy.check("123456789", "john.doe");
        assertTrue(err9.isPresent());
        assertEquals("password", err9.get().field());
        assertEquals(FieldRule.LENGTH, err9.get().rule());

        // 10 символів -> валідно
        Optional<FieldError> ok10 = PasswordPolicy.check("1234567890", "john.doe");
        assertFalse(ok10.isPresent());
        assertDoesNotThrow(() -> PasswordPolicy.validate("1234567890", "john.doe"));
    }

    @Test
    @DisplayName("TS-03: Граничні значення довжини в байтах UTF-8 (72 байти — валідно, 73 байти — відхиляється)")
    void utf8BytesBoundaries() {
        // 72 ASCII-символи (72 байти) -> валідно
        String pass72Ascii = "A".repeat(72);
        assertDoesNotThrow(() -> PasswordPolicy.validate(pass72Ascii, "john.doe"));

        // 73 ASCII-символи (73 байти) -> відхиляється
        String pass73Ascii = "A".repeat(73);
        DomainException ex73 = assertThrows(DomainException.class, () ->
                PasswordPolicy.validate(pass73Ascii, "john.doe"));
        assertEquals(Reason.INVALID_INPUT, ex73.getReason());
        assertTrue(ex73.getFieldErrors().stream()
                .anyMatch(fe -> fe.field().equals("password") && fe.rule() == FieldRule.LENGTH));

        // 36 кириличних літер (36 * 2 = 72 байти в UTF-8) -> валідно
        String pass36Cyr = "Пароль".repeat(6); // 36 літер = 72 байти
        assertEquals(36, pass36Cyr.length());
        assertEquals(72, pass36Cyr.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertDoesNotThrow(() -> PasswordPolicy.validate(pass36Cyr, "john.doe"));

        // 37 кириличних літер (37 * 2 = 74 байти в UTF-8 > 72) -> відхиляється
        String pass37Cyr = "П".repeat(37); // 37 літер = 74 байти
        assertEquals(74, pass37Cyr.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        DomainException ex37Cyr = assertThrows(DomainException.class, () ->
                PasswordPolicy.validate(pass37Cyr, "john.doe"));
        assertEquals(Reason.INVALID_INPUT, ex37Cyr.getReason());
        assertTrue(ex37Cyr.getFieldErrors().stream()
                .anyMatch(fe -> fe.field().equals("password") && fe.rule() == FieldRule.LENGTH));
    }

    @Test
    @DisplayName("TS-03: Пароль, що збігається з логіном без урахування регістру, відхиляється")
    void passwordMatchingUsernameRejected() {
        // Точний збіг
        Optional<FieldError> errExact = PasswordPolicy.check("supersecret", "supersecret");
        assertTrue(errExact.isPresent());
        assertEquals(FieldRule.FORMAT, errExact.get().rule());

        // Збіг без урахування регістру
        Optional<FieldError> errCase = PasswordPolicy.check("SuperSecret", "supersecret");
        assertTrue(errCase.isPresent());
        assertEquals(FieldRule.FORMAT, errCase.get().rule());

        DomainException ex = assertThrows(DomainException.class, () ->
                PasswordPolicy.validate("SUPERSECRET", "supersecret"));
        assertEquals(Reason.INVALID_INPUT, ex.getReason());
    }

    @Test
    @DisplayName("TS-03: Пробіли на краях пароля зараховуються до довжини (не обрізаються)")
    void leadingAndTrailingWhitespacesPreserved() {
        // "  123456  " має довжину 10 символів (2 пробіли + 6 цифр + 2 пробіли)
        // Якщо б обрізалося, лишилося б 6 символів і впало б з LENGTH.
        // Оскільки пробіли не обрізаються, пароль має 10 символів і успішно проходить.
        String passWithSpaces = "  123456  ";
        assertEquals(10, passWithSpaces.length());
        assertDoesNotThrow(() -> PasswordPolicy.validate(passWithSpaces, "john.doe"));
    }
}

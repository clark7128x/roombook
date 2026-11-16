package roombook.accounts.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import roombook.kernel.DomainException;
import roombook.kernel.FieldError;
import roombook.kernel.FieldRule;
import roombook.kernel.Reason;
import roombook.kernel.Role;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тести сутності AppUser (T-10, TS-03, INV-13, FR-02, FR-23, SEC-03, SEC-04, A-14, A-15, A-17).
 */
@Tag("TS-03")
@Tag("INV-13")
@Tag("FR-02")
@Tag("FR-23")
@Tag("SEC-03")
@Tag("SEC-04")
@Tag("A-14")
@Tag("A-15")
@Tag("A-17")
class AppUserTest {

    private static final String VALID_HASH = "$2a$12$e8Y/p5xR1f9oU8kG.aC3buxK12345678901234567890123456789";

    @Test
    @DisplayName("Успішне створення користувача: enabled=true, username_key розраховано, displayName обрізано")
    void createUserSuccess() {
        AppUser user = new AppUser("john.doe", "  John Doe  ", VALID_HASH, Role.EMPLOYEE);

        assertEquals("john.doe", user.getUsername());
        assertEquals("john.doe", user.getUsernameKey());
        assertEquals("John Doe", user.getDisplayName());
        assertEquals(VALID_HASH, user.getPasswordHash());
        assertEquals(Role.EMPLOYEE, user.getRole());
        assertTrue(user.isEnabled());
    }

    @Test
    @DisplayName("TS-03: Граничні значення username (3 і 64 символи — валідно; 2 і 65 — відхиляються)")
    void usernameBoundaries() {
        // 3 символи — валідно
        AppUser u3 = new AppUser("usr", "User 3", VALID_HASH, Role.EMPLOYEE);
        assertEquals("usr", u3.getUsername());

        // 64 символи — валідно
        String u64Name = "a".repeat(64);
        AppUser u64 = new AppUser(u64Name, "User 64", VALID_HASH, Role.EMPLOYEE);
        assertEquals(64, u64.getUsername().length());

        // 2 символи — відхиляється з LENGTH
        DomainException ex2 = assertThrows(DomainException.class, () ->
                new AppUser("ab", "User 2", VALID_HASH, Role.EMPLOYEE));
        assertEquals(Reason.INVALID_INPUT, ex2.getReason());
        assertTrue(ex2.getFieldErrors().stream()
                .anyMatch(fe -> fe.field().equals("username") && fe.rule() == FieldRule.LENGTH));

        // 65 символів — відхиляється з LENGTH
        String u65Name = "a".repeat(65);
        DomainException ex65 = assertThrows(DomainException.class, () ->
                new AppUser(u65Name, "User 65", VALID_HASH, Role.EMPLOYEE));
        assertEquals(Reason.INVALID_INPUT, ex65.getReason());
        assertTrue(ex65.getFieldErrors().stream()
                .anyMatch(fe -> fe.field().equals("username") && fe.rule() == FieldRule.LENGTH));
    }

    @Test
    @DisplayName("TS-03: Заборонені символи в username (лише латиниця, цифри, крапка, підкреслення, дефіс)")
    void usernameIllegalCharacters() {
        // Дозволені символи (латиниця, цифри, крапка, підкреслення, дефіс)
        assertDoesNotThrow(() -> new AppUser("user.name_12-test", "Display", VALID_HASH, Role.EMPLOYEE));

        // Пробіл — заборонено
        DomainException exSpace = assertThrows(DomainException.class, () ->
                new AppUser("user name", "Display", VALID_HASH, Role.EMPLOYEE));
        assertTrue(exSpace.getFieldErrors().stream()
                .anyMatch(fe -> fe.field().equals("username") && fe.rule() == FieldRule.ILLEGAL_CHARACTERS));

        // Кирилиця — заборонено
        DomainException exCyr = assertThrows(DomainException.class, () ->
                new AppUser("юзер", "Display", VALID_HASH, Role.EMPLOYEE));
        assertTrue(exCyr.getFieldErrors().stream()
                .anyMatch(fe -> fe.field().equals("username") && fe.rule() == FieldRule.ILLEGAL_CHARACTERS));

        // Спецсимволи (@, !) — заборонено
        DomainException exSpecial = assertThrows(DomainException.class, () ->
                new AppUser("user@corp", "Display", VALID_HASH, Role.EMPLOYEE));
        assertTrue(exSpecial.getFieldErrors().stream()
                .anyMatch(fe -> fe.field().equals("username") && fe.rule() == FieldRule.ILLEGAL_CHARACTERS));
    }

    @Test
    @DisplayName("TS-03: Граничні значення displayName (1 і 100 символів — валідно; > 100 або порожнє — відхиляється)")
    void displayNameBoundaries() {
        // 1 символ — валідно
        AppUser u1 = new AppUser("user1", "A", VALID_HASH, Role.EMPLOYEE);
        assertEquals("A", u1.getDisplayName());

        // 100 символів — валідно
        String name100 = "Д".repeat(100);
        AppUser u100 = new AppUser("user100", name100, VALID_HASH, Role.EMPLOYEE);
        assertEquals(100, u100.getDisplayName().length());

        // Порожнє / пробіли — REQUIRED
        DomainException exBlank = assertThrows(DomainException.class, () ->
                new AppUser("userb", "   \t  ", VALID_HASH, Role.EMPLOYEE));
        assertTrue(exBlank.getFieldErrors().stream()
                .anyMatch(fe -> fe.field().equals("displayName") && fe.rule() == FieldRule.REQUIRED));

        // 101 символ — LENGTH
        String name101 = "Д".repeat(101);
        DomainException ex101 = assertThrows(DomainException.class, () ->
                new AppUser("user101", name101, VALID_HASH, Role.EMPLOYEE));
        assertTrue(ex101.getFieldErrors().stream()
                .anyMatch(fe -> fe.field().equals("displayName") && fe.rule() == FieldRule.LENGTH));

        // Керувальні символи — ILLEGAL_CHARACTERS
        DomainException exCtrl = assertThrows(DomainException.class, () ->
                new AppUser("userctrl", "Display\u0007Name", VALID_HASH, Role.EMPLOYEE));
        assertTrue(exCtrl.getFieldErrors().stream()
                .anyMatch(fe -> fe.field().equals("displayName") && fe.rule() == FieldRule.ILLEGAL_CHARACTERS));
    }

    @Test
    @DisplayName("Зміна ролі, активація, деактивація (A-17) та оновлення хешу пароля")
    void changeRoleEnableDisableUpdatePassword() {
        AppUser user = new AppUser("admin.user", "Admin", VALID_HASH, Role.ADMIN);
        assertTrue(user.isEnabled());

        // Зміна ролі
        user.changeRole(Role.EMPLOYEE);
        assertEquals(Role.EMPLOYEE, user.getRole());

        // Деактивація
        user.disable();
        assertFalse(user.isEnabled());

        // Повторна деактивація безпечна (A-17)
        user.disable();
        assertFalse(user.isEnabled());

        // Активація
        user.enable();
        assertTrue(user.isEnabled());

        // Оновлення пароля
        String newHash = "$2a$12$newHashStringWithBcryptFormatValue1234567890123456789012";
        user.updatePasswordHash(newHash);
        assertEquals(newHash, user.getPasswordHash());

        // Порожній новий хеш відхиляється
        assertThrows(DomainException.class, () -> user.updatePasswordHash(""));
    }

    @Test
    @DisplayName("SEC-04: toString() сутності не містить passwordHash")
    void toStringNeverContainsPasswordHash() {
        AppUser user = new AppUser("test.sec", "Test Sec", VALID_HASH, Role.EMPLOYEE);
        String str = user.toString();

        assertNotNull(str);
        assertFalse(str.contains(VALID_HASH), "toString не повинен містити хеш пароля");
        assertTrue(str.contains("test.sec"));
        assertTrue(str.contains("Test Sec"));
    }
}

package roombook.accounts.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import roombook.accounts.domain.AppUser;
import roombook.accounts.persistence.AppUserRepository;
import roombook.kernel.ComparisonKey;
import roombook.kernel.Role;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Тести AdminBootstrap (T-13, D-22, A-19, SEC-04).
 */
@Tag("D-22")
@Tag("A-19")
@Tag("SEC-04")
class AdminBootstrapTest {

    private AppUserRepository appUserRepository;
    private PasswordEncoder passwordEncoder;
    private AdminBootstrap adminBootstrap;

    private static final String ADMIN_USER = "system.admin";
    private static final String VALID_PASSWORD = "veryStrongPassword123";

    @BeforeEach
    void setUp() {
        appUserRepository = mock(AppUserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        adminBootstrap = new AdminBootstrap(appUserRepository, passwordEncoder);

        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "hash_" + inv.getArgument(0));
    }

    @Test
    @DisplayName("Сценарій 1 (D-22): Якщо в БД є активний ADMIN, bootstrap повертає ADMIN_EXISTS і не вносить змін")
    void activeAdminExistsNoChanges() {
        when(appUserRepository.existsActiveAdmin()).thenReturn(true);

        BootstrapResult result = adminBootstrap.bootstrap(ADMIN_USER, VALID_PASSWORD);

        assertEquals(BootstrapResult.ADMIN_EXISTS, result);
        verify(appUserRepository, never()).saveAndFlush(any());
        verify(appUserRepository, never()).findByUsernameKey(any());
    }

    @Test
    @DisplayName("Сценарій 2 (D-22): Активного ADMIN немає, користувача з таким логіном немає -> ADMIN_CREATED")
    void noActiveAdminAndUserDoesNotExistCreatesAdmin() {
        when(appUserRepository.existsActiveAdmin()).thenReturn(false);
        String usernameKey = ComparisonKey.calculate(ADMIN_USER);
        when(appUserRepository.findByUsernameKey(usernameKey)).thenReturn(Optional.empty());

        BootstrapResult result = adminBootstrap.bootstrap(ADMIN_USER, VALID_PASSWORD);

        assertEquals(BootstrapResult.ADMIN_CREATED, result);

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(appUserRepository).saveAndFlush(captor.capture());

        AppUser created = captor.getValue();
        assertEquals(ADMIN_USER, created.getUsername());
        assertEquals(ADMIN_USER, created.getDisplayName());
        assertEquals(Role.ADMIN, created.getRole());
        assertTrue(created.isEnabled());
        assertEquals("hash_" + VALID_PASSWORD, created.getPasswordHash());
    }

    @Test
    @DisplayName("Сценарій 3 (D-22): Активного ADMIN немає, користувач існує (EMPLOYEE/disabled) -> USER_PROMOTED")
    void noActiveAdminAndUserExistsPromotesUser() {
        when(appUserRepository.existsActiveAdmin()).thenReturn(false);
        String usernameKey = ComparisonKey.calculate(ADMIN_USER);

        AppUser existingUser = new AppUser(ADMIN_USER, "Existing Admin", "old_hash", Role.EMPLOYEE);
        existingUser.disable();
        ReflectionTestUtils.setField(existingUser, "id", 42L);

        when(appUserRepository.findByUsernameKey(usernameKey)).thenReturn(Optional.of(existingUser));

        BootstrapResult result = adminBootstrap.bootstrap(ADMIN_USER, VALID_PASSWORD);

        assertEquals(BootstrapResult.USER_PROMOTED, result);

        verify(appUserRepository).saveAndFlush(existingUser);
        assertEquals(Role.ADMIN, existingUser.getRole());
        assertTrue(existingUser.isEnabled());
        assertEquals("hash_" + VALID_PASSWORD, existingUser.getPasswordHash());
    }

    @Test
    @DisplayName("Сценарій 4 (D-22): Активного ADMIN немає, облікові дані не передано -> IllegalStateException")
    void noActiveAdminAndMissingCredentialsThrowsIllegalStateException() {
        when(appUserRepository.existsActiveAdmin()).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> adminBootstrap.bootstrap(null, null));
        assertThrows(IllegalStateException.class, () -> adminBootstrap.bootstrap("", "pass"));
        assertThrows(IllegalStateException.class, () -> adminBootstrap.bootstrap("admin", "   "));

        verify(appUserRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Сценарій 5 (SEC-04): Пароль bootstrap не відповідає PasswordPolicy -> IllegalStateException")
    void invalidPasswordThrowsIllegalStateException() {
        when(appUserRepository.existsActiveAdmin()).thenReturn(false);

        // Пароль коротший 10 символів
        assertThrows(IllegalStateException.class, () -> adminBootstrap.bootstrap(ADMIN_USER, "short"));

        // Пароль збігається з логіном
        assertThrows(IllegalStateException.class, () -> adminBootstrap.bootstrap(ADMIN_USER, ADMIN_USER));

        verify(appUserRepository, never()).saveAndFlush(any());
    }
}

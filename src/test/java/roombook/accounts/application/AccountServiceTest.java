package roombook.accounts.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import roombook.accounts.domain.AppUser;
import roombook.accounts.persistence.AppUserRepository;
import roombook.kernel.Actor;
import roombook.kernel.DomainException;
import roombook.kernel.FieldError;
import roombook.kernel.Reason;
import roombook.kernel.Role;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Модульні тести AccountService (T-13, TS-06, FR-23, D-04, D-10, INV-14, A-17, OPS-05).
 */
@Tag("TS-06")
@Tag("FR-23")
@Tag("D-04")
@Tag("D-10")
@Tag("INV-14")
@Tag("A-17")
@Tag("OPS-05")
class AccountServiceTest {

    private AppUserRepository appUserRepository;
    private PasswordEncoder passwordEncoder;
    private AccountService accountService;

    private final Actor adminActor = new Actor(1L, Role.ADMIN);
    private final Actor employeeActor = new Actor(2L, Role.EMPLOYEE);

    @BeforeEach
    void setUp() {
        appUserRepository = mock(AppUserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        accountService = new AccountService(appUserRepository, passwordEncoder);

        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "hashed_" + inv.getArgument(0));
    }

    private AppUser createUser(long id, String username, String displayName, Role role, boolean enabled) {
        AppUser user = new AppUser(username, displayName, "existing_hash", role);
        ReflectionTestUtils.setField(user, "id", id);
        if (!enabled) {
            user.disable();
        }
        return user;
    }

    // -------------------------------------------------------------------------
    // TS-06: requireAdmin перевіряється до першого звернення до БД
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createUser: доступний лише для ADMIN; для EMPLOYEE кидає NOT_PERMITTED до звернення до БД")
    void createUserRequiresAdmin() {
        CreateUserCommand cmd = new CreateUserCommand("new.user", "New User", "strongPass123", Role.EMPLOYEE);

        DomainException ex = assertThrows(DomainException.class, () -> accountService.createUser(employeeActor, cmd));
        assertEquals(Reason.NOT_PERMITTED, ex.getReason());
        verifyNoInteractions(appUserRepository);
    }

    @Test
    @DisplayName("changeUserRole: доступний лише для ADMIN; для EMPLOYEE кидає NOT_PERMITTED до звернення до БД")
    void changeUserRoleRequiresAdmin() {
        ChangeUserRoleCommand cmd = new ChangeUserRoleCommand(Role.ADMIN);

        DomainException ex = assertThrows(DomainException.class, () -> accountService.changeUserRole(employeeActor, 10L, cmd));
        assertEquals(Reason.NOT_PERMITTED, ex.getReason());
        verifyNoInteractions(appUserRepository);
    }

    @Test
    @DisplayName("disableUser: доступний лише для ADMIN; для EMPLOYEE кидає NOT_PERMITTED до звернення до БД")
    void disableUserRequiresAdmin() {
        DomainException ex = assertThrows(DomainException.class, () -> accountService.disableUser(employeeActor, 10L));
        assertEquals(Reason.NOT_PERMITTED, ex.getReason());
        verifyNoInteractions(appUserRepository);
    }

    @Test
    @DisplayName("enableUser: доступний лише для ADMIN; для EMPLOYEE кидає NOT_PERMITTED до звернення до БД")
    void enableUserRequiresAdmin() {
        DomainException ex = assertThrows(DomainException.class, () -> accountService.enableUser(employeeActor, 10L));
        assertEquals(Reason.NOT_PERMITTED, ex.getReason());
        verifyNoInteractions(appUserRepository);
    }

    @Test
    @DisplayName("resetPassword: доступний лише для ADMIN; для EMPLOYEE кидає NOT_PERMITTED до звернення до БД")
    void resetPasswordRequiresAdmin() {
        ResetPasswordCommand cmd = new ResetPasswordCommand("newStrongPass123");

        DomainException ex = assertThrows(DomainException.class, () -> accountService.resetPassword(employeeActor, 10L, cmd));
        assertEquals(Reason.NOT_PERMITTED, ex.getReason());
        verifyNoInteractions(appUserRepository);
    }

    // -------------------------------------------------------------------------
    // TS-06 / D-04: Перший виклик репозиторію є блокуючим (InOrder)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("changeUserRole: першим зверненням до БД є блокуючий lockWithActiveAdmins")
    void changeUserRoleFirstDbCallIsLock() {
        long targetId = 10L;
        AppUser target = createUser(targetId, "user10", "User Ten", Role.EMPLOYEE, true);
        AppUser admin1 = createUser(1L, "admin1", "Admin One", Role.ADMIN, true);

        when(appUserRepository.lockWithActiveAdmins(targetId)).thenReturn(List.of(admin1, target));
        when(appUserRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ChangeUserRoleCommand cmd = new ChangeUserRoleCommand(Role.ADMIN);
        accountService.changeUserRole(adminActor, targetId, cmd);

        InOrder inOrder = inOrder(appUserRepository);
        inOrder.verify(appUserRepository).lockWithActiveAdmins(targetId);
        inOrder.verify(appUserRepository).saveAndFlush(target);
    }

    @Test
    @DisplayName("disableUser: першим зверненням до БД є блокуючий lockWithActiveAdmins")
    void disableUserFirstDbCallIsLock() {
        long targetId = 10L;
        AppUser target = createUser(targetId, "user10", "User Ten", Role.EMPLOYEE, true);
        AppUser admin1 = createUser(1L, "admin1", "Admin One", Role.ADMIN, true);

        when(appUserRepository.lockWithActiveAdmins(targetId)).thenReturn(List.of(admin1, target));
        when(appUserRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        accountService.disableUser(adminActor, targetId);

        InOrder inOrder = inOrder(appUserRepository);
        inOrder.verify(appUserRepository).lockWithActiveAdmins(targetId);
        inOrder.verify(appUserRepository).saveAndFlush(target);
    }

    @Test
    @DisplayName("enableUser: першим зверненням до БД є блокуючий lockById")
    void enableUserFirstDbCallIsLock() {
        long targetId = 10L;
        AppUser target = createUser(targetId, "user10", "User Ten", Role.EMPLOYEE, false);

        when(appUserRepository.lockById(targetId)).thenReturn(Optional.of(target));
        when(appUserRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        accountService.enableUser(adminActor, targetId);

        InOrder inOrder = inOrder(appUserRepository);
        inOrder.verify(appUserRepository).lockById(targetId);
        inOrder.verify(appUserRepository).saveAndFlush(target);
    }

    @Test
    @DisplayName("resetPassword: першим зверненням до БД є блокуючий lockById")
    void resetPasswordFirstDbCallIsLock() {
        long targetId = 10L;
        AppUser target = createUser(targetId, "user10", "User Ten", Role.EMPLOYEE, true);

        when(appUserRepository.lockById(targetId)).thenReturn(Optional.of(target));
        when(appUserRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        ResetPasswordCommand cmd = new ResetPasswordCommand("newStrongPass123");
        accountService.resetPassword(adminActor, targetId, cmd);

        InOrder inOrder = inOrder(appUserRepository);
        inOrder.verify(appUserRepository).lockById(targetId);
        inOrder.verify(appUserRepository).saveAndFlush(target);
    }

    // -------------------------------------------------------------------------
    // A-17: Безпечні повтори (ідемпотентність)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("changeUserRole: безпечний повтор для користувача з тією самою роллю не викликає saveAndFlush")
    void changeUserRoleSafeReplay() {
        long targetId = 10L;
        AppUser target = createUser(targetId, "user10", "User Ten", Role.EMPLOYEE, true);

        when(appUserRepository.lockWithActiveAdmins(targetId)).thenReturn(List.of(target));

        ChangeUserRoleCommand cmd = new ChangeUserRoleCommand(Role.EMPLOYEE);
        UserView view = accountService.changeUserRole(adminActor, targetId, cmd);

        assertEquals(targetId, view.id());
        assertEquals(Role.EMPLOYEE, view.role());
        verify(appUserRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("disableUser: безпечний повтор для вже вимкненого користувача не викликає saveAndFlush")
    void disableUserAlreadyDisabledSafeReplay() {
        long targetId = 10L;
        AppUser target = createUser(targetId, "user10", "User Ten", Role.EMPLOYEE, false);

        when(appUserRepository.lockWithActiveAdmins(targetId)).thenReturn(List.of(target));

        UserView view = accountService.disableUser(adminActor, targetId);

        assertEquals(targetId, view.id());
        assertFalse(view.enabled());
        verify(appUserRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("enableUser: безпечний повтор для вже активного користувача не викликає saveAndFlush")
    void enableUserAlreadyEnabledSafeReplay() {
        long targetId = 10L;
        AppUser target = createUser(targetId, "user10", "User Ten", Role.EMPLOYEE, true);

        when(appUserRepository.lockById(targetId)).thenReturn(Optional.of(target));

        UserView view = accountService.enableUser(adminActor, targetId);

        assertEquals(targetId, view.id());
        assertTrue(view.enabled());
        verify(appUserRepository, never()).saveAndFlush(any());
    }

    // -------------------------------------------------------------------------
    // INV-14: Політика збереження останнього адміністратора
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("changeUserRole: спроба понизити єдиного активного ADMIN дає помилку LAST_ADMIN")
    void changeUserRoleLastAdminThrowsDomainException() {
        long targetId = 1L;
        AppUser admin = createUser(targetId, "admin1", "Admin One", Role.ADMIN, true);

        when(appUserRepository.lockWithActiveAdmins(targetId)).thenReturn(List.of(admin));

        ChangeUserRoleCommand cmd = new ChangeUserRoleCommand(Role.EMPLOYEE);
        DomainException ex = assertThrows(DomainException.class, () -> accountService.changeUserRole(adminActor, targetId, cmd));

        assertEquals(Reason.LAST_ADMIN, ex.getReason());
        verify(appUserRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("disableUser: спроба вимкнути єдиного активного ADMIN дає помилку LAST_ADMIN")
    void disableUserLastAdminThrowsDomainException() {
        long targetId = 1L;
        AppUser admin = createUser(targetId, "admin1", "Admin One", Role.ADMIN, true);

        when(appUserRepository.lockWithActiveAdmins(targetId)).thenReturn(List.of(admin));

        DomainException ex = assertThrows(DomainException.class, () -> accountService.disableUser(adminActor, targetId));

        assertEquals(Reason.LAST_ADMIN, ex.getReason());
        verify(appUserRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("disableUser: якщо є 2 активних адміністратори, вимкнення одного дозволено")
    void disableUserOneOfTwoAdminsAllowed() {
        long targetId = 1L;
        AppUser admin1 = createUser(targetId, "admin1", "Admin One", Role.ADMIN, true);
        AppUser admin2 = createUser(2L, "admin2", "Admin Two", Role.ADMIN, true);

        when(appUserRepository.lockWithActiveAdmins(targetId)).thenReturn(List.of(admin1, admin2));
        when(appUserRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        UserView view = accountService.disableUser(adminActor, targetId);

        assertEquals(targetId, view.id());
        assertFalse(view.enabled());
        verify(appUserRepository).saveAndFlush(admin1);
    }

    // -------------------------------------------------------------------------
    // D-10: Відображення винятків унікальності
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createUser: помилка унікальності логіна відображається на USERNAME_TAKEN")
    void createUserDuplicateUsernameTranslated() {
        CreateUserCommand cmd = new CreateUserCommand("existing.user", "User Name", "strongPass123", Role.EMPLOYEE);

        DataIntegrityViolationException dive = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uq_app_user_username_key\"",
                new java.sql.SQLException("ERROR: duplicate key value violates unique constraint \"uq_app_user_username_key\"", "23505"));

        when(appUserRepository.saveAndFlush(any())).thenThrow(dive);

        DomainException ex = assertThrows(DomainException.class, () -> accountService.createUser(adminActor, cmd));
        assertEquals(Reason.USERNAME_TAKEN, ex.getReason());
    }

    // -------------------------------------------------------------------------
    // Валідація пароля та вхідних даних
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("createUser: невалідний пароль кидає INVALID_INPUT до звернення до БД")
    void createUserInvalidPasswordThrowsDomainException() {
        CreateUserCommand cmd = new CreateUserCommand("new.user", "New User", "short", Role.EMPLOYEE);

        DomainException ex = assertThrows(DomainException.class, () -> accountService.createUser(adminActor, cmd));
        assertEquals(Reason.INVALID_INPUT, ex.getReason());
        List<FieldError> errors = ex.getFieldErrors();
        assertFalse(errors.isEmpty());
        assertEquals("password", errors.get(0).field());
        verifyNoInteractions(appUserRepository);
    }

    @Test
    @DisplayName("resetPassword: невалідний пароль кидає INVALID_INPUT")
    void resetPasswordInvalidPasswordThrowsDomainException() {
        long targetId = 10L;
        AppUser target = createUser(targetId, "user10", "User Ten", Role.EMPLOYEE, true);

        when(appUserRepository.lockById(targetId)).thenReturn(Optional.of(target));

        ResetPasswordCommand cmd = new ResetPasswordCommand("short");
        DomainException ex = assertThrows(DomainException.class, () -> accountService.resetPassword(adminActor, targetId, cmd));

        assertEquals(Reason.INVALID_INPUT, ex.getReason());
        assertEquals("password", ex.getFieldErrors().get(0).field());
        verify(appUserRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Користувача не знайдено: USER_NOT_FOUND")
    void userNotFoundThrowsDomainException() {
        long missingId = 999L;
        when(appUserRepository.lockWithActiveAdmins(missingId)).thenReturn(List.of());
        when(appUserRepository.lockById(missingId)).thenReturn(Optional.empty());

        assertThrows(DomainException.class, () -> accountService.disableUser(adminActor, missingId));
        assertThrows(DomainException.class, () -> accountService.enableUser(adminActor, missingId));
        assertThrows(DomainException.class, () -> accountService.changeUserRole(adminActor, missingId, new ChangeUserRoleCommand(Role.ADMIN)));
        assertThrows(DomainException.class, () -> accountService.resetPassword(adminActor, missingId, new ResetPasswordCommand("newStrongPass123")));
    }
}

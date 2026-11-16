package roombook.accounts.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import roombook.accounts.domain.AppUser;
import roombook.accounts.persistence.AppUserRepository;
import roombook.kernel.Actor;
import roombook.kernel.DomainException;
import roombook.kernel.Reason;
import roombook.kernel.Role;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Тести читаючих запитів AccountQueries (T-13, TS-06, FR-23, A-34).
 */
@Tag("TS-06")
@Tag("FR-23")
@Tag("A-34")
class AccountQueriesTest {

    private AppUserRepository appUserRepository;
    private AccountQueries accountQueries;

    private final Actor adminActor = new Actor(1L, Role.ADMIN);
    private final Actor employeeActor = new Actor(2L, Role.EMPLOYEE);

    @BeforeEach
    void setUp() {
        appUserRepository = mock(AppUserRepository.class);
        accountQueries = new AccountQueries(appUserRepository);
    }

    private AppUser createUser(long id, String username, String displayName, Role role, boolean enabled) {
        AppUser user = new AppUser(username, displayName, "hash", role);
        ReflectionTestUtils.setField(user, "id", id);
        if (!enabled) {
            user.disable();
        }
        return user;
    }

    @Test
    @DisplayName("listUsers: доступний лише для ADMIN; для EMPLOYEE кидає NOT_PERMITTED")
    void listUsersRequiresAdmin() {
        DomainException ex = assertThrows(DomainException.class, () -> accountQueries.listUsers(employeeActor));
        assertEquals(Reason.NOT_PERMITTED, ex.getReason());
        verifyNoInteractions(appUserRepository);
    }

    @Test
    @DisplayName("listUsers: сортує користувачів за українською абеткою (A-34: ґ, є, і, ї)")
    void listUsersSortedByUkrainianCollation() {
        AppUser u1 = createUser(1L, "user.ya", "Ярослав", Role.EMPLOYEE, true);
        AppUser u2 = createUser(2L, "user.ye", "Євген", Role.EMPLOYEE, true);
        AppUser u3 = createUser(3L, "user.g", "Ґрунт", Role.EMPLOYEE, true);
        AppUser u4 = createUser(4L, "user.b", "Богдан", Role.EMPLOYEE, true);
        AppUser u5 = createUser(5L, "user.i", "Ігор", Role.EMPLOYEE, true);
        AppUser u6 = createUser(6L, "user.yi", "Їжакевич", Role.EMPLOYEE, true);
        AppUser u7 = createUser(7L, "user.a", "Андрій", Role.EMPLOYEE, true);

        when(appUserRepository.findAll()).thenReturn(List.of(u1, u2, u3, u4, u5, u6, u7));

        List<UserView> list = accountQueries.listUsers(adminActor);

        assertEquals(7, list.size());
        assertEquals("Андрій", list.get(0).displayName());
        assertEquals("Богдан", list.get(1).displayName());
        assertEquals("Ґрунт", list.get(2).displayName());
        assertEquals("Євген", list.get(3).displayName());
        assertEquals("Ігор", list.get(4).displayName());
        assertEquals("Їжакевич", list.get(5).displayName());
        assertEquals("Ярослав", list.get(6).displayName());
    }

    @Test
    @DisplayName("getUser: повертає UserView для наявного користувача")
    void getUserSuccess() {
        AppUser u = createUser(10L, "john", "John Doe", Role.EMPLOYEE, true);
        when(appUserRepository.findById(10L)).thenReturn(Optional.of(u));

        UserView view = accountQueries.getUser(adminActor, 10L);

        assertEquals(10L, view.id());
        assertEquals("john", view.username());
        assertEquals("John Doe", view.displayName());
        assertEquals(Role.EMPLOYEE, view.role());
        assertTrue(view.enabled());
    }

    @Test
    @DisplayName("getUser: якщо користувача немає, кидає USER_NOT_FOUND")
    void getUserNotFound() {
        when(appUserRepository.findById(99L)).thenReturn(Optional.empty());

        DomainException ex = assertThrows(DomainException.class, () -> accountQueries.getUser(adminActor, 99L));
        assertEquals(Reason.USER_NOT_FOUND, ex.getReason());
    }
}

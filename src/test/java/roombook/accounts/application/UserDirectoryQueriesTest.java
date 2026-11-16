package roombook.accounts.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import roombook.accounts.api.UserDirectory;
import roombook.accounts.domain.AppUser;
import roombook.accounts.persistence.AppUserRepository;
import roombook.kernel.Role;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Тести реалізації міжмодульного інтерфейсу UserDirectory (T-13, A-31, A-34).
 */
@Tag("A-31")
@Tag("A-34")
class UserDirectoryQueriesTest {

    private AppUserRepository appUserRepository;
    private UserDirectoryQueries userDirectoryQueries;

    @BeforeEach
    void setUp() {
        appUserRepository = mock(AppUserRepository.class);
        userDirectoryQueries = new UserDirectoryQueries(appUserRepository);
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
    @DisplayName("displayNames: повертає мапу id -> displayName для вказаних ідентифікаторів")
    void displayNamesReturnsMap() {
        AppUser u1 = createUser(1L, "user1", "User One", Role.EMPLOYEE, true);
        AppUser u2 = createUser(2L, "user2", "User Two", Role.ADMIN, true);

        when(appUserRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(u1, u2));

        Map<Long, String> result = userDirectoryQueries.displayNames(Set.of(1L, 2L));

        assertEquals(2, result.size());
        assertEquals("User One", result.get(1L));
        assertEquals("User Two", result.get(2L));
    }

    @Test
    @DisplayName("displayNames: для порожнього набору не звертається до репозиторію")
    void displayNamesEmptySet() {
        Map<Long, String> result = userDirectoryQueries.displayNames(Set.of());

        assertTrue(result.isEmpty());
        verifyNoInteractions(appUserRepository);
    }

    @Test
    @DisplayName("selectableUsers: повертає всіх користувачів (зокрема неактивних), відсортованих за українською абеткою (A-34)")
    void selectableUsersIncludesAllAndSortsByUkrainianCollation() {
        AppUser u1 = createUser(1L, "user.ya", "Ярослав", Role.EMPLOYEE, true);
        AppUser u2 = createUser(2L, "user.ye", "Євген", Role.EMPLOYEE, true);
        AppUser u3 = createUser(3L, "user.disabled", "Богдан (вимкнений)", Role.EMPLOYEE, false);
        AppUser u4 = createUser(4L, "user.a", "Андрій", Role.ADMIN, true);

        when(appUserRepository.findAll()).thenReturn(List.of(u1, u2, u3, u4));

        List<UserDirectory.SelectableUser> result = userDirectoryQueries.selectableUsers();

        assertEquals(4, result.size());
        assertEquals("Андрій", result.get(0).displayName());
        assertEquals("Богдан (вимкнений)", result.get(1).displayName());
        assertEquals("Євген", result.get(2).displayName());
        assertEquals("Ярослав", result.get(3).displayName());
    }
}

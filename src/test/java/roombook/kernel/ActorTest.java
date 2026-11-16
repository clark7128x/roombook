package roombook.kernel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("A-17")
@Tag("A-30")
@Tag("A-33")
class ActorTest {

    @Test
    @DisplayName("A-17: Actor.requireAdmin для EMPLOYEE кидає NOT_PERMITTED")
    void requireAdminThrowsNotPermittedForEmployee() {
        Actor employee = new Actor(42L, Role.EMPLOYEE);

        assertEquals(42L, employee.userId());
        assertEquals(Role.EMPLOYEE, employee.role());
        assertFalse(employee.isAdmin());

        DomainException ex = assertThrows(DomainException.class, employee::requireAdmin);
        assertEquals(Reason.NOT_PERMITTED, ex.getReason(), "Очікується код NOT_PERMITTED для EMPLOYEE");
    }

    @Test
    @DisplayName("A-17: Actor.requireAdmin для ADMIN дозволяє дію")
    void requireAdminSucceedsForAdmin() {
        Actor admin = new Actor(1L, Role.ADMIN);

        assertEquals(1L, admin.userId());
        assertEquals(Role.ADMIN, admin.role());
        assertTrue(admin.isAdmin());

        assertDoesNotThrow(admin::requireAdmin);
    }

    @Test
    @DisplayName("A-17: Actor вимагає ненульову роль")
    void nullRoleThrowsException() {
        assertThrows(NullPointerException.class, () -> new Actor(1L, null));
    }
}

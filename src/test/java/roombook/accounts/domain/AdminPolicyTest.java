package roombook.accounts.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import roombook.kernel.DomainException;
import roombook.kernel.Reason;
import roombook.kernel.Role;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Тести політики збереження останнього адміністратора AdminPolicy (T-10, TS-03, INV-14, FR-23, A-17, A-19).
 */
@Tag("TS-03")
@Tag("INV-14")
@Tag("FR-23")
@Tag("A-17")
@Tag("A-19")
class AdminPolicyTest {

    private static final String HASH = "$2a$12$e8Y/p5xR1f9oU8kG.aC3buxK12345678901234567890123456789";

    @Test
    @DisplayName("TS-03 Випадок 1: Два ADMIN і вимкнення або зниження одного з них дозволено (активних лишається 1)")
    void twoAdminsDisablingOrDemotingOneAllowed() {
        AppUser admin = new AppUser("admin1", "Admin 1", HASH, Role.ADMIN);

        // При 2 активних адміністраторах вимкнення одного дозволено
        assertDoesNotThrow(() -> AdminPolicy.checkCanDisable(admin, 2));

        // При 2 активних адміністраторах зниження ролі одного дозволено
        assertDoesNotThrow(() -> AdminPolicy.checkCanChangeRole(admin, Role.EMPLOYEE, 2));
    }

    @Test
    @DisplayName("TS-03 Випадок 2: Вимкнення чи зниження останнього адміністратора дає LAST_ADMIN")
    void lastAdminCannotBeDisabledOrDemoted() {
        AppUser lastAdmin = new AppUser("last.admin", "Last Admin", HASH, Role.ADMIN);

        // Вимкнення останнього дає LAST_ADMIN (activeAdminCount = 1)
        DomainException exDisable = assertThrows(DomainException.class, () ->
                AdminPolicy.checkCanDisable(lastAdmin, 1));
        assertEquals(Reason.LAST_ADMIN, exDisable.getReason());

        // Зниження останнього дає LAST_ADMIN (activeAdminCount = 1)
        DomainException exDemote = assertThrows(DomainException.class, () ->
                AdminPolicy.checkCanChangeRole(lastAdmin, Role.EMPLOYEE, 1));
        assertEquals(Reason.LAST_ADMIN, exDemote.getReason());

        // Навіть якщо активних адміністраторів 0 (пошкоджений стан), операція відхиляється
        DomainException exZero = assertThrows(DomainException.class, () ->
                AdminPolicy.checkCanDisable(lastAdmin, 0));
        assertEquals(Reason.LAST_ADMIN, exZero.getReason());
    }

    @Test
    @DisplayName("TS-03 Випадок 3: Вимкнення та зміна ролі для EMPLOYEE байдуже до кількості адміністраторів")
    void employeeOperationsDoNotCheckLastAdmin() {
        AppUser employee = new AppUser("regular.emp", "Employee", HASH, Role.EMPLOYEE);

        // Вимкнення EMPLOYEE дозволено навіть коли в системі всього 1 адміністратор
        assertDoesNotThrow(() -> AdminPolicy.checkCanDisable(employee, 1));
        assertDoesNotThrow(() -> AdminPolicy.checkCanDisable(employee, 0));

        // Зміна ролі EMPLOYEE на іншу (або підвищення до ADMIN) завжди дозволена
        assertDoesNotThrow(() -> AdminPolicy.checkCanChangeRole(employee, Role.ADMIN, 1));
        assertDoesNotThrow(() -> AdminPolicy.checkCanChangeRole(employee, Role.EMPLOYEE, 1));
    }

    @Test
    @DisplayName("TS-03 Випадок 4: Повтор для вже вимкненого адміністратора не дає LAST_ADMIN (безпечний повтор A-17)")
    void repeatedDisableOrRoleChangeForDisabledUserDoesNotFail() {
        AppUser disabledAdmin = new AppUser("old.admin", "Old Admin", HASH, Role.ADMIN);
        disabledAdmin.disable(); // вже неактивний

        // Повторна деактивація вже вимкненого адміністратора не кидає помилки (A-17)
        assertDoesNotThrow(() -> AdminPolicy.checkCanDisable(disabledAdmin, 1));

        // Зміна ролі вже вимкненого користувача не впливає на кількість активних адміністраторів
        assertDoesNotThrow(() -> AdminPolicy.checkCanChangeRole(disabledAdmin, Role.EMPLOYEE, 1));
    }
}

package roombook.acceptance;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import roombook.testsupport.HttpTestSupport;
import roombook.testsupport.InvariantAudit;
import roombook.testsupport.TestDataBuilder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Специфічні сценарії перевірки вимог безпеки та LAST_ADMIN (T-25, TS-16).
 */
@Tag("TS-16")
class SecurityRequirementsTest extends AcceptanceTestSupport {

    @Test
    @Tag("FR-23")
    @Tag("INV-14")
    void lastAdminCannotBeDisabledOrDemoted() throws Exception {
        // В системі лише один ADMIN (з фікстури)
        // Намагаємось деактивувати
        var disable = api("POST", "/api/v1/users/" + adminId + "/disable", admin, "");
        problem(disable, 409, "LAST_ADMIN");

        // Намагаємось знизити роль
        var demote = api("PUT", "/api/v1/users/" + adminId + "/role", admin,
                mapper.writeValueAsString(Map.of("role", "EMPLOYEE")));
        problem(demote, 409, "LAST_ADMIN");

        // Створюємо другого ADMIN
        var createSecond = api("POST", "/api/v1/users", admin,
                mapper.writeValueAsString(Map.of(
                        "username", "secondadmin", "displayName", "Другий Адмін",
                        "role", "ADMIN", "password", "SecurePass-456"
                )));
        assertEquals(201, createSecond.statusCode());

        // Тепер першого можна деактивувати
        var disableFirst = api("POST", "/api/v1/users/" + adminId + "/disable", admin, "");
        assertEquals(200, disableFirst.statusCode());

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("NFR-06")
    void allEndpointsRequireAuthenticationExceptLoginAndStatic() throws Exception {
        // Захищені ендпоінти без сесії
        for (String path : new String[]{
                "/api/v1/rooms",
                "/api/v1/occupancy",
                "/api/v1/me/bookings",
                "/api/v1/bookings"
        }) {
            var response = api("GET", path, null, "");
            problem(response, 401, "UNAUTHENTICATED");
        }

        // POST-запити також захищені
        var createBooking = api("POST", "/api/v1/bookings", null,
                mapper.writeValueAsString(Map.of(
                        "roomId", 1L, "title", "Тест",
                        "startAt", "2026-10-27T10:00", "endAt", "2026-10-27T11:00"
                )));
        problem(createBooking, 401, "UNAUTHENTICATED");

        // CSRF доступний без автентифікації
        var csrf = api("GET", "/api/v1/csrf", null, "");
        assertEquals(200, csrf.statusCode());

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("NFR-06")
    void rolesAreEnforcedOnServer() throws Exception {
        long room = TestDataBuilder.room().name("Права").insert();

        // EMPLOYEE не може створювати кімнати
        var createRoom = api("POST", "/api/v1/rooms", employee,
                mapper.writeValueAsString(Map.of(
                        "name", "Спроба", "floor", 1, "capacity", 4
                )));
        problem(createRoom, 403, "NOT_PERMITTED");

        // EMPLOYEE не може переглядати всі бронювання
        var allBookings = api("GET", "/api/v1/bookings", employee, "");
        problem(allBookings, 403, "NOT_PERMITTED");

        // EMPLOYEE не може керувати користувачами
        var createUser = api("POST", "/api/v1/users", employee,
                mapper.writeValueAsString(Map.of(
                        "username", "test", "displayName", "Тест",
                        "role", "EMPLOYEE", "password", "Pass-123"
                )));
        problem(createUser, 403, "NOT_PERMITTED");

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("NFR-06")
    void csrfTokenIsRequiredForStateMutations() throws Exception {
        long room = TestDataBuilder.room().name("CSRF").insert();

        // Намагаємось створити бронювання без CSRF-токену
        var sessionWithoutToken = new Session(employee.cookie(), null);
        var response = api("POST", "/api/v1/bookings", sessionWithoutToken,
                mapper.writeValueAsString(Map.of(
                        "roomId", room, "title", "Без токену",
                        "startAt", "2026-10-27T10:00", "endAt", "2026-10-27T11:00"
                )));
        problem(response, 403, "CSRF_INVALID");

        // З токеном працює
        var withToken = api("POST", "/api/v1/bookings", employee,
                mapper.writeValueAsString(Map.of(
                        "roomId", room, "title", "З токеном",
                        "startAt", "2026-10-27T10:00", "endAt", "2026-10-27T11:00"
                )));
        assertEquals(201, withToken.statusCode());

        InvariantAudit.assertAllInvariantsHold();
    }
}

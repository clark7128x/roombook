package roombook.acceptance;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import roombook.testsupport.HttpTestSupport;
import roombook.testsupport.InvariantAudit;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Наскрізний сценарій UC-06: управління обліковими записами (T-25, TS-16).
 */
@Tag("TS-16")
@Tag("UC-06")
class UC06ManageUsersTest extends AcceptanceTestSupport {

    @Test
    @Tag("FR-23")
    void adminCreatesUserAssignsRoleAndResetsPassword() throws Exception {
        // EMPLOYEE не має прав створювати користувачів
        var employeeCreate = api("POST", "/api/v1/users", employee,
                mapper.writeValueAsString(Map.of(
                        "username", "attempt", "displayName", "Спроба",
                        "role", "EMPLOYEE", "password", "NewPassword-123"
                )));
        problem(employeeCreate, 403, "NOT_PERMITTED");

        // ADMIN створює користувача
        var create = api("POST", "/api/v1/users", admin,
                mapper.writeValueAsString(Map.of(
                        "username", "newuser", "displayName", "Новий Користувач",
                        "role", "EMPLOYEE", "password", "InitialPass-123"
                )));
        assertEquals(201, create.statusCode());
        var user = json(create);
        assertTrue(user.get("enabled").asBoolean());
        assertEquals("EMPLOYEE", user.get("role").asString());
        long userId = user.get("id").asLong();

        // Змінює роль
        var changeRole = api("PUT", "/api/v1/users/" + userId + "/role", admin,
                mapper.writeValueAsString(Map.of("role", "ADMIN")));
        assertEquals(200, changeRole.statusCode());
        assertEquals("ADMIN", json(changeRole).get("role").asString());

        // Скидає пароль
        var resetPassword = api("PUT", "/api/v1/users/" + userId + "/password", admin,
                mapper.writeValueAsString(Map.of("password", "NewSecurePass-456")));
        assertEquals(204, resetPassword.statusCode());

        // Деактивує
        var disable = api("POST", "/api/v1/users/" + userId + "/disable", admin, "");
        assertEquals(200, disable.statusCode());
        assertFalse(json(disable).get("enabled").asBoolean());

        // Активує
        var enable = api("POST", "/api/v1/users/" + userId + "/enable", admin, "");
        assertEquals(200, enable.statusCode());
        assertTrue(json(enable).get("enabled").asBoolean());

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("FR-23")
    void deactivatedUserCannotLogin() throws Exception {
        // Створюємо користувача
        var create = api("POST", "/api/v1/users", admin,
                mapper.writeValueAsString(Map.of(
                        "username", "tobedisabled", "displayName", "Деактивований",
                        "role", "EMPLOYEE", "password", "Password-12345"
                )));
        long userId = json(create).get("id").asLong();

        // Користувач може увійти
        var beforeDisable = login("tobedisabled");
        assertNotNull(beforeDisable.token());

        // Деактивуємо
        api("POST", "/api/v1/users/" + userId + "/disable", admin, "");

        // Спроба входу відхиляється
        var csrfResponse = send("GET", "/api/v1/csrf", null, "", "application/json");
        var sessionCookie = csrfResponse.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0];
        var csrfToken = json(csrfResponse).get("token").asString();
        var loginSession = new Session(sessionCookie, csrfToken);

        var loginAttempt = send("POST", "/login", loginSession,
                "username=tobedisabled&password=Password-12345", "application/x-www-form-urlencoded");
        assertEquals(302, loginAttempt.statusCode());
        assertTrue(loginAttempt.headers().firstValue("Location").orElseThrow().contains("error"));

        InvariantAudit.assertAllInvariantsHold();
    }

    @Test
    @Tag("FR-23")
    void usernamesMustBeUnique() throws Exception {
        api("POST", "/api/v1/users", admin,
                mapper.writeValueAsString(Map.of(
                        "username", "unique", "displayName", "Перший",
                        "role", "EMPLOYEE", "password", "Password-123"
                )));

        var duplicate = api("POST", "/api/v1/users", admin,
                mapper.writeValueAsString(Map.of(
                        "username", "unique", "displayName", "Другий",
                        "role", "EMPLOYEE", "password", "Password-456"
                )));
        problem(duplicate, 409, "USERNAME_TAKEN");

        InvariantAudit.assertAllInvariantsHold();
    }
}

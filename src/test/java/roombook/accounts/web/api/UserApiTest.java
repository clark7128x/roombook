package roombook.accounts.web.api;

import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import roombook.kernel.Role;
import roombook.testsupport.*;
import static org.junit.jupiter.api.Assertions.*;

class UserApiTest extends HttpTestSupport {
    private String input(String username, String name, String password) {
        return mapper.writeValueAsString(Map.of("username", username, "displayName", name, "password", password,
                "role", "EMPLOYEE", "enabled", false));
    }
    private void noCredentials(String response) {
        assertFalse(response.contains("\"password\""));
        assertFalse(response.contains("passwordHash"));
        assertFalse(response.contains("Password-12345"));
        assertFalse(response.contains("{bcrypt}"));
    }
    @Test @Tag("TS-08") @Tag("EP-13") @Tag("EP-14") @Tag("EP-15") @Tag("EP-16") @Tag("EP-17") @Tag("EP-18") @Tag("SEC-03")
    void userLifecycleAndUkrainianOrderingNeverExposeCredentials() throws Exception {
        var created = api("POST", "/api/v1/users", admin, input("new.user", "Єва", "Password-12345"));
        assertEquals(201, created.statusCode(), created.body()); noCredentials(created.body());
        String path = created.headers().firstValue("Location").orElseThrow();
        assertEquals("/api/v1/users/" + json(created).get("id").asLong(), path);
        assertTrue(json(created).get("enabled").asBoolean());
        assertEquals(200, api("GET", path, admin, "").statusCode());
        assertEquals(201, api("POST", "/api/v1/users", admin, input("emma", "Емма", "Password-12345")).statusCode());
        var list = api("GET", "/api/v1/users", admin, ""); noCredentials(list.body());
        assertEquals("Емма", json(list).get(1).get("displayName").asString());
        assertEquals("Єва", json(list).get(2).get("displayName").asString());
        assertEquals("ADMIN", json(api("PUT", path + "/role", admin, "{\"role\":\"ADMIN\"}")).get("role").asString());
        var session = login("new.user");
        assertFalse(json(api("POST", path + "/disable", admin, "")).get("enabled").asBoolean());
        problem(api("GET", "/api/v1/rooms", session, ""), 401, "UNAUTHENTICATED");
        assertFalse(json(api("POST", path + "/disable", admin, "")).get("enabled").asBoolean());
        assertTrue(json(api("POST", path + "/enable", admin, "")).get("enabled").asBoolean());
        problem(api("POST", "/api/v1/users", admin, input("NEW.USER", "Дублікат", "Password-12345")), 409, "USERNAME_TAKEN");
        problem(api("DELETE", path, admin, ""), 405, "UNSUPPORTED_REQUEST");
        InvariantAudit.assertAllInvariantsHold();
    }

    @Test @Tag("TS-08") @Tag("EP-19") @Tag("SEC-04") @Tag("SEC-03")
    void passwordValidationIdentifiesFieldAndResetReturnsEmpty204() throws Exception {
        for (String password : new String[]{"short", "x".repeat(73), "ї".repeat(37), "long.username"}) {
            var response = api("POST", "/api/v1/users", admin, input("long.username", "Ім’я", password));
            problem(response, 400, "INVALID_INPUT");
            assertEquals("password", json(response).get("errors").get(0).get("field").asString());
            assertFalse(response.body().contains(password));
        }
        var badReset = api("PUT", "/api/v1/users/" + employeeId + "/password", admin, "{\"password\":\"short\"}");
        problem(badReset, 400, "INVALID_INPUT");
        assertEquals("password", json(badReset).get("errors").get(0).get("field").asString());
        var reset = api("PUT", "/api/v1/users/" + employeeId + "/password", admin, "{\"password\":\"New-password-123\"}");
        assertEquals(204, reset.statusCode()); assertEquals("", reset.body());
        // A password reset does not invalidate an existing session under the agreed session policy.
        assertEquals(200, api("GET", "/api/v1/rooms", employee, "").statusCode());
        noCredentials(api("GET", "/api/v1/users/" + employeeId, admin, "").body());
    }

    @Test @Tag("TS-09") @Tag("INV-14") @Tag("FR-23")
    void lastEnabledAdminCannotBeDisabledOrDemotedOverHttp() throws Exception {
        problem(api("POST", "/api/v1/users/" + adminId + "/disable", admin, ""), 409, "LAST_ADMIN");
        problem(api("PUT", "/api/v1/users/" + adminId + "/role", admin, "{\"role\":\"EMPLOYEE\"}"), 409, "LAST_ADMIN");
        problem(api("PUT", "/api/v1/users/" + employeeId + "/role", admin, "{\"role\":\"OWNER\"}"), 400, "INVALID_INPUT");
        InvariantAudit.assertAllInvariantsHold();
    }

    @Test @Tag("SEC-03")
    void requestRecordStringsRedactPasswords() {
        String secret = "marker-secret-password";
        assertFalse(new UserInput("name", "Ім’я", secret, Role.EMPLOYEE).toString().contains(secret));
        assertFalse(new PasswordInput(secret).toString().contains(secret));
    }
}

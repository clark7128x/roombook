package roombook.acceptance;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import roombook.testsupport.HttpTestSupport;
import roombook.testsupport.TestDataBuilder;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Наскрізний сценарій UC-01: вхід і вихід (T-25, TS-16).
 */
@Tag("TS-16")
@Tag("UC-01")
class UC01AuthenticationTest extends AcceptanceTestSupport {

    @Test
    @Tag("FR-01")
    void guestCannotAccessProtectedResourcesWithoutAuthentication() throws Exception {
        var response = api("GET", "/api/v1/rooms", null, "");
        problem(response, 401, "UNAUTHENTICATED");

        response = api("GET", "/api/v1/occupancy", null, "");
        problem(response, 401, "UNAUTHENTICATED");

        response = api("GET", "/api/v1/me/bookings", null, "");
        problem(response, 401, "UNAUTHENTICATED");
    }

    @Test
    @Tag("FR-01")
    void userLogsInWithCredentialsAndAccessesProtectedResources() throws Exception {
        // Створюємо користувача
        long userId = user("testuser", "Тестовий Користувач", "EMPLOYEE");

        // Отримуємо CSRF-токен
        var csrfResponse = send("GET", "/api/v1/csrf", null, "", "application/json");
        assertEquals(200, csrfResponse.statusCode());
        var sessionCookie = csrfResponse.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0];
        var csrfToken = json(csrfResponse).get("token").asString();

        // Входимо
        var loginSession = new Session(sessionCookie, csrfToken);
        var loginResponse = send("POST", "/login", loginSession,
                "username=testuser&password=Password-12345", "application/x-www-form-urlencoded");
        assertEquals(302, loginResponse.statusCode());
        assertFalse(loginResponse.headers().firstValue("Location").orElseThrow().contains("error"));

        // Отримуємо новий токен після входу
        var newCookie = loginResponse.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0];
        var newSession = new Session(newCookie, null);
        var newCsrfResponse = send("GET", "/api/v1/csrf", newSession, "", "application/json");
        var newToken = json(newCsrfResponse).get("token").asString();
        var authenticatedSession = new Session(newCookie, newToken);

        // Доступ до захищених ресурсів
        var roomsResponse = api("GET", "/api/v1/rooms", authenticatedSession, "");
        assertEquals(200, roomsResponse.statusCode());
    }

    @Test
    @Tag("FR-01")
    void userLogsInWithWrongCredentialsAndReceivesUniformError() throws Exception {
        user("existing", "Існуючий", "EMPLOYEE");

        var csrfResponse = send("GET", "/api/v1/csrf", null, "", "application/json");
        var sessionCookie = csrfResponse.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0];
        var csrfToken = json(csrfResponse).get("token").asString();
        var session = new Session(sessionCookie, csrfToken);

        // Невірний пароль
        var wrongPassword = send("POST", "/login", session,
                "username=existing&password=WrongPassword", "application/x-www-form-urlencoded");
        assertEquals(302, wrongPassword.statusCode());
        assertTrue(wrongPassword.headers().firstValue("Location").orElseThrow().contains("error"));

        // Неіснуючий користувач
        var newSession = login("admin");
        var unknownUser = send("POST", "/login", newSession,
                "username=nonexistent&password=Password-12345", "application/x-www-form-urlencoded");
        assertEquals(302, unknownUser.statusCode());
        assertTrue(unknownUser.headers().firstValue("Location").orElseThrow().contains("error"));
    }

    @Test
    @Tag("FR-01")
    void userLogsOutAndSessionEnds() throws Exception {
        var session = login("employee");

        // Можемо читати кімнати
        var beforeLogout = api("GET", "/api/v1/rooms", session, "");
        assertEquals(200, beforeLogout.statusCode());

        // Виходимо
        var logoutResponse = send("POST", "/logout", session, "", "application/x-www-form-urlencoded");
        assertEquals(204, logoutResponse.statusCode());

        // Після виходу доступ заборонено
        var afterLogout = api("GET", "/api/v1/rooms", session, "");
        problem(afterLogout, 401, "UNAUTHENTICATED");
    }
}

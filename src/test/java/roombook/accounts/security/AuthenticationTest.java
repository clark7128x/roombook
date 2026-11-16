package roombook.accounts.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.web.bind.annotation.*;
import roombook.accounts.application.AccountService;
import roombook.accounts.application.ChangeUserRoleCommand;
import roombook.kernel.*;
import roombook.testsupport.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "server.servlet.session.cookie.secure=true"})
@ActiveProfiles("test")
@Import(AuthenticationTest.Routes.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthenticationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        var container = PostgresContainerHolder.getInstance();
        String url = "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/roombook";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "roombook_app");
        registry.add("spring.datasource.password", () -> "app_pass");
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", () -> "roombook_owner");
        registry.add("spring.flyway.password", () -> "owner_pass");
    }
    @TestConfiguration static class Routes {
        @Bean ProbeController probeController() { return new ProbeController(); }
    }
    @RestController static class ProbeController {
        @GetMapping({"/api/v1/auth-probe", "/admin/auth-probe"})
        Map<String, Object> probe(Actor actor, HttpServletRequest request) {
            return Map.of("userId", actor.userId(), "role", actor.role(), "timeout", request.getSession().getMaxInactiveInterval());
        }
        @GetMapping("/api/v1/expire-probe")
        Map<String, Object> expire(HttpServletRequest request) {
            request.getSession().setMaxInactiveInterval(1);
            return Map.of("timeout", 1);
        }
        @GetMapping("/api/v1/token-probe")
        Map<String, Object> token(HttpServletRequest request) {
            var token = (org.springframework.security.web.csrf.CsrfToken) request.getAttribute("_csrf");
            return Map.of("token", token.getToken());
        }
        @PostMapping("/api/v1/write-probe")
        Map<String, Object> write(Actor actor) { return Map.of("userId", actor.userId()); }
    }
    @Autowired Environment environment;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountService accounts;
    @Autowired jakarta.servlet.ServletContext servletContext;
    @Autowired org.springframework.security.web.FilterChainProxy securityFilters;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping mappings;
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final String password = "Password-12345";
    private long employeeId;
    private long adminId;
    private long keeperId;

    @BeforeEach void seed() {
        PostgresContainerHolder.cleanDatabase();
        employeeId = user("Employee", "EMPLOYEE", true);
        adminId = user("Admin", "ADMIN", true);
        keeperId = user("Keeper", "ADMIN", true);
        user("Disabled", "EMPLOYEE", false);
    }
    private long user(String name, String role, boolean enabled) {
        return TestDataBuilder.appUser().username(name).role(role).enabled(enabled).passwordHash(encoder.encode(password)).insert();
    }
    private HttpResponse<String> get(String path, String cookie) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + environment.getProperty("local.server.port") + path));
        if (cookie != null) builder.header("Cookie", cookie);
        return client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(String path, String cookie, String body, String accept) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + environment.getProperty("local.server.port") + path))
                .header("Cookie", cookie).header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", accept).POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String cookie(HttpResponse<String> response) { return response.headers().firstValue("set-cookie").orElseThrow().split(";")[0]; }
    private String csrf(HttpResponse<String> response) {
        var matcher = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(response.body());
        assertTrue(matcher.find(), response.body());
        return matcher.group(1);
    }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private HttpResponse<String> login(String username, String rawPassword, HttpResponse<String> page) throws Exception {
        return post("/login", cookie(page), "username=" + encode(username) + "&password=" + encode(rawPassword)
                + "&_csrf=" + encode(csrf(page)), "text/html");
    }
    private String loggedIn(String username) throws Exception {
        var response = login(username, password, get("/login", null));
        assertEquals(302, response.statusCode());
        assertFalse(response.headers().firstValue("Location").orElse("").contains("error"));
        return cookie(response);
    }

    @Test @Tag("TS-17") @Tag("SEC-01") @Tag("SEC-05") @Tag("SEC-06")
    void loginIgnoresUsernameCaseRotatesSessionAndSetsCookieFlags() throws Exception {
        var page = get("/login", null);
        assertEquals(200, page.statusCode());
        assertEquals(15, servletContext.getSessionTimeout(), "Anonymous sessions expire after 15 minutes");
        String setCookie = page.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("Secure"));
        assertTrue(setCookie.contains("SameSite=Lax"));
        var login = login("eMpLoYeE", password, page);
        assertNotEquals(cookie(page), cookie(login));
        var probe = get("/api/v1/auth-probe", cookie(login));
        assertEquals(200, probe.statusCode(), probe.body());
        assertTrue(probe.body().contains("\"userId\":" + employeeId));
        assertTrue(probe.body().contains("28800"));
        assertEquals(302, get("/login", cookie(login)).statusCode());
    }

    @Test @Tag("TS-17") @Tag("SEC-02") @Tag("SEC-04")
    void allInvalidCredentialsHaveTheSameResponse() throws Exception {
        for (String[] input : new String[][]{{"missing", password}, {"Employee", "wrong"}, {"Disabled", password},
                {"Employee", "x".repeat(73)}, {"' OR 1=1 --", password}, {"x".repeat(65), password}}) {
            var response = login(input[0], input[1], get("/login", null));
            assertEquals(302, response.statusCode());
            var location = URI.create(response.headers().firstValue("Location").orElseThrow());
            assertEquals("/login", location.getPath());
            assertEquals("error", location.getQuery());
            assertEquals("", response.body());
        }
    }

    @Test @Tag("TS-17") @Tag("SEC-08")
    void disableAndRoleChangeTakeEffectOnNextRequest() throws Exception {
        String employee = loggedIn("Employee");
        String admin = loggedIn("Admin");
        assertEquals(200, get("/admin/auth-probe", admin).statusCode());
        accounts.changeUserRole(new Actor(keeperId, Role.ADMIN), adminId, new ChangeUserRoleCommand(Role.EMPLOYEE));
        assertEquals(403, get("/admin/auth-probe", admin).statusCode());
        assertTrue(get("/api/v1/auth-probe", admin).body().contains("EMPLOYEE"));
        accounts.disableUser(new Actor(keeperId, Role.ADMIN), employeeId);
        assertEquals(401, get("/api/v1/auth-probe", employee).statusCode());
    }

    @Test @Tag("TS-17") @Tag("SEC-07")
    void idleExpiryAndLogoutInvalidateSessions() throws Exception {
        String session = loggedIn("Employee");
        assertEquals(200, get("/api/v1/expire-probe", session).statusCode());
        var afterIdle = CompletableFuture.supplyAsync(() -> {
            try { return get("/api/v1/auth-probe", session); }
            catch (Exception exception) { throw new CompletionException(exception); }
        }, CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS)).get(10, TimeUnit.SECONDS);
        assertEquals(401, afterIdle.statusCode());
        String another = loggedIn("Employee");
        String tokenJson = get("/api/v1/token-probe", another).body();
        String token = tools.jackson.databind.json.JsonMapper.builder().build().readTree(tokenJson).get("token").asString();
        assertEquals(204, post("/logout", another, "_csrf=" + encode(token), "application/json").statusCode());
        assertEquals(401, get("/api/v1/auth-probe", another).statusCode());
    }

    @Test @Tag("TS-17") @Tag("SEC-15")
    void onlyFullPagesAreSavedForThePostLoginRedirect() throws Exception {
        var api = get("/api/v1/auth-probe", null);
        assertEquals(401, api.statusCode());
        assertTrue(api.headers().firstValue("Set-Cookie").isEmpty());
        var fragment = get("/fragments/room-day", null);
        assertTrue(fragment.headers().firstValue("Set-Cookie").isEmpty());
        var saved = get("/admin/auth-probe", null);
        assertEquals(302, saved.statusCode());
        String savedCookie = cookie(saved);
        var page = get("/login", savedCookie);
        var result = post("/login", savedCookie, "username=Admin&password=" + encode(password)
                + "&_csrf=" + encode(csrf(page)), "text/html");
        assertEquals(302, result.statusCode());
        assertEquals("/admin/auth-probe", URI.create(result.headers().firstValue("Location").orElseThrow()).getPath());
    }

    private String apiToken(String session) throws Exception {
        return tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(get("/api/v1/csrf", session).body()).get("token").asString();
    }
    @Test @Tag("SEC-08") @Tag("SEC-11") @Tag("SEC-12")
    void identityRefreshRunsAfterContextAndHeadersButBeforeCsrfAndAuthorization() {
        var names = securityFilters.getFilterChains().stream()
                .filter(chain -> chain.getFilters().stream().anyMatch(filter -> filter instanceof CurrentUserFilter))
                .findFirst().orElseThrow().getFilters().stream()
                .map(filter -> filter.getClass().getSimpleName()).toList();
        int refresh = names.indexOf("CurrentUserFilter");
        assertTrue(names.indexOf("SecurityContextHolderFilter") >= 0);
        assertTrue(names.indexOf("HeaderWriterFilter") > names.indexOf("SecurityContextHolderFilter"));
        assertTrue(refresh > names.indexOf("HeaderWriterFilter"));
        assertTrue(names.indexOf("CsrfFilter") > refresh);
        assertTrue(names.indexOf("AuthorizationFilter") > names.indexOf("CsrfFilter"));
    }
    private HttpResponse<String> request(String method, String path, String session, String token, String body,
                                         String... headers) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + environment.getProperty("local.server.port") + path))
                .header("Accept", "application/json");
        if (session != null) builder.header("Cookie", session);
        if (token != null) builder.header("X-CSRF-TOKEN", token);
        if (headers.length > 0) builder.headers(headers);
        return client.send(builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test @Tag("TS-17") @Tag("EP-20") @Tag("SEC-09") @Tag("SEC-10") @Tag("SEC-11")
    void scriptCsrfFlowRotatesTokenAndAcceptsBothHeaderAndForm() throws Exception {
        var initial = get("/api/v1/csrf", null);
        assertEquals(200, initial.statusCode());
        var json = tools.jackson.databind.json.JsonMapper.builder().build().readTree(initial.body());
        assertEquals("X-CSRF-TOKEN", json.get("headerName").asString());
        assertEquals("_csrf", json.get("parameterName").asString());
        String oldToken = json.get("token").asString();
        var login = post("/login", cookie(initial), "username=Employee&password=" + encode(password)
                + "&_csrf=" + encode(oldToken), "text/html");
        assertEquals(302, login.statusCode());
        String session = cookie(login);
        assertNotEquals(cookie(initial), session);
        String token = apiToken(session);
        assertNotEquals(oldToken, token);
        var stale = request("POST", "/api/v1/write-probe", session, oldToken, "");
        assertEquals(403, stale.statusCode());
        assertTrue(stale.body().contains("CSRF_INVALID"));
        assertEquals(200, request("POST", "/api/v1/write-probe", session, token, "").statusCode());
        assertEquals(204, request("POST", "/logout", session, token, "").statusCode());
        var expired = request("POST", "/api/v1/write-probe", session, token, "");
        assertEquals(401, expired.statusCode());
        assertTrue(expired.body().contains("UNAUTHENTICATED"));
        var htmx = request("POST", "/fragments/write-probe", session, token, "", "HX-Request", "true");
        assertEquals(401, htmx.statusCode());
        assertEquals("/login", htmx.headers().firstValue("HX-Redirect").orElseThrow());
        assertEquals("", htmx.body());
    }

    @Test @Tag("TS-10") @Tag("SEC-12") @Tag("SEC-13") @Tag("SEC-14")
    void headersAndRoleBoundariesApplyBeforeControllerDispatch() throws Exception {
        for (String path : new String[]{"/login", "/api/v1/csrf"}) {
            var response = request("GET", path, null, null, "", "Origin", "https://foreign.example");
            assertEquals(200, response.statusCode());
            assertEquals("default-src 'self'; frame-ancestors 'none'; form-action 'self'; base-uri 'self'",
                    response.headers().firstValue("Content-Security-Policy").orElseThrow());
            assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElseThrow());
            assertEquals("DENY", response.headers().firstValue("X-Frame-Options").orElseThrow());
            assertEquals("no-referrer", response.headers().firstValue("Referrer-Policy").orElseThrow());
            assertTrue(response.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
            assertTrue(response.headers().map().keySet().stream().noneMatch(name -> name.startsWith("access-control-")));
            assertTrue(response.headers().firstValue("Strict-Transport-Security").isEmpty());
        }
        String employee = loggedIn("Employee");
        assertEquals(403, get("/api/v1/bookings", employee).statusCode());
        // Missing detail reaches MVC (404), proving it is not accidentally admin-only.
        assertEquals(404, get("/api/v1/bookings/999999999", employee).statusCode());
        assertEquals(403, get("/api/v1/users", employee).statusCode());
        var denied = request("POST", "/api/v1/rooms", employee, apiToken(employee), "");
        assertEquals(403, denied.statusCode());
        assertTrue(denied.body().contains("NOT_PERMITTED"));
        var fragment = request("GET", "/admin/users", employee, null, "", "HX-Request", "true");
        assertEquals(403, fragment.statusCode());
        assertEquals("#alerts", fragment.headers().firstValue("HX-Retarget").orElseThrow());
    }

    private java.util.List<String[]> accessRows() throws Exception {
        return Files.readAllLines(Path.of("src/test/resources/access-matrix.tsv")).stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .map(line -> line.split("\\s+")).toList();
    }
    @Test @Tag("TS-10")
    void accessMatrixCoversEveryProductionMvcHandler() throws Exception {
        Set<String> covered = new HashSet<>();
        for (var row : accessRows()) covered.add(row[0] + " " + row[1]);
        var actual = new HashSet<String>();
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            String name = handler.getBeanType().getName();
            boolean testFixture = handler.getBeanType().getProtectionDomain().getCodeSource().getLocation().toString().contains("test-classes");
            if (!testFixture && name.startsWith("roombook.") && (name.contains(".web.api.") || name.contains(".web.ui."))) {
                assertFalse(mapping.getMethodsCondition().getMethods().isEmpty(), name + " must declare HTTP methods");
                mapping.getMethodsCondition().getMethods().forEach(method -> mapping.getPatternValues()
                        .forEach(path -> actual.add(method.name() + " " + path)));
            }
        });
        assertFalse(actual.isEmpty());
        actual.removeAll(covered);
        assertEquals(Set.of(), actual, "MVC endpoints missing from access-matrix.tsv");
    }

    @Test @Tag("TS-10") @Tag("SEC-09")
    void accessMatrixMatchesAnonymousEmployeeAndAdminAndUnsafeRoutesRequireCsrf() throws Exception {
        for (var row : accessRows()) {
            String method = row[0], path = row[1].replaceAll("\\{[^}]+}", "999999999");
            for (int role = 0; role < 3; role++) {
                String session = role == 0 ? cookie(get("/api/v1/csrf", null)) : loggedIn(role == 1 ? "Employee" : "Admin");
                String body = path.equals("/login") && method.equals("POST")
                        ? "username=Employee&password=" + encode(password) : "";
                var response = request(method, path, session, apiToken(session), body,
                        "Content-Type", path.startsWith("/api/") ? "application/json" : "application/x-www-form-urlencoded");
                assertEquals(Integer.parseInt(row[role + 2]), response.statusCode(), method + " " + path + " role=" + role + " " + response.body());
            }
            if (method.equals("POST") || method.equals("PUT")) {
                String session = loggedIn("Admin");
                var response = request(method, path, session, null, "");
                assertEquals(403, response.statusCode(), path);
                assertTrue(response.body().contains(path.startsWith("/api/") ? "CSRF_INVALID" : "Недійсний токен захисту"), path);
            }
        }
    }
}

package roombook.testsupport;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "spring.datasource.hikari.minimum-idle=1"})
@ActiveProfiles("test")
@Import(HttpTestSupport.FixedTime.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class HttpTestSupport {
    @TestConfiguration public static class FixedTime {
        @Bean @Primary TestClockFactory.MutableClock httpClock() {
            return TestClockFactory.fixed(Instant.parse("2026-10-27T07:00:00Z"));
        }
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        var container = PostgresContainerHolder.getInstance();
        String url = "jdbc:postgresql://" + container.getHost() + ":" + container.getMappedPort(5432) + "/roombook";
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "roombook_app");
        registry.add("spring.datasource.password", () -> "app_pass");
        registry.add("spring.flyway.url", () -> url);
        registry.add("spring.flyway.user", () -> "roombook_owner");
        registry.add("spring.flyway.password", () -> "owner_pass");
    }
    @Autowired protected Environment environment;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected TestClockFactory.MutableClock clock;
    protected long employeeId, adminId, otherId;
    protected Session employee, admin, other;
    protected final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    public record Session(String cookie, String token) {}

    @BeforeEach void initializeHttpFixtures() throws Exception {
        PostgresContainerHolder.cleanDatabase();
        clock.setInstant(Instant.parse("2026-10-27T07:00:00Z"));
        employeeId = user("employee", "Працівник", "EMPLOYEE");
        adminId = user("admin", "Адміністратор", "ADMIN");
        otherId = user("other", "Інший", "EMPLOYEE");
        employee = login("employee"); admin = login("admin"); other = login("other");
    }
    protected long user(String name, String displayName, String role) {
        return TestDataBuilder.appUser().username(name).displayName(displayName).role(role)
                .passwordHash(encoder.encode("Password-12345")).insert();
    }
    protected Session login(String username) throws Exception {
        var csrf = send("GET", "/api/v1/csrf", null, "", "application/json");
        var initial = new Session(csrf.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0], json(csrf).get("token").asString());
        var response = send("POST", "/login", initial, "username=" + username + "&password=Password-12345", "application/x-www-form-urlencoded");
        assertEquals(302, response.statusCode(), response.body());
        assertFalse(response.headers().firstValue("Location").orElseThrow().contains("error"));
        var loggedIn = new Session(response.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0], null);
        return new Session(loggedIn.cookie(), json(send("GET", "/api/v1/csrf", loggedIn, "", "application/json")).get("token").asString());
    }
    protected HttpResponse<String> send(String method, String path, Session session, String body, String type) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + environment.getProperty("local.server.port") + path))
                .header("Accept", "application/json").header("Content-Type", type);
        if (session != null) {
            builder.header("Cookie", session.cookie());
            if (session.token() != null) builder.header("X-CSRF-TOKEN", session.token());
        }
        return client.send(builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    protected HttpResponse<String> api(String method, String path, Session session, String body) throws Exception {
        return send(method, path, session, body, "application/json");
    }
    protected JsonNode json(HttpResponse<String> response) { return mapper.readTree(response.body()); }
    protected String query(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    protected void problem(HttpResponse<String> response, int status, String code) {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/problem+json"));
        assertEquals(code, json(response).get("code").asString());
    }
}

package roombook.platform;

import ch.qos.logback.classic.*;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.web.bind.annotation.*;
import roombook.testsupport.*;
import static org.junit.jupiter.api.Assertions.*;

@Import(ObservabilityHttpTest.Probes.class)
@org.springframework.test.context.TestPropertySource(properties = "spring.datasource.hikari.data-source-properties.socketTimeout=2")
class ObservabilityHttpTest extends HttpTestSupport {
    @TestConfiguration static class Probes { @Bean ProbeController operationsProbe() { return new ProbeController(); } }
    @RestController static class ProbeController {
        @GetMapping("/api/v1/operations-test-failure") String fail() { throw new IllegalStateException("controlled failure"); }
    }
    static class Capture extends AppenderBase<ILoggingEvent> {
        final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();
        @Override protected void append(ILoggingEvent event) { event.prepareForDeferredProcessing(); events.add(event); }
    }
    private final Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private Capture capture;
    @BeforeEach void captureEvents() { capture = new Capture(); capture.start(); root.addAppender(capture); }
    @AfterEach void releaseCapture() { root.detachAppender(capture); capture.stop(); }

    @Test @Tag("TS-21") @Tag("OPS-04") @Tag("OPS-05") @Tag("OPS-01")
    void eventsContainStructuredIdsAndNeverSensitiveInputs() throws Exception {
        employee = login("employee");
        var failed = send("POST", "/login", other, "username=" + "z".repeat(80) + "&password=MarkerLoginSecret", "application/x-www-form-urlencoded");
        assertEquals(302, failed.statusCode());
        var createdRoom = api("POST", "/api/v1/rooms", admin, "{\"name\":\"Studio\",\"floor\":1,\"capacity\":8}");
        assertEquals(201, createdRoom.statusCode(), createdRoom.body());
        long room = json(createdRoom).get("id").asLong();
        assertEquals(200, api("PUT", "/api/v1/rooms/" + room, admin, "{\"name\":\"Studio 2\",\"floor\":1,\"capacity\":8}").statusCode());
        String booking = mapper.writeValueAsString(Map.of("roomId", room, "title", "MarkerPrivateTitle", "startAt", "2026-10-27T10:00", "endAt", "2026-10-27T11:00"));
        var created = api("POST", "/api/v1/bookings", employee, booking);
        assertEquals(201, created.statusCode(), created.body());
        problem(api("POST", "/api/v1/bookings", employee, booking), 409, "SLOT_TAKEN");
        assertEquals(200, api("POST", "/api/v1/bookings/" + json(created).get("id").asLong() + "/cancel", employee, "").statusCode());
        assertEquals(200, api("POST", "/api/v1/rooms/" + room + "/deactivate", admin, "").statusCode());
        assertEquals(200, api("POST", "/api/v1/rooms/" + room + "/activate", admin, "").statusCode());
        var user = api("POST", "/api/v1/users", admin, mapper.writeValueAsString(Map.of("username", "new-account", "displayName", "MarkerPrivateDisplay", "password", "MarkerPassword-123", "role", "EMPLOYEE")));
        assertEquals(201, user.statusCode(), user.body());
        long id = json(user).get("id").asLong();
        assertEquals(200, api("PUT", "/api/v1/users/" + id + "/role", admin, "{\"role\":\"ADMIN\"}").statusCode());
        assertEquals(200, api("POST", "/api/v1/users/" + id + "/disable", admin, "").statusCode());
        assertEquals(200, api("POST", "/api/v1/users/" + id + "/enable", admin, "").statusCode());
        assertEquals(204, api("PUT", "/api/v1/users/" + id + "/password", admin, "{\"password\":\"MarkerReset-123\"}").statusCode());
        assertEquals(500, api("GET", "/api/v1/operations-test-failure", employee, "").statusCode());
        assertEquals(204, api("POST", "/logout", employee, "").statusCode());

        var fields = Map.ofEntries(
                Map.entry("LOGIN_SUCCESS", List.of("userId")), Map.entry("LOGOUT", List.of("userId")),
                Map.entry("LOGIN_FAILURE", List.of("username")), Map.entry("HTTP_REQUEST", List.of("method", "path", "status", "durationMs")),
                Map.entry("BOOKING_CREATED", List.of("bookingId", "roomId", "actorId")),
                Map.entry("BOOKING_CANCELLED", List.of("bookingId", "actorId", "authorId")),
                Map.entry("ROOM_CREATED", List.of("roomId", "actorId", "action")), Map.entry("ROOM_UPDATED", List.of("roomId", "actorId", "action")),
                Map.entry("ROOM_ACTIVATED", List.of("roomId", "actorId", "action")), Map.entry("ROOM_DEACTIVATED", List.of("roomId", "actorId", "action")),
                Map.entry("USER_CREATED", List.of("targetUserId", "actorId")), Map.entry("USER_DISABLED", List.of("targetUserId", "actorId")),
                Map.entry("USER_ENABLED", List.of("targetUserId", "actorId")), Map.entry("USER_PASSWORD_RESET", List.of("targetUserId", "actorId")),
                Map.entry("USER_ROLE_CHANGED", List.of("targetUserId", "actorId", "oldRole", "newRole")));
        for (var spec : fields.entrySet()) {
            var event = capture.events.stream().filter(item -> spec.getKey().equals(values(item).get("event"))).findFirst().orElseThrow(() -> new AssertionError(spec.getKey()));
            assertTrue(values(event).keySet().containsAll(spec.getValue()), spec.getKey());
            assertNotNull(event.getMDCPropertyMap().get("requestId"), spec.getKey());
            if (spec.getKey().equals("LOGIN_FAILURE")) { assertEquals(Level.WARN, event.getLevel()); assertEquals(64, values(event).get("username").toString().length()); }
        }
        var errors = capture.events.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
        assertEquals(1, errors.size()); assertNotNull(errors.getFirst().getThrowableProxy());
        var conflict = capture.events.stream().filter(event -> "SLOT_TAKEN".equals(values(event).get("code"))).toList();
        assertEquals(1, conflict.size()); assertEquals(Level.INFO, conflict.getFirst().getLevel()); assertNull(conflict.getFirst().getThrowableProxy());
        String output = capture.events.stream().map(event -> event.getFormattedMessage() + values(event) + event.getMDCPropertyMap())
                .collect(java.util.stream.Collectors.joining("\n"));
        for (String marker : List.of("MarkerLoginSecret", "MarkerPrivateTitle", "MarkerPrivateDisplay", "MarkerPassword-123", "MarkerReset-123", employee.cookie().split("=", 2)[1], employee.token()))
            assertFalse(output.contains(marker), "Sensitive field leaked to logs");
    }

    @Test @Tag("TS-21") @Tag("OPS-06") @Tag("TS-20")
    void managementPortExposesOnlyHealthAndMetricsAndReadinessTracksDatabase() throws Exception {
        var ready = management("/actuator/health/readiness");
        assertEquals(200, ready.statusCode(), ready.body());
        assertEquals("UP", json(ready).get("status").asString());
        assertFalse(ready.body().contains("components"));
        assertEquals(200, management("/actuator/metrics/jvm.memory.used").statusCode());
        assertNotEquals(200, management("/actuator/env").statusCode());
        assertNotEquals(200, api("GET", "/actuator/metrics", null, "").statusCode());
        var container = PostgresContainerHolder.getInstance();
        var docker = container.getDockerClient();
        try {
            docker.pauseContainerCmd(container.getContainerId()).exec();
            assertEquals(503, management("/actuator/health/readiness").statusCode());
            assertEquals(200, management("/actuator/health/liveness").statusCode());
        } finally {
            docker.unpauseContainerCmd(container.getContainerId()).exec();
        }
        assertEquals(200, management("/actuator/health/readiness").statusCode());
    }
    private HttpResponse<String> management(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + environment.getProperty("local.management.port") + path))
                .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private static Map<String, Object> values(ILoggingEvent event) {
        var result = new LinkedHashMap<String, Object>();
        if (event.getKeyValuePairs() != null) event.getKeyValuePairs().forEach(pair -> result.put(pair.key, pair.value));
        return result;
    }
}

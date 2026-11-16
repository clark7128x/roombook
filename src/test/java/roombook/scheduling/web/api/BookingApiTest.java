package roombook.scheduling.web.api;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import roombook.testsupport.*;
import roombook.platform.ErrorLogging;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.*;

class BookingApiTest extends HttpTestSupport {
    private String input(long room, String start, String end) {
        return mapper.writeValueAsString(Map.of("roomId", room, "title", "Зустріч", "startAt", start,
                "endAt", end, "authorId", otherId));
    }
    @Test @Tag("TS-08") @Tag("EP-08") @Tag("EP-09") @Tag("EP-10") @Tag("FR-09") @Tag("FR-17") @Tag("FR-18")
    void lifecycleUsesSessionAuthorAndMakesCancellationIdempotent() throws Exception {
        long room = TestDataBuilder.room().name("Бронювання").insert();
        var created = api("POST", "/api/v1/bookings", employee, input(room, "2026-10-27T10:00", "2026-10-27T11:00"));
        assertEquals(201, created.statusCode(), created.body());
        var booking = json(created);
        assertEquals(employeeId, booking.get("author").get("id").asLong());
        assertEquals("2026-10-27T10:00:00", booking.get("startAt").asString());
        assertTrue(booking.get("createdAt").asString().endsWith("Z"));
        assertTrue(booking.get("cancellable").asBoolean());
        String path = created.headers().firstValue("Location").orElseThrow();
        assertEquals("/api/v1/bookings/" + booking.get("id").asLong(), path);
        assertFalse(json(api("GET", path, other, "")).get("cancellable").asBoolean());
        problem(api("POST", path + "/cancel", other, ""), 403, "NOT_PERMITTED");
        for (String method : new String[]{"PUT", "PATCH", "DELETE"})
            problem(api(method, path, employee, "{}"), 405, "UNSUPPORTED_REQUEST");
        var cancelled = api("POST", path + "/cancel", employee, "");
        assertEquals(200, cancelled.statusCode());
        assertEquals("CANCELLED", json(cancelled).get("status").asString());
        assertTrue(json(cancelled).get("phase").isNull());
        assertFalse(json(cancelled).get("cancellable").asBoolean());
        assertEquals(json(cancelled), json(api("POST", path + "/cancel", admin, "")));
        InvariantAudit.assertAllInvariantsHold();
    }

    @Test @Tag("TS-08") @Tag("EP-11") @Tag("EP-12")
    void listsUseStablePaginationFiltersAndDerivedPhase() throws Exception {
        long room = TestDataBuilder.room().name("Сторінки").insert();
        for (int hour = 10; hour < 13; hour++) {
            var response = api("POST", "/api/v1/bookings", employee,
                    input(room, "2026-10-27T" + hour + ":00", "2026-10-27T" + (hour + 1) + ":00"));
            assertEquals(201, response.statusCode(), response.body());
        }
        var first = json(api("GET", "/api/v1/me/bookings?size=2&order=asc", employee, ""));
        assertEquals(2, first.get("items").size());
        assertTrue(first.get("hasNext").asBoolean());
        var second = json(api("GET", "/api/v1/me/bookings?size=2&order=asc&page=1", employee, ""));
        assertEquals(1, second.get("items").size());
        assertFalse(second.get("hasNext").asBoolean());
        assertTrue(first.get("items").get(1).get("id").asLong() < second.get("items").get(0).get("id").asLong());
        assertEquals(0, json(api("GET", "/api/v1/me/bookings?authorId=" + employeeId, other, "")).get("items").size());
        assertEquals(3, json(api("GET", "/api/v1/bookings?roomId=" + room + "&authorId=" + employeeId, admin, "")).get("items").size());
        for (String query : new String[]{"size=0", "size=101", "page=-1", "order=wrong", "status=wrong", "dateFrom=2101-01-01"})
            problem(api("GET", "/api/v1/me/bookings?" + query, employee, ""), 400, "INVALID_INPUT");
        clock.setInstant(Instant.parse("2026-10-27T12:00:00Z"));
        var finished = json(api("GET", "/api/v1/me/bookings", employee, "")).get("items").get(0);
        assertEquals("FINISHED", finished.get("phase").asString());
        assertFalse(finished.get("cancellable").asBoolean());
    }

    @Test @Tag("TS-09") @Tag("TS-08") @Tag("TR-01")
    void temporalInputsRejectOffsetsAndReturnContractSubcodes() throws Exception {
        long room = TestDataBuilder.room().name("Час").insert();
        for (String start : new String[]{"2026-10-27T10:00Z", "2026-10-27T10:00+03:00", "1999-10-27T10:00"}) {
            var response = api("POST", "/api/v1/bookings", employee, input(room, start, "2026-10-27T11:00"));
            problem(response, 400, "INVALID_INPUT");
            assertEquals("startAt", json(response).get("errors").get(0).get("field").asString());
        }
        for (var row : new String[][]{{"07:45", "08:15", "OUTSIDE_WORK_HOURS"}, {"10:07", "10:22", "NOT_ON_GRID"},
                {"10:00", "14:15", "BAD_DURATION"}, {"10:00", "10:00", "INTERVAL_SHAPE"}}) {
            var response = api("POST", "/api/v1/bookings", employee, input(room, "2026-10-27T" + row[0], "2026-10-27T" + row[1]));
            problem(response, 422, "INVALID_TIME");
            assertEquals(row[2], json(response).get("rule").asString());
        }
    }

    @Test @Tag("CS-01") @Tag("TS-09") @Tag("INV-08")
    void simultaneousDuplicateRequestsProduceOneCreationAndOneConflict() throws Exception {
        long room = TestDataBuilder.room().name("Гонка HTTP").insert();
        String body = input(room, "2026-10-27T10:00", "2026-10-27T11:00");
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<java.net.http.HttpResponse<String>> action = () -> {
                ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
                return api("POST", "/api/v1/bookings", employee, body);
            };
            var first = pool.submit(action); var second = pool.submit(action);
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            var results = java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertEquals(java.util.List.of(201, 409), results.stream().map(java.net.http.HttpResponse::statusCode).sorted().toList());
            problem(results.stream().filter(response -> response.statusCode() == 409).findFirst().orElseThrow(), 409, "SLOT_TAKEN");
        }
        InvariantAudit.assertAllInvariantsHold();
    }

    @Test @Tag("CS-14") @Tag("OPS-01") @Tag("TS-09")
    void heldRoomLockReturnsSanitized500AndPoolRecovers() throws Exception {
        long room = TestDataBuilder.room().name("Блокування HTTP").insert();
        String body = input(room, "2026-10-27T10:00", "2026-10-27T11:00");
        Logger logger = (Logger) LoggerFactory.getLogger(ErrorLogging.class);
        var capture = new ListAppender<ILoggingEvent>(); capture.start(); logger.addAppender(capture);
        try {
            try (var connection = PostgresContainerHolder.getOwnerConnection()) {
                connection.setAutoCommit(false);
                try (var statement = connection.prepareStatement("SELECT id FROM room WHERE id = ? FOR UPDATE")) {
                    statement.setLong(1, room); statement.executeQuery().close();
                    var response = api("POST", "/api/v1/bookings", employee, body);
                    problem(response, 500, "INTERNAL_ERROR");
                    assertFalse(response.body().contains("55P03"));
                    assertFalse(response.body().contains("SELECT"));
                } finally { connection.rollback(); }
            }
            assertEquals(1, capture.list.stream().filter(event -> event.getFormattedMessage().contains("LOCK_TIMEOUT")).count());
            assertEquals(201, api("POST", "/api/v1/bookings", employee, body).statusCode());
        } finally { logger.detachAppender(capture); capture.stop(); }
        InvariantAudit.assertAllInvariantsHold();
    }
}

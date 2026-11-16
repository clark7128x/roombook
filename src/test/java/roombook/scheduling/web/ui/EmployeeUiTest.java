package roombook.scheduling.web.ui;

import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.*;
import roombook.testsupport.*;
import static org.junit.jupiter.api.Assertions.*;

class EmployeeUiTest extends HttpTestSupport {
    @Test @Tag("A-72") @Tag("D-29") @Tag("PG-01") @Tag("PG-02") @Tag("PG-03") @Tag("PG-04") @Tag("PG-05")
    void employeePagesRenderRoomsAndOnlyOneShell() throws Exception {
        var root = api("GET", "/", employee, "");
        assertEquals(302, root.statusCode());
        assertTrue(root.headers().firstValue("Location").orElseThrow().endsWith("/occupancy"));
        long room = TestDataBuilder.room().name("<Studio>").insert();
        for (String path : new String[]{"/occupancy", "/rooms", "/bookings/new?roomId=" + room, "/bookings/mine"}) {
            var response = api("GET", path, employee, "");
            assertEquals(200, response.statusCode(), path + response.body());
            assertEquals(1, response.body().split("<html", -1).length - 1, path);
            assertEquals(1, response.body().split("id=\"main\"", -1).length - 1, path);
            assertEquals(1, response.body().split("id=\"alerts\"", -1).length - 1, path);
            assertFalse(response.body().contains("<Studio>"));
            if (!path.endsWith("mine")) assertTrue(response.body().contains("&lt;Studio&gt;"), path);
        }
        var form = api("GET", "/bookings/new?start=10:07", employee, "");
        assertTrue(form.body().contains("value=\"09:15\" selected=\"selected\""), form.body());
        assertFalse(form.body().contains("value=\"10:07\""));
        clock.setInstant(java.time.Instant.parse("2026-10-27T19:00:00Z"));
        var late = api("GET", "/bookings/new", employee, "");
        assertEquals(200, late.statusCode(), late.body());
        assertTrue(late.body().contains("Робочий день завершився"));
    }

    @Test @Tag("A-67") @Tag("HX-01") @Tag("HX-02") @Tag("HX-03") @Tag("FR-12")
    void createConflictFlashAndCancelRenderRealFragments() throws Exception {
        long room = TestDataBuilder.room().insert();
        String body = "roomId=" + room + "&date=2026-10-27&start=10:00&end=11:00&title=" + query("<Review>");
        var created = hx("/bookings", body);
        assertEquals(200, created.statusCode(), created.body());
        assertEquals("/occupancy?date=2026-10-27", created.headers().firstValue("HX-Redirect").orElseThrow());
        var flashed = api("GET", "/occupancy", employee, "");
        assertEquals(200, flashed.statusCode(), flashed.body());
        assertTrue(flashed.body().contains("Бронювання створено:"));
        assertFalse(api("GET", "/occupancy", employee, "").body().contains("Бронювання створено:"));
        var conflict = hx("/bookings", body);
        assertEquals(409, conflict.statusCode(), conflict.body());
        assertTrue(conflict.body().contains("hx-swap-oob=\"innerHTML\""));
        assertTrue(conflict.body().contains("value=\"&lt;Review&gt;\""));
        assertTrue(conflict.body().contains("role=\"alert\""));
        var mine = api("GET", "/bookings/mine", employee, "");
        assertEquals(200, mine.statusCode(), mine.body());
        long id = json(api("GET", "/api/v1/me/bookings", employee, "")).get("items").get(0).get("id").asLong();
        var cancelled = hx("/bookings/" + id + "/cancel", "");
        assertEquals(200, cancelled.statusCode(), cancelled.body());
        assertTrue(cancelled.body().contains("Скасовано"));
        assertFalse(cancelled.body().contains("<form"));
        InvariantAudit.assertAllInvariantsHold();
    }

    @Test @Tag("HX-01") @Tag("FR-15")
    void inactiveRoomAndInvalidFieldsPreserveFormWithoutRenderingFailure() throws Exception {
        long room = TestDataBuilder.room().active(false).insert();
        TestDataBuilder.room().name("Active").insert();
        var inactive = hx("/bookings", "roomId=" + room + "&date=2026-10-27&start=10:00&end=11:00&title=Kept");
        assertEquals(409, inactive.statusCode(), inactive.body());
        assertTrue(inactive.body().contains("Кімната недоступна"));
        var invalid = hx("/bookings", "roomId=bad&date=2026-10-27&start=10:00&end=11:00&title=Kept");
        assertEquals(400, invalid.statusCode(), invalid.body());
        assertTrue(invalid.body().contains("value=\"Kept\""));
    }

    private HttpResponse<String> hx(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + environment.getProperty("local.server.port") + path))
                .header("Cookie", employee.cookie()).header("X-CSRF-TOKEN", employee.token()).header("HX-Request", "true")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}


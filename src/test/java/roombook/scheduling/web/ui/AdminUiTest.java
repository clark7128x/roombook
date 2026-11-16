package roombook.scheduling.web.ui;

import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.*;
import roombook.testsupport.*;
import static org.junit.jupiter.api.Assertions.*;

class AdminUiTest extends HttpTestSupport {
    @Test @Tag("PG-06") @Tag("PG-07") @Tag("PG-08") @Tag("PG-09") @Tag("PG-10") @Tag("PG-11") @Tag("PG-12")
    void adminPagesRenderAndRejectMalformedFilters() throws Exception {
        long room = TestDataBuilder.room().equipment("<Screen>").insert();
        long booking = TestDataBuilder.booking().roomId(room).authorId(employeeId).title("<Meeting>")
                .startAt(java.time.LocalDateTime.parse("2026-10-27T10:00"))
                .endAt(java.time.LocalDateTime.parse("2026-10-27T11:00")).insert();
        for (String path : new String[]{"/admin/bookings", "/admin/rooms", "/admin/rooms/new", "/admin/rooms/" + room + "/edit",
                "/admin/users", "/admin/users/new", "/admin/users/" + employeeId}) {
            var result = api("GET", path, admin, "");
            assertEquals(200, result.statusCode(), path + result.body());
            assertEquals(1, result.body().split("id=\"main\"", -1).length - 1);
            assertFalse(result.body().contains("<Screen>"));
        }
        assertEquals(400, api("GET", "/admin/bookings?dateFrom=bad", admin, "").statusCode());
        assertEquals(400, api("GET", "/admin/bookings?status=bad", admin, "").statusCode());
        var cancelled = hx("/bookings/" + booking + "/cancel", "adminView=true");
        assertEquals(200, cancelled.statusCode(), cancelled.body());
        assertTrue(cancelled.body().contains("Працівник"));
        assertTrue(cancelled.body().contains("&lt;Meeting&gt;"));
    }

    @Test @Tag("HX-04") @Tag("HX-05")
    void roomsPreserveInvalidFieldsAndSwapLifecycleRows() throws Exception {
        var invalid = hx("/admin/rooms", "name=Kept&floor=bad&capacity=5");
        assertEquals(400, invalid.statusCode(), invalid.body());
        assertTrue(invalid.body().contains("id=\"room-form\""));
        assertTrue(invalid.body().contains("value=\"Kept\""));
        assertFalse(invalid.body().contains("<html"));
        var created = hx("/admin/rooms", "name=Studio&floor=1&capacity=5");
        assertEquals(200, created.statusCode(), created.body());
        assertEquals("/admin/rooms", created.headers().firstValue("HX-Redirect").orElseThrow());
        long id = json(api("GET", "/api/v1/rooms", admin, "")).get(0).get("id").asLong();
        var inactive = hx("/admin/rooms/" + id + "/deactivate", "");
        assertEquals(200, inactive.statusCode(), inactive.body());
        assertTrue(inactive.body().contains("Неактивна"));
        assertEquals(200, hx("/admin/rooms/" + id + "/activate", "").statusCode());
        assertEquals(200, hx("/admin/rooms/" + id, "name=Renamed&floor=2&capacity=8").statusCode());
        assertEquals(409, hx("/admin/rooms", "name=Renamed&floor=2&capacity=8").statusCode());
    }

    @Test @Tag("HX-06") @Tag("HX-07") @Tag("HX-08") @Tag("HX-09") @Tag("SEC-03")
    void usersValidateWithoutPasswordsAndProtectLastAdmin() throws Exception {
        var invalid = hx("/admin/users", "username=employee&displayName=Name&role=EMPLOYEE&password=Private-123456");
        assertEquals(409, invalid.statusCode(), invalid.body());
        assertTrue(invalid.body().contains("id=\"user-form\""));
        assertFalse(invalid.body().contains("Private-123456"));
        var created = hx("/admin/users", "username=newuser&displayName=Name&role=EMPLOYEE&password=Private-123456");
        assertEquals(200, created.statusCode(), created.body());
        assertEquals("/admin/users", created.headers().firstValue("HX-Redirect").orElseThrow());
        var reset = hx("/admin/users/" + employeeId + "/password", "password=New-Password-123");
        assertEquals(200, reset.statusCode(), reset.body());
        assertTrue(reset.body().contains("Пароль змінено"));
        assertTrue(reset.body().contains("id=\"password-form\""));
        assertFalse(reset.body().contains("New-Password-123"));
        var weak = hx("/admin/users/" + employeeId + "/password", "password=x");
        assertEquals(400, weak.statusCode(), weak.body());
        assertTrue(weak.body().contains("role=\"alert\""));
        for (String action : new String[]{"disable", "role"}) {
            var protectedAdmin = hx("/admin/users/" + adminId + "/" + action, "role=EMPLOYEE");
            assertEquals(409, protectedAdmin.statusCode(), protectedAdmin.body());
            assertEquals("#alerts", protectedAdmin.headers().firstValue("HX-Retarget").orElseThrow());
        }
        assertEquals(200, hx("/admin/users/" + otherId + "/disable", "").statusCode());
        assertEquals(200, hx("/admin/users/" + otherId + "/enable", "").statusCode());
        var role = hx("/admin/users/" + otherId + "/role", "role=ADMIN");
        assertEquals(200, role.statusCode(), role.body());
        assertTrue(role.body().contains("id=\"user-panel\""));
        InvariantAudit.assertAllInvariantsHold();
    }
    private HttpResponse<String> hx(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + environment.getProperty("local.server.port") + path))
                .header("Cookie", admin.cookie()).header("X-CSRF-TOKEN", admin.token()).header("HX-Request", "true")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
}

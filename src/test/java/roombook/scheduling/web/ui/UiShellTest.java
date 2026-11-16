package roombook.scheduling.web.ui;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import roombook.testsupport.HttpTestSupport;
import roombook.platform.UiFormat;
import roombook.kernel.OfficeClock;
import static org.junit.jupiter.api.Assertions.*;

@Import(UiShellTest.Routes.class)
class UiShellTest extends HttpTestSupport {
    @TestConfiguration static class Routes { @Bean ProbeController uiProbeController() { return new ProbeController(); } }
    @Controller static class ProbeController {
        @GetMapping("/ui-shell-failure") String failure() { throw new IllegalStateException("private-error-marker"); }
        @GetMapping("/ui-shell-binding") String binding(@RequestParam int count) { return "login"; }
    }
    @Test @Tag("TS-11") @Tag("PG-13") @Tag("SEC-12")
    void loginHasLocalAssetsCsrfAndConsistentErrorCopy() throws Exception {
        var login = api("GET", "/login?error", null, "");
        assertEquals(200, login.statusCode(), login.body());
        assertTrue(login.body().contains("Не вдалося увійти. Перевірте логін і пароль або зверніться до адміністратора."));
        assertTrue(login.body().contains("До змісту"));
        assertTrue(login.body().contains("name=\"_csrf\""));
        assertTrue(login.body().contains("hx-headers="));
        assertTrue(login.body().contains("/js/htmx.min.js"));
        assertTrue(login.body().contains("includeIndicatorStyles"));
        assertTrue(login.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
        assertTrue(api("GET", "/login?logout", null, "").body().contains("Ви вийшли з системи."));
        var css = api("GET", "/css/tokens.css", null, "");
        assertEquals(200, css.statusCode());
        assertTrue(css.headers().firstValue("Cache-Control").orElseThrow().contains("max-age=3600"));
    }
    @Test @Tag("TS-11") @Tag("SEC-14") @Tag("OPS-03")
    void errorPagesHaveRoleAwareShellAndRequestIdWithoutExceptionDetails() throws Exception {
        var forbidden = api("GET", "/admin/users", employee, "");
        assertEquals(403, forbidden.statusCode(), forbidden.body());
        assertTrue(forbidden.body().contains("Немає доступу"));
        assertTrue(forbidden.body().contains("Мої бронювання"));
        assertFalse(forbidden.body().contains("href=\"/admin/users\""));
        for (var item : new Object[][]{{"/missing-ui-page", 404}, {"/ui-shell-failure", 500}, {"/ui-shell-binding?count=bad", 400}}) {
            var response = api("GET", (String) item[0], admin, "");
            assertEquals(item[1], response.statusCode(), response.body());
            assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("text/html"));
            assertTrue(response.body().contains("href=\"/admin/users\""));
            assertTrue(response.body().contains("name=\"_csrf\""));
            assertTrue(response.body().contains(response.headers().firstValue("X-Request-Id").orElseThrow()));
            assertFalse(response.body().contains("private-error-marker"));
        }
    }
    @Test @Tag("TS-12") @Tag("HX-10")
    void htmxErrorsRenderAlertFragmentAndAnonymousRequestsRedirectWithoutBody() throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + environment.getProperty("local.server.port") + "/ui-shell-failure"))
                .header("HX-Request", "true");
        var anonymous = client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(401, anonymous.statusCode()); assertEquals("", anonymous.body());
        assertEquals("/login", anonymous.headers().firstValue("HX-Redirect").orElseThrow());
        var failure = client.send(builder.header("Cookie", employee.cookie()).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(500, failure.statusCode(), failure.body());
        assertEquals("#alerts", failure.headers().firstValue("HX-Retarget").orElseThrow());
        assertEquals("innerHTML", failure.headers().firstValue("HX-Reswap").orElseThrow());
        assertTrue(failure.body().contains("role=\"alert\"")); assertFalse(failure.body().contains("<html"));
    }
    @Test @Tag("TR-02") @Tag("UI-12") @Tag("A-68")
    void auditFormattingUsesOfficeZoneIndependentlyOfJvmZone() {
        var formatter = new UiFormat(new OfficeClock(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), ZoneId.of("Europe/Kyiv")));
        assertEquals("27.10.2026 10:00", formatter.audit(Instant.parse("2026-10-27T08:00:00Z")));
        assertEquals("10:00", formatter.time(LocalDateTime.parse("2026-10-27T10:00")));
        assertEquals("27.10.2026", formatter.date(LocalDate.parse("2026-10-27")));
        assertEquals("вівторок, 27.10.2026", formatter.dateWithDay(LocalDate.parse("2026-10-27")));
        assertEquals("вт 27.10", formatter.dateShortWithDay(LocalDateTime.parse("2026-10-27T10:00")));
    }
}

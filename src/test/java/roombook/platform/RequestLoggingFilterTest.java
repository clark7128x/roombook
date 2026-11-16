package roombook.platform;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("NFR-01")
@Tag("OPS-03")
class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();
    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
        listAppender.stop();
    }

    @Test
    @DisplayName("NFR-01, OPS-03: Запит логується на рівні INFO з методом, шляхом без query, статусом та durationMs")
    void dynamicRequestLoggedWithDetails() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/bookings");
        request.setQueryString("date=2026-10-25&room=1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(201);

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(1, listAppender.list.size(), "Має бути записано рівно одну подію логу");
        ILoggingEvent event = listAppender.list.get(0);
        assertEquals(Level.INFO, event.getLevel());

        String message = event.getFormattedMessage();
        assertTrue(message.contains("POST"), "Має містити метод POST: " + message);
        assertTrue(message.contains("/api/v1/bookings"), "Має містити шлях без query: " + message);
        assertFalse(message.contains("date=2026-10-25"), "Не повинен містити query string: " + message);
        assertTrue(message.contains("201"), "Має містити статус відповіді: " + message);
        assertTrue(message.contains("durationMs"), "Має містити тривалість durationMs: " + message);
    }

    @Test
    @DisplayName("NFR-01: Статичні ресурси (css, js, favicon) не потрапляють у журнал")
    void staticResourcesNotLogged() throws Exception {
        String[] staticPaths = {
                "/css/app.css",
                "/js/htmx.min.js",
                "/images/logo.png",
                "/favicon.ico",
                "/webjars/bootstrap/css/bootstrap.min.css"
        };

        for (String staticPath : staticPaths) {
            MockHttpServletRequest req = new MockHttpServletRequest("GET", staticPath);
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
        }

        assertTrue(listAppender.list.isEmpty(), "Для статичних ресурсів журнал має бути порожнім");
    }
}

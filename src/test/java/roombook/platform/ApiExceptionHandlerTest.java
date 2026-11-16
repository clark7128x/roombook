package roombook.platform;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import roombook.kernel.*;
import tools.jackson.databind.json.JsonMapper;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiExceptionHandlerTest {
    interface Probe { Map<String, Object> read(); }
    enum Mode { ACTIVE }
    record Input(Integer count, Mode mode) {}

    @RestController
    static class Controller {
        private final Probe probe;
        Controller(Probe probe) { this.probe = probe; }
        @GetMapping(value = "/api/v1/probe", produces = "application/json")
        Map<String, Object> read() { return probe.read(); }
        @GetMapping("/api/v1/number")
        Map<String, Object> number(@RequestParam int size) { return Map.of("size", size); }
        @PostMapping(value = "/api/v1/body", consumes = "application/json", produces = "application/json")
        Input body(@RequestBody Input body) { return body; }
    }

    private Probe probe;
    private MockMvc mvc;
    private final Logger logger = (Logger) LoggerFactory.getLogger(ErrorLogging.class);
    private ListAppender<ILoggingEvent> events;

    @BeforeEach
    void setup() {
        probe = mock(Probe.class);
        ErrorCatalog catalog = new ErrorCatalog();
        var security = new SecurityErrorHandler(catalog, new ClientClassifier(), JsonMapper.builder().build());
        mvc = MockMvcBuilders.standaloneSetup(new Controller(probe))
                .setControllerAdvice(new ApiExceptionHandler(catalog, new ErrorLogging(), security))
                .addFilters(new RequestIdFilter()).build();
        events = new ListAppender<>();
        events.start();
        logger.addAppender(events);
    }

    @AfterEach
    void cleanup() { logger.detachAppender(events); events.stop(); }

    @Test
    @Tag("TS-09")
    void allReasonsAreSerializedWithTopLevelCodeAndSafeDetails() throws Exception {
        var catalog = new ErrorCatalog();
        for (Reason reason : Reason.values()) {
            reset(probe);
            when(probe.read()).thenThrow(new DomainException(reason));
            mvc.perform(get("/api/v1/probe")).andExpect(status().is(catalog.lookup(reason).status().value()))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value(reason.name()))
                    .andExpect(jsonPath("$.title").isNotEmpty())
                    .andExpect(jsonPath("$.type").value("about:blank"))
                    .andExpect(jsonPath("$.detail").isNotEmpty())
                    .andExpect(jsonPath("$.properties").doesNotExist());
        }
    }

    @Test
    @Tag("TS-09")
    void fieldsAndSixTimeSubcodesAreAtTopLevel() throws Exception {
        when(probe.read()).thenThrow(DomainException.invalidInput("title", FieldRule.LENGTH));
        mvc.perform(get("/api/v1/probe")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("title"))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty())
                .andExpect(jsonPath("$.rule").doesNotExist());
        for (TimeRule rule : TimeRule.values()) {
            reset(probe);
            when(probe.read()).thenThrow(DomainException.invalidTime(rule, Map.of("slotGrid", 15,
                    "workDayStart", "08:00", "workDayEnd", "20:00", "minDuration", 15, "maxDuration", 240, "horizonDays", 90)));
            mvc.perform(get("/api/v1/probe")).andExpect(status().is(422))
                    .andExpect(jsonPath("$.code").value("INVALID_TIME"))
                    .andExpect(jsonPath("$.rule").value(rule.name()));
        }
    }

    @Test
    @Tag("TS-09")
    void malformedJsonTypesAndMissingParametersAreInvalidInput() throws Exception {
        for (String json : List.of("{", "{\"count\":\"oops\"}", "{\"mode\":\"UNKNOWN\"}")) {
            mvc.perform(post("/api/v1/body").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }
        mvc.perform(get("/api/v1/number").param("size", "oops")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        mvc.perform(get("/api/v1/number")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
        mvc.perform(post("/api/v1/body").contentType(MediaType.APPLICATION_JSON).content("{\"count\":\"secret-input\"}"))
                .andExpect(jsonPath("$.errors[0].field").value("count"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-input"))));
    }

    @Test
    @Tag("TS-09")
    void unsupportedPathMethodAcceptAndContentTypeKeepTheirStatuses() throws Exception {
        mvc.perform(get("/api/v1/unknown")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_REQUEST"));
        mvc.perform(delete("/api/v1/probe")).andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "GET"))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_REQUEST"));
        mvc.perform(get("/api/v1/probe").accept(MediaType.APPLICATION_XML)).andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_REQUEST"));
        mvc.perform(post("/api/v1/body").contentType(MediaType.TEXT_PLAIN).content("bad"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("UNSUPPORTED_REQUEST"));
    }

    @Test
    @Tag("OPS-01")
    @Tag("OPS-02")
    void unexpectedFailuresAreSanitizedAndLoggedOnceWithClassificationAndStack() throws Exception {
        var failures = List.of(new RuntimeException("SELECT secret FROM app_user"),
                new RuntimeException(new SQLException("ck_booking_internal", "23514")),
                new RuntimeException(new SQLException("fk_booking_author", "23503")),
                new RuntimeException(new SQLException("lock details", "55P03")),
                new CannotGetJdbcConnectionException("pool secret", new SQLTransientConnectionException("Hikari timeout")));
        var classifications = List.of("UNEXPECTED", "UNEXPECTED", "UNEXPECTED", "LOCK_TIMEOUT", "POOL_TIMEOUT");
        for (int i = 0; i < failures.size(); i++) {
            reset(probe);
            events.list.clear();
            when(probe.read()).thenThrow(failures.get(i));
            String body = mvc.perform(get("/api/v1/probe").header("X-Request-Id", "error-test"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                    .andExpect(jsonPath("$.detail").value("Внутрішня помилка, спробуйте пізніше"))
                    .andReturn().getResponse().getContentAsString();
            for (String forbidden : List.of("SELECT", "ck_", "fk_", "SQLException", "RuntimeException", "stack", "Hikari", "secret")) {
                assertFalse(body.contains(forbidden), body);
            }
            assertEquals(1, events.list.size());
            var event = events.list.getFirst();
            assertEquals(Level.ERROR, event.getLevel());
            assertNotNull(event.getThrowableProxy());
            assertTrue(event.getFormattedMessage().contains(classifications.get(i)));
            assertTrue(event.getFormattedMessage().contains("error-test"));
        }
    }

    @Test
    @Tag("OPS-01")
    void slotConflictIsLoggedOnceAtInfoWithoutStackOrExceptionMessage() throws Exception {
        when(probe.read()).thenThrow(new DomainException(Reason.SLOT_TAKEN, "SENSITIVE", List.of(), Map.of("secret", "hidden")));
        mvc.perform(get("/api/v1/probe").header("X-Request-Id", "slot-test")).andExpect(status().isConflict());
        assertEquals(1, events.list.size());
        var event = events.list.getFirst();
        assertEquals(Level.INFO, event.getLevel());
        assertNull(event.getThrowableProxy());
        assertTrue(event.getFormattedMessage().contains("SLOT_TAKEN"));
        assertTrue(event.getFormattedMessage().contains("slot-test"));
        assertFalse(event.getFormattedMessage().contains("hidden"));
    }
}

package roombook.platform;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import roombook.kernel.*;
import roombook.scheduling.domain.BookingRules;
import roombook.scheduling.domain.BookingTimePolicy;
import java.time.*;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class ErrorCatalogTest {
    private final ErrorCatalog catalog = new ErrorCatalog();

    @Test
    @Tag("TS-09")
    @Tag("A-56")
    void everyEnumValueHasAnExplicitUkrainianCatalogEntry() {
        Stream.of(Reason.values(), TimeRule.values(), FieldRule.values(), TransportCode.values())
                .flatMap(Arrays::stream).forEach(key -> {
                    var entry = catalog.lookup(key);
                    assertNotNull(entry.status(), key.name());
                    assertFalse(entry.code().isBlank(), key.name());
                    assertTrue(entry.template().matches("(?s).*[А-Яа-яІіЇїЄєҐґ].*"), key.name());
                    assertTrue(entry.status().isError(), key.name());
                    if (key instanceof TimeRule) {
                        assertEquals(422, entry.status().value());
                        assertEquals("INVALID_TIME", entry.code());
                    } else if (key instanceof FieldRule) {
                        assertEquals(400, entry.status().value());
                        assertEquals("INVALID_INPUT", entry.code());
                    } else assertEquals(key.name(), entry.code());
                });
    }

    @Test
    @Tag("TS-09")
    void reasonAndTransportStatusesMatchContract() {
        int[] statuses = {400, 422, 404, 409, 409, 404, 403, 409, 409, 409, 409, 404, 409, 400};
        for (int i = 0; i < Reason.values().length; i++) {
            assertEquals(statuses[i], catalog.problem(Reason.values()[i]).getStatus());
        }
        assertEquals(401, catalog.problem(TransportCode.UNAUTHENTICATED).getStatus());
        assertEquals(403, catalog.problem(TransportCode.CSRF_INVALID).getStatus());
        assertEquals(500, catalog.problem(TransportCode.INTERNAL_ERROR).getStatus());
        for (int status : new int[]{404, 405, 406, 415}) {
            var problem = catalog.unsupported(status);
            assertEquals(status, problem.getStatus());
            assertEquals("UNSUPPORTED_REQUEST", problem.getProperties().get("code"));
            assertEquals("about:blank", problem.getType().toString());
        }
    }

    @Test
    @Tag("TS-09")
    @Tag("A-56")
    void timeMessagesUseExceptionParametersIncludingNonDefaultBusinessRules() {
        var policy = new BookingTimePolicy(new BookingRules(LocalTime.of(9, 0), LocalTime.of(18, 0),
                Duration.ofMinutes(30), Duration.ofMinutes(30), Duration.ofHours(2), 7));
        var start = LocalDateTime.of(2026, 11, 2, 8, 0);
        var exception = assertThrows(DomainException.class, () -> policy.validateForSearch(start, start.plusHours(1)));
        assertEquals("Бронювання можливе лише з 09:00 до 18:00", catalog.problem(exception).getDetail());
        Map<String, Object> parameters = Map.of("slotGrid", 30, "minDuration", 30, "maxDuration", 120,
                "workDayStart", "09:00", "workDayEnd", "18:00", "horizonDays", 7);
        for (TimeRule rule : TimeRule.values()) {
            var problem = catalog.problem(DomainException.invalidTime(rule, parameters));
            assertEquals(rule.name(), problem.getProperties().get("rule"));
            assertFalse(problem.getDetail().contains("{"));
        }
        assertTrue(catalog.problem(DomainException.invalidTime(TimeRule.NOT_ON_GRID, parameters)).getDetail().contains("30"));
        assertTrue(catalog.problem(DomainException.invalidTime(TimeRule.BAD_DURATION, parameters)).getDetail().contains("120"));
        assertTrue(catalog.problem(DomainException.invalidTime(TimeRule.TOO_FAR, parameters)).getDetail().contains("7"));
    }
}

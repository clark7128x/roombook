package roombook.platform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("OPS-03")
@Tag("NFR-01")
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    @DisplayName("OPS-03: Коректний, надто довгий і відсутній X-Request-Id дають три різні requestId, що збігаються в заголовку й MDC")
    void threeScenariosProduceThreeDistinctMatchingIds() throws Exception {
        // 1. Коректний заголовок X-Request-Id
        String validIncomingId = "proxy-req-abc-12345";
        MockHttpServletRequest req1 = new MockHttpServletRequest("GET", "/api/v1/test");
        req1.addHeader(RequestIdFilter.HEADER_NAME, validIncomingId);
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        AtomicReference<String> mdcCaptured1 = new AtomicReference<>();

        filter.doFilter(req1, res1, (request, response) -> {
            mdcCaptured1.set(MDC.get(RequestIdFilter.MDC_KEY));
        });

        String actualId1 = res1.getHeader(RequestIdFilter.HEADER_NAME);
        assertEquals(validIncomingId, actualId1, "Коректний вхідний ID має бути прийнятий без змін");
        assertEquals(actualId1, mdcCaptured1.get(), "Значення в заголовку відповіді має збігатися з MDC");

        // 2. Надто довгий заголовок (65 символів > ліміту в 64)
        String tooLongIncomingId = "a".repeat(65);
        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/api/v1/test");
        req2.addHeader(RequestIdFilter.HEADER_NAME, tooLongIncomingId);
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        AtomicReference<String> mdcCaptured2 = new AtomicReference<>();

        filter.doFilter(req2, res2, (request, response) -> {
            mdcCaptured2.set(MDC.get(RequestIdFilter.MDC_KEY));
        });

        String actualId2 = res2.getHeader(RequestIdFilter.HEADER_NAME);
        assertNotNull(actualId2);
        assertNotEquals(tooLongIncomingId, actualId2, "Надто довгий ID має бути відхилений і замінений згенерованим");
        assertTrue(actualId2.length() <= 64, "Згенерований ID має вміщуватися в 64 символи");
        assertEquals(actualId2, mdcCaptured2.get(), "Значення в заголовку відповіді має збігатися з MDC");

        // 3. Відсутній заголовок X-Request-Id
        MockHttpServletRequest req3 = new MockHttpServletRequest("GET", "/api/v1/test");
        MockHttpServletResponse res3 = new MockHttpServletResponse();
        AtomicReference<String> mdcCaptured3 = new AtomicReference<>();

        filter.doFilter(req3, res3, (request, response) -> {
            mdcCaptured3.set(MDC.get(RequestIdFilter.MDC_KEY));
        });

        String actualId3 = res3.getHeader(RequestIdFilter.HEADER_NAME);
        assertNotNull(actualId3);
        assertTrue(actualId3.length() <= 64);
        assertEquals(actualId3, mdcCaptured3.get(), "Значення в заголовку відповіді має збігатися з MDC");

        // Усі три ID мають бути різними між собою
        assertNotEquals(actualId1, actualId2, "ID 1 та ID 2 мають бути різними");
        assertNotEquals(actualId2, actualId3, "ID 2 та ID 3 мають бути різними");
        assertNotEquals(actualId1, actualId3, "ID 1 та ID 3 мають бути різними");
    }

    @Test
    @DisplayName("OPS-03: MDC ключ очищається після завершення запиту")
    void mdcKeyClearedAfterRequest() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/test");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertNull(MDC.get(RequestIdFilter.MDC_KEY), "MDC ключ requestId має бути видалений після запиту");
    }

    @Test
    @DisplayName("OPS-03: Заголовок із недозволеними символами відхиляється і генерується новий ID")
    void invalidCharactersInHeaderRejected() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/test");
        req.addHeader(RequestIdFilter.HEADER_NAME, "bad id with spaces!");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        String generatedId = res.getHeader(RequestIdFilter.HEADER_NAME);
        assertNotNull(generatedId);
        assertNotEquals("bad id with spaces!", generatedId);
    }
}

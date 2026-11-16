package roombook.platform;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SecurityErrorHandlerTest {
    private final ClientClassifier clients = new ClientClassifier();
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final SecurityErrorHandler handler = new SecurityErrorHandler(new ErrorCatalog(), clients, mapper);

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); }

    @Test
    @Tag("SEC-14")
    void classifierUsesApiPathBeforeHtmxAndRespectsContextBoundary() {
        var request = new MockHttpServletRequest("GET", "/office/api/v1/rooms");
        request.setContextPath("/office");
        request.addHeader("HX-Request", "true");
        assertEquals(ClientClassifier.Client.API, clients.classify(request));
        request.setRequestURI("/office/api/v10/rooms");
        assertEquals(ClientClassifier.Client.HTMX, clients.classify(request));
        request.removeHeader("HX-Request");
        request.addHeader("Accept", "application/json");
        assertEquals(ClientClassifier.Client.PAGE, clients.classify(request));
        request.addHeader("HX-Request", "false");
        assertEquals(ClientClassifier.Client.PAGE, clients.classify(request));
    }

    @Test
    @Tag("SEC-14")
    void missingAuthenticationUsesJsonHtmxRedirectOrPageRedirect() throws Exception {
        var api = new MockHttpServletRequest("GET", "/api/v1/rooms");
        api.addHeader("HX-Request", "true");
        var response = new MockHttpServletResponse();
        handler.commence(api, response, null);
        assertEquals(401, response.getStatus());
        assertEquals("application/problem+json;charset=UTF-8", response.getContentType());
        assertEquals("UNAUTHENTICATED", mapper.readTree(response.getContentAsString()).get("code").asString());
        assertNull(response.getHeader("Location"));
        assertNull(response.getHeader("HX-Redirect"));
        var htmx = new MockHttpServletRequest("GET", "/fragments/rooms");
        htmx.addHeader("HX-Request", "true");
        response = new MockHttpServletResponse();
        handler.commence(htmx, response, null);
        assertEquals(401, response.getStatus());
        assertEquals("/login", response.getHeader("HX-Redirect"));
        assertEquals(0, response.getContentAsByteArray().length);
        response = new MockHttpServletResponse();
        handler.commence(new MockHttpServletRequest("GET", "/rooms"), response, null);
        assertEquals(302, response.getStatus());
        assertEquals("/login", response.getRedirectedUrl());
    }

    @Test
    @Tag("SEC-14")
    void authenticatedDenialsAndCsrfUse403ForEachClient() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, AuthorityUtils.createAuthorityList("ROLE_EMPLOYEE")));
        var csrf = new InvalidCsrfTokenException(new DefaultCsrfToken("header", "param", "secret-token"), "wrong-token");
        for (AccessDeniedException exception : List.of(new AccessDeniedException("internal details"), csrf)) {
            var response = new MockHttpServletResponse();
            handler.handle(new MockHttpServletRequest("POST", "/api/v1/rooms"), response, exception);
            assertEquals(403, response.getStatus());
            assertEquals(exception == csrf ? "CSRF_INVALID" : "NOT_PERMITTED",
                    mapper.readTree(response.getContentAsString()).get("code").asString());
            assertFalse(response.getContentAsString().contains("token"));
            var request = new MockHttpServletRequest("POST", "/rooms");
            request.addHeader("HX-Request", "true");
            response = new MockHttpServletResponse();
            handler.handle(request, response, exception);
            assertEquals(403, response.getStatus());
            assertEquals("#alerts", response.getHeader("HX-Retarget"));
            assertEquals("innerHTML", response.getHeader("HX-Reswap"));
            assertTrue(response.getContentAsString().contains("role=\"alert\""));
            assertNull(response.getHeader("HX-Redirect"));
            request.removeHeader("HX-Request");
            response = new MockHttpServletResponse();
            handler.handle(request, response, exception);
            assertEquals(403, response.getStatus());
            assertTrue(response.getContentAsString().contains("<html lang=\"uk\">"));
        }
    }

    @Test
    @Tag("SEC-11")
    @Tag("SEC-14")
    void expiredSessionWithInvalidCsrfUsesUnauthenticatedResponse() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));
        var request = new MockHttpServletRequest("POST", "/rooms");
        request.addHeader("HX-Request", "true");
        var response = new MockHttpServletResponse();
        handler.handle(request, response,
                new InvalidCsrfTokenException(new DefaultCsrfToken("header", "param", "old"), "wrong"));
        assertEquals(401, response.getStatus());
        assertEquals("/login", response.getHeader("HX-Redirect"));
        assertEquals(0, response.getContentAsByteArray().length);
    }
}

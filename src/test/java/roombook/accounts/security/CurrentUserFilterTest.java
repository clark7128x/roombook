package roombook.accounts.security;

import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import roombook.accounts.application.CredentialsQueries;
import roombook.platform.*;
import tools.jackson.databind.json.JsonMapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrentUserFilterTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test @Tag("TS-17") @Tag("SEC-08")
    void databaseFailureFailsClosedAndStaticResourcesSkipTheLookup() throws Exception {
        var queries = mock(CredentialsQueries.class);
        when(queries.loadForSession(1)).thenThrow(new IllegalStateException("private database detail"));
        var errors = new SecurityErrorHandler(new ErrorCatalog(), new ClientClassifier(), JsonMapper.builder().build());
        var filter = new CurrentUserFilter(queries, errors, mock(ErrorLogging.class));
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new ActorPrincipal(1, "user"), null, List.of()));
        var request = new MockHttpServletRequest("GET", "/api/v1/rooms");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(500, response.getStatus());
        assertNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertFalse(response.getContentAsString().contains("private"));
        reset(queries);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new ActorPrincipal(1, "user"), null, List.of()));
        request = new MockHttpServletRequest("GET", "/css/base.css");
        request.setServletPath("/css/base.css");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        verifyNoInteractions(queries);
    }
}

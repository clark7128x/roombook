package roombook.platform;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import roombook.kernel.Reason;
import tools.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;

/** SEC-14 response adapters, shared by MVC and the security filter chain. */
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ErrorCatalog catalog;
    private final ClientClassifier clients;
    private final ObjectMapper mapper;
    private UiResponseRenderer uiRenderer;
    @org.springframework.beans.factory.annotation.Autowired
    void uiRenderer(UiResponseRenderer renderer) { this.uiRenderer = renderer; }

    public SecurityErrorHandler(ErrorCatalog catalog, ClientClassifier clients, ObjectMapper mapper) {
        this.catalog = catalog;
        this.clients = clients;
        this.mapper = mapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        if (response.isCommitted()) return;
        switch (clients.classify(request)) {
            case API -> writeProblem(response, catalog.problem(TransportCode.UNAUTHENTICATED));
            case HTMX -> {
                response.setStatus(401);
                response.setHeader("HX-Redirect", request.getContextPath() + "/login");
            }
            case PAGE -> response.sendRedirect(request.getContextPath() + "/login");
        }
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception) throws IOException {
        if (response.isCommitted()) return;
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        // Joining is deliberately anonymous, but still requires a valid session CSRF token.
        boolean publicJoinCsrf = exception instanceof CsrfException && "/join".equals(request.getServletPath());
        if (!publicJoinCsrf && !new AuthenticationTrustResolverImpl().isAuthenticated(authentication)) {
            commence(request, response, null);
            return;
        }
        ProblemDetail problem = exception instanceof CsrfException
                ? catalog.problem(TransportCode.CSRF_INVALID) : catalog.problem(Reason.NOT_PERMITTED);
        if (clients.classify(request) == ClientClassifier.Client.API) {
            writeProblem(response, problem);
        } else if (uiRenderer != null) {
            uiRenderer.problem(request, response, problem);
        } else {
            response.setStatus(403);
            response.setContentType("text/html;charset=UTF-8");
            String message = HtmlUtils.htmlEscape(problem.getDetail());
            if (clients.classify(request) == ClientClassifier.Client.HTMX) {
                response.setHeader("HX-Retarget", "#alerts");
                response.setHeader("HX-Reswap", "innerHTML");
                response.getWriter().write("<div role=\"alert\">" + message + "</div>");
            } else {
                response.getWriter().write("<!doctype html><html lang=\"uk\"><meta charset=\"UTF-8\">"
                        + "<title>Доступ заборонено</title><body><h1>" + message + "</h1></body></html>");
            }
        }
    }

    private void writeProblem(HttpServletResponse response, ProblemDetail problem) throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // Explicitly flatten extensions outside MVC, without relying on converter-specific mixins.
        var body = new LinkedHashMap<String, Object>();
        body.put("type", problem.getType().toString());
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        body.put("detail", problem.getDetail());
        if (problem.getProperties() != null) body.putAll(problem.getProperties());
        mapper.writeValue(response.getOutputStream(), body);
    }

    public void internalError(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (response.isCommitted()) return;
        var problem = catalog.problem(TransportCode.INTERNAL_ERROR);
        if (uiRenderer != null && clients.classify(request) != ClientClassifier.Client.API) uiRenderer.problem(request, response, problem);
        else writeProblem(response, problem);
    }
}

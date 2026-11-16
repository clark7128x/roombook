package roombook.accounts.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import roombook.accounts.application.CredentialsQueries;
import roombook.platform.ErrorLogging;
import roombook.platform.SecurityErrorHandler;
import java.io.IOException;

public class CurrentUserFilter extends OncePerRequestFilter {
    public static final String CURRENT_USER = "currentUser";
    private final CredentialsQueries queries;
    private final SecurityErrorHandler errors;
    private final ErrorLogging logging;

    public CurrentUserFilter(CredentialsQueries queries, SecurityErrorHandler errors, ErrorLogging logging) {
        this.queries = queries; this.errors = errors; this.logging = logging;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/fonts/")
                || path.equals("/icons.svg") || path.equals("/favicon.ico");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof ActorPrincipal principal) {
            try {
                var current = queries.loadForSession(principal.userId());
                if (current.isEmpty() || !current.get().enabled()) {
                    SecurityContextHolder.clearContext();
                    HttpSession session = request.getSession(false);
                    if (session != null) session.invalidate();
                } else {
                    var user = current.get();
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                            AuthorityUtils.createAuthorityList("ROLE_" + user.role().name())));
                    SecurityContextHolder.setContext(context);
                    request.setAttribute(CURRENT_USER, user);
                    MDC.put("userId", Long.toString(user.userId()));
                }
            } catch (RuntimeException exception) {
                SecurityContextHolder.clearContext();
                logging.unexpected(exception);
                errors.internalError(request, response);
                return;
            }
        }
        try {
            if ("GET".equals(request.getMethod()) && "/login".equals(request.getServletPath())
                    && request.getAttribute(CURRENT_USER) != null) {
                response.sendRedirect(request.getContextPath() + "/");
                return;
            }
            chain.doFilter(request, response);
        } finally {
            MDC.remove("userId");
        }
    }
}

package roombook.platform;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class UiModels {
    private final ObjectMapper mapper;
    public UiModels(ObjectMapper mapper) { this.mapper = mapper; }
    public Map<String, Object> common(HttpServletRequest request) {
        var model = new HashMap<String, Object>();
        model.put("currentUser", request.getAttribute("currentUser"));
        model.put("isAdmin", request.isUserInRole("ADMIN"));
        model.put("currentPath", request.getRequestURI());
        model.put("requestId", Objects.toString(MDC.get("requestId"), ""));
        model.put("fieldErrors", Map.of());
        var token = (CsrfToken) request.getAttribute("_csrf");
        model.put("_csrf", token);
        model.put("csrfHeaders", token == null ? "{}" : mapper.writeValueAsString(Map.of(token.getHeaderName(), token.getToken())));
        return model;
    }
}

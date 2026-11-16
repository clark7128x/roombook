package roombook.platform;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientClassifier {
    public enum Client { API, HTMX, PAGE }

    public Client classify(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/api/v1") || path.startsWith("/api/v1/")) return Client.API;
        if ("true".equalsIgnoreCase(request.getHeader("HX-Request"))) return Client.HTMX;
        return Client.PAGE;
    }
}

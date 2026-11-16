package roombook.accounts.web.api;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CsrfController {
    @GetMapping("/api/v1/csrf")
    public TokenResponse token(CsrfToken token) {
        return new TokenResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }
    public record TokenResponse(String headerName, String parameterName, String token) {}
}

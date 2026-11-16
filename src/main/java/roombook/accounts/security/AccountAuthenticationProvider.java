package roombook.accounts.security;

import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;

@Component
public class AccountAuthenticationProvider implements AuthenticationProvider {
    private final AccountUserDetailsService users;
    private final PasswordEncoder encoder;
    private final String dummyHash;

    public AccountAuthenticationProvider(AccountUserDetailsService users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
        this.dummyHash = encoder.encode("dummy-login-comparison-value");
    }

    @Override public Authentication authenticate(Authentication authentication) {
        String password = authentication.getCredentials() instanceof String value ? value : "";
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) throw badCredentials();
        AccountUserDetailsService.AccountDetails user;
        try {
            user = users.loadUserByUsername(authentication.getName());
        } catch (UsernameNotFoundException exception) {
            encoder.matches(password, dummyHash);
            throw badCredentials();
        }
        boolean matches = encoder.matches(password, user.getPassword());
        if (!matches || !user.isEnabled()) throw badCredentials();
        return UsernamePasswordAuthenticationToken.authenticated(
                new ActorPrincipal(user.userId(), user.getUsername()), null, user.getAuthorities());
    }

    private BadCredentialsException badCredentials() { return new BadCredentialsException("Invalid credentials"); }
    @Override public boolean supports(Class<?> type) { return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type); }
}

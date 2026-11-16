package roombook.accounts.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Component;
import roombook.accounts.application.CredentialsQueries;
import roombook.accounts.application.LoginCredentials;
import java.util.Collection;

@Component
public class AccountUserDetailsService implements UserDetailsService {
    private final CredentialsQueries queries;
    public AccountUserDetailsService(CredentialsQueries queries) { this.queries = queries; }

    @Override public AccountDetails loadUserByUsername(String username) {
        if (username == null || username.codePointCount(0, username.length()) > 64) {
            throw new UsernameNotFoundException("Invalid credentials");
        }
        return new AccountDetails(queries.loadForLogin(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials")));
    }

    public static final class AccountDetails implements UserDetails {
        private final LoginCredentials credentials;
        AccountDetails(LoginCredentials credentials) { this.credentials = credentials; }
        public long userId() { return credentials.userId(); }
        @Override public String getUsername() { return credentials.username(); }
        @Override public String getPassword() { return credentials.passwordHash(); }
        @Override public boolean isEnabled() { return credentials.enabled(); }
        @Override public Collection<? extends GrantedAuthority> getAuthorities() {
            return AuthorityUtils.createAuthorityList("ROLE_" + credentials.role().name());
        }
        @Override public String toString() { return "AccountDetails[credentials=REDACTED]"; }
    }
}

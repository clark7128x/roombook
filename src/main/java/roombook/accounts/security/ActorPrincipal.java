package roombook.accounts.security;

import java.io.Serializable;
import java.security.Principal;

/** Only these two values are retained in an authenticated session. */
public record ActorPrincipal(long userId, String username) implements Principal, Serializable {
    @Override public String getName() { return username; }
}

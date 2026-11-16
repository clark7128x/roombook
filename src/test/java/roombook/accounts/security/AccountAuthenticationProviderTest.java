package roombook.accounts.security;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import roombook.accounts.application.CredentialsQueries;
import roombook.accounts.application.LoginCredentials;
import roombook.kernel.Role;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountAuthenticationProviderTest {
    @Test @Tag("TS-17") @Tag("SEC-02") @Tag("SEC-03")
    void checksDisabledAndUnknownPasswordsButRetainsNoCredentialsInSuccessfulAuthentication() {
        var queries = mock(CredentialsQueries.class);
        var encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("dummy-hash");
        when(encoder.matches("password", "stored-hash")).thenReturn(true);
        var provider = new AccountAuthenticationProvider(new AccountUserDetailsService(queries), encoder);
        when(queries.loadForLogin("user")).thenReturn(Optional.of(new LoginCredentials(1, "User", "stored-hash", Role.EMPLOYEE, true)));
        var result = provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("user", "password"));
        assertEquals(new ActorPrincipal(1, "User"), result.getPrincipal());
        assertNull(result.getCredentials());
        assertEquals(2, ActorPrincipal.class.getRecordComponents().length);
        assertFalse(result.toString().contains("stored-hash"));
        when(queries.loadForLogin("disabled")).thenReturn(Optional.of(new LoginCredentials(2, "Disabled", "stored-hash", Role.EMPLOYEE, false)));
        assertThrows(BadCredentialsException.class, () -> provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("disabled", "password")));
        assertThrows(BadCredentialsException.class, () -> provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("missing", "password")));
        verify(encoder, times(2)).matches("password", "stored-hash");
        verify(encoder).matches("password", "dummy-hash");
    }
}

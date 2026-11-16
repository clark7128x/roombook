package roombook.accounts.application;

import java.time.Instant;
public record InvitationView(long id, String username, String displayName, Instant expiresAt, String state, boolean revocable) {}

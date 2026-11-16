package roombook.accounts.domain;

import jakarta.persistence.*;
import java.time.Instant;
import roombook.kernel.DomainException;
import roombook.kernel.Reason;

/** Stores only a digest of the bearer token. Invitations can create employees only. */
@Entity
@Table(name = "account_invitation")
public class AccountInvitation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 64) private String username;
    @Column(name = "username_key", nullable = false, length = 64) private String usernameKey;
    @Column(name = "display_name", nullable = false, length = 100) private String displayName;
    @Column(name = "token_hash", nullable = false, length = 64) private String tokenHash;
    @Column(name = "created_by", nullable = false) private long createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    protected AccountInvitation() {}
    public AccountInvitation(String username, String usernameKey, String displayName, String tokenHash,
                             long createdBy, Instant now, Instant expiresAt) {
        this.username = username; this.usernameKey = usernameKey; this.displayName = displayName;
        this.tokenHash = tokenHash; this.createdBy = createdBy; this.createdAt = now; this.expiresAt = expiresAt;
    }
    public boolean available(Instant now) { return consumedAt == null && revokedAt == null && now.isBefore(expiresAt); }
    public void consume(Instant now) {
        if (!available(now)) throw new DomainException(Reason.INVITATION_INVALID);
        consumedAt = now;
    }
    public void revoke(Instant now) { if (consumedAt == null && revokedAt == null) revokedAt = now; }
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getUsernameKey() { return usernameKey; }
    public String getDisplayName() { return displayName; }
    public long getCreatedBy() { return createdBy; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    @Override public String toString() { return "AccountInvitation[id=" + id + "]"; }
}

package roombook.accounts.application;

import java.time.Duration;
import java.time.Instant;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roombook.accounts.domain.AccountInvitation;
import roombook.accounts.domain.AppUser;
import roombook.accounts.persistence.AppUserRepository;
import roombook.accounts.persistence.InvitationRepository;
import roombook.kernel.*;

@Service @Transactional
public class InvitationService {
    private final InvitationRepository invitations;
    private final AppUserRepository users;
    private final OfficeClock clock;
    public InvitationService(InvitationRepository invitations, AppUserRepository users, OfficeClock clock) {
        this.invitations = invitations; this.users = users; this.clock = clock;
    }
    public record Issued(long id, String token, Instant expiresAt) {
        @Override public String toString() { return "Issued[id=" + id + ", token=redacted]"; }
    }
    public Issued issue(Actor actor, String username, String displayName) {
        actor.requireAdmin();
        // Reuse all account identity rules. This validation object is never persisted or used for authentication.
        var identity = new AppUser(username, displayName, "invitation-identity-validation", Role.EMPLOYEE);
        if (users.findByUsernameKey(identity.getUsernameKey()).isPresent()) throw new DomainException(Reason.USERNAME_TAKEN);
        String token = InvitationTokens.generate(); var now = clock.instant();
        var invitation = invitations.saveAndFlush(new AccountInvitation(identity.getUsername(), identity.getUsernameKey(),
            identity.getDisplayName(), InvitationTokens.digest(token), actor.userId(), now, now.plus(Duration.ofHours(48))));
        LoggerFactory.getLogger(InvitationService.class).atInfo().addKeyValue("event", "INVITATION_CREATED")
            .addKeyValue("invitationId", invitation.getId()).addKeyValue("actorId", actor.userId()).log("Invitation created");
        return new Issued(invitation.getId(), token, invitation.getExpiresAt());
    }
    public void revoke(Actor actor, long id) {
        actor.requireAdmin();
        var invitation = invitations.lockById(id).orElseThrow(() -> new DomainException(Reason.INVITATION_INVALID));
        invitation.revoke(clock.instant()); invitations.saveAndFlush(invitation);
        LoggerFactory.getLogger(InvitationService.class).atInfo().addKeyValue("event", "INVITATION_REVOKED")
            .addKeyValue("invitationId", id).addKeyValue("actorId", actor.userId()).log("Invitation revoked");
    }
}

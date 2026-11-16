package roombook.accounts.application;

import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import roombook.accounts.domain.AppUser;
import roombook.accounts.domain.PasswordPolicy;
import roombook.accounts.persistence.AccountsConstraints;
import roombook.accounts.persistence.AppUserRepository;
import roombook.accounts.persistence.InvitationRepository;
import roombook.kernel.*;

/** Public capability entry point: a valid invitation replaces an authenticated Actor. */
@Component @Transactional
public class InvitationRedemption {
    private final InvitationRepository invitations;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final OfficeClock clock;
    public InvitationRedemption(InvitationRepository invitations, AppUserRepository users, PasswordEncoder encoder, OfficeClock clock) {
        this.invitations = invitations; this.users = users; this.encoder = encoder; this.clock = clock;
    }
    public String accept(String token, String password, String confirmation) {
        var invitation = invitations.lockByTokenHash(InvitationTokens.digest(token))
            .orElseThrow(() -> new DomainException(Reason.INVITATION_INVALID));
        var now = clock.instant();
        if (!invitation.available(now)) throw new DomainException(Reason.INVITATION_INVALID);
        var issuer = users.lockById(invitation.getCreatedBy()).orElseThrow(() -> new DomainException(Reason.INVITATION_INVALID));
        if (!issuer.isEnabled() || issuer.getRole() != Role.ADMIN || users.findByUsernameKey(invitation.getUsernameKey()).isPresent())
            throw new DomainException(Reason.INVITATION_INVALID);
        PasswordPolicy.validate(password, invitation.getUsername());
        if (!password.equals(confirmation)) throw DomainException.invalidInput("confirmation", FieldRule.FORMAT);
        var user = new AppUser(invitation.getUsername(), invitation.getDisplayName(), encoder.encode(password), Role.EMPLOYEE);
        var saved = AccountsConstraints.execute(() -> users.saveAndFlush(user));
        // Recheck expiry after hashing and before consumption; any failure rolls back account creation as well.
        invitation.consume(clock.instant()); invitations.saveAndFlush(invitation);
        LoggerFactory.getLogger(InvitationRedemption.class).atInfo().addKeyValue("event", "INVITATION_ACCEPTED")
            .addKeyValue("invitationId", invitation.getId()).addKeyValue("targetUserId", saved.getId()).log("Invitation accepted");
        return saved.getUsername();
    }
}

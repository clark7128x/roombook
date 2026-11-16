package roombook.accounts.application;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roombook.accounts.persistence.InvitationRepository;
import roombook.kernel.Actor;
import roombook.kernel.OfficeClock;

@Service @Transactional(readOnly = true)
public class InvitationQueries {
    private final InvitationRepository invitations;
    private final OfficeClock clock;
    public InvitationQueries(InvitationRepository invitations, OfficeClock clock) { this.invitations = invitations; this.clock = clock; }
    public List<InvitationView> list(Actor actor) {
        actor.requireAdmin(); var now = clock.instant();
        return invitations.findTop100ByOrderByCreatedAtDescIdDesc().stream().map(i -> new InvitationView(i.getId(),
            i.getUsername(), i.getDisplayName(), i.getExpiresAt(), i.getConsumedAt() != null ? "Прийнято"
            : i.getRevokedAt() != null ? "Відкликано" : i.available(now) ? "Очікує" : "Строк минув", i.available(now))).toList();
    }
}

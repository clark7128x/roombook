package roombook.accounts.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import roombook.accounts.domain.AccountInvitation;

public interface InvitationRepository extends Repository<AccountInvitation, Long> {
    AccountInvitation saveAndFlush(AccountInvitation invitation);
    List<AccountInvitation> findTop100ByOrderByCreatedAtDescIdDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from AccountInvitation i where i.id = :id")
    Optional<AccountInvitation> lockById(@Param("id") long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from AccountInvitation i where i.tokenHash = :hash")
    Optional<AccountInvitation> lockByTokenHash(@Param("hash") String hash);
}

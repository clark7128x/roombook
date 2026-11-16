package roombook.accounts.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import roombook.accounts.domain.AppUser;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторій агрегату AppUser для операцій запису (D-04, D-09, INV-13, INV-14, AR-04, AR-12).
 * Розширює базовий Repository без методів delete*.
 */
public interface AppUserRepository extends Repository<AppUser, Long> {

    Optional<AppUser> findById(Long id);

    List<AppUser> findAll();

    List<AppUser> findAllById(Iterable<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM AppUser u WHERE u.id = :id")
    Optional<AppUser> lockById(@Param("id") Long id);

    /**
     * Блокування цільового користувача та всіх активних ADMIN одним запитом
     * у детермінованому порядку id для запобігання взаємним блокуванням (D-04, INV-14).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT u FROM AppUser u
        WHERE u.id = :targetId
           OR (u.role = roombook.kernel.Role.ADMIN AND u.enabled = true)
        ORDER BY u.id ASC
    """)
    List<AppUser> lockWithActiveAdmins(@Param("targetId") Long targetId);

    @Query("SELECT u FROM AppUser u WHERE u.usernameKey = :usernameKey")
    Optional<AppUser> findByUsernameKey(@Param("usernameKey") String usernameKey);

    @Query("SELECT COUNT(u) > 0 FROM AppUser u WHERE u.role = roombook.kernel.Role.ADMIN AND u.enabled = true")
    boolean existsActiveAdmin();

    AppUser save(AppUser user);

    AppUser saveAndFlush(AppUser user);

    void flush();
}

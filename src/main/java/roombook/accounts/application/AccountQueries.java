package roombook.accounts.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roombook.accounts.domain.AppUser;
import roombook.accounts.persistence.AppUserRepository;
import roombook.kernel.Actor;
import roombook.kernel.DomainException;
import roombook.kernel.Reason;

import java.text.Collator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Читання облікових записів адміністратором (EP-13, EP-14, D-10, AR-07, AR-09).
 */
@Service
@Transactional(readOnly = true)
public class AccountQueries {

    private final AppUserRepository appUserRepository;

    public AccountQueries(AppUserRepository appUserRepository) {
        this.appUserRepository = Objects.requireNonNull(appUserRepository, "appUserRepository must not be null");
    }

    /**
     * Отримання списку всіх користувачів, впорядкованого за українською абеткою displayName (EP-13, T-01).
     * Доступ лише для ADMIN.
     */
    public List<UserView> listUsers(Actor actor) {
        Objects.requireNonNull(actor, "actor must not be null");
        actor.requireAdmin();

        Collator collator = Collator.getInstance(Locale.forLanguageTag("uk-UA"));
        return appUserRepository.findAll().stream()
                .sorted((u1, u2) -> collator.compare(u1.getDisplayName(), u2.getDisplayName()))
                .map(this::toView)
                .toList();
    }

    /**
     * Отримання даних одного користувача за його ідентифікатором (EP-14).
     * Доступ лише для ADMIN.
     */
    public UserView getUser(Actor actor, long userId) {
        Objects.requireNonNull(actor, "actor must not be null");
        actor.requireAdmin();

        AppUser user = appUserRepository.findById(userId)
                .orElseThrow(() -> new DomainException(Reason.USER_NOT_FOUND));
        return toView(user);
    }

    private UserView toView(AppUser u) {
        return new UserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.isEnabled());
    }
}

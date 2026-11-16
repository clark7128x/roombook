package roombook.accounts.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import roombook.accounts.api.UserDirectory;
import roombook.accounts.domain.AppUser;
import roombook.accounts.persistence.AppUserRepository;

import java.text.Collator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Реалізація публічного API UserDirectory для читання імен авторів (A-31, A-34, AR-03, AR-09).
 */
@Service
@Transactional(readOnly = true)
public class UserDirectoryQueries implements UserDirectory {

    private final AppUserRepository appUserRepository;

    public UserDirectoryQueries(AppUserRepository appUserRepository) {
        this.appUserRepository = Objects.requireNonNull(appUserRepository, "appUserRepository must not be null");
    }

    @Override
    public Map<Long, String> displayNames(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<AppUser> users = appUserRepository.findAllById(userIds);
        Map<Long, String> result = new HashMap<>();
        for (AppUser user : users) {
            result.put(user.getId(), user.getDisplayName());
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public List<SelectableUser> selectableUsers() {
        Collator collator = Collator.getInstance(Locale.forLanguageTag("uk-UA"));
        return appUserRepository.findAll().stream()
                .sorted((u1, u2) -> collator.compare(u1.getDisplayName(), u2.getDisplayName()))
                .map(u -> new SelectableUser(u.getId(), u.getDisplayName()))
                .toList();
    }
}

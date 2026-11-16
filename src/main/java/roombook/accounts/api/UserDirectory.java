package roombook.accounts.api;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Публічний API каталогу користувачів для інших модулів (A-31, A-34, AR-03).
 */
public interface UserDirectory {

    /**
     * Повертає мапу відображення ID користувача на його displayName (A-31).
     */
    Map<Long, String> displayNames(Set<Long> userIds);

    /**
     * Повертає список усіх користувачів (зокрема неактивних), впорядкований за українською абеткою (A-34, T-01).
     */
    List<SelectableUser> selectableUsers();

    record SelectableUser(long id, String displayName) {
    }
}

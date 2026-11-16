package roombook.kernel;

import java.util.Objects;

/**
 * Представлення суб'єкта поточної дії (A-17, етап 4).
 */
public record Actor(long userId, Role role) {

    public Actor {
        Objects.requireNonNull(role, "role must not be null");
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public void requireAdmin() {
        if (!isAdmin()) {
            throw new DomainException(Reason.NOT_PERMITTED);
        }
    }
}

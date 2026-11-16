package roombook.accounts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import roombook.kernel.ComparisonKey;
import roombook.kernel.DomainException;
import roombook.kernel.FieldError;
import roombook.kernel.FieldRule;
import roombook.kernel.Reason;
import roombook.kernel.Role;
import roombook.kernel.TextRules;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Сутність облікового запису користувача (INV-13, FR-02, FR-23, SEC-03, SEC-04, A-14, A-15, AR-04).
 * Без публічних сеттерів. toString() ніколи не містить passwordHash.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]+$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "username_key", nullable = false, length = 64)
    private String usernameKey;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private Role role;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    /**
     * Конструктор для JPA.
     */
    protected AppUser() {
    }

    /**
     * Створення нового облікового запису. За замовчуванням enabled = true (FR-23).
     */
    public AppUser(String rawUsername, String rawDisplayName, String passwordHash, Role role) {
        List<FieldError> errors = new ArrayList<>();

        // 1. username: 3...64 символи, латиниця, цифри, крапка, підкреслення, дефіс (A-14, FR-23)
        if (rawUsername == null) {
            errors.add(new FieldError("username", FieldRule.REQUIRED));
        } else {
            int len = TextRules.codePointLength(rawUsername);
            if (len < 3 || len > 64) {
                errors.add(new FieldError("username", FieldRule.LENGTH));
            } else if (!USERNAME_PATTERN.matcher(rawUsername).matches()) {
                errors.add(new FieldError("username", FieldRule.ILLEGAL_CHARACTERS));
            }
        }

        // 2. displayName: після strip від 1 до 100 символів, без керувальних символів (A-14, A-36)
        String strippedDisplayName = null;
        if (rawDisplayName == null) {
            errors.add(new FieldError("displayName", FieldRule.REQUIRED));
        } else {
            strippedDisplayName = TextRules.strip(rawDisplayName);
            if (strippedDisplayName.isEmpty()) {
                errors.add(new FieldError("displayName", FieldRule.REQUIRED));
            } else if (TextRules.codePointLength(strippedDisplayName) > 100) {
                errors.add(new FieldError("displayName", FieldRule.LENGTH));
            } else if (TextRules.hasControlCharacters(strippedDisplayName, false)) {
                errors.add(new FieldError("displayName", FieldRule.ILLEGAL_CHARACTERS));
            }
        }

        // 3. passwordHash
        if (passwordHash == null || passwordHash.isBlank()) {
            errors.add(new FieldError("passwordHash", FieldRule.REQUIRED));
        }

        // 4. role
        if (role == null) {
            errors.add(new FieldError("role", FieldRule.REQUIRED));
        }

        if (!errors.isEmpty()) {
            throw new DomainException(Reason.INVALID_INPUT, errors);
        }

        this.username = rawUsername;
        this.usernameKey = ComparisonKey.calculate(rawUsername);
        this.displayName = strippedDisplayName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
    }

    /**
     * Зміна ролі користувача (FR-23).
     */
    public void changeRole(Role newRole) {
        this.role = Objects.requireNonNull(newRole, "role must not be null");
    }

    /**
     * Деактивація користувача (FR-23). Безпечний повтор (ідемпотентна дія, A-17).
     */
    public void disable() {
        this.enabled = false;
    }

    /**
     * Активація користувача (FR-23). Безпечний повтор (ідемпотентна дія, A-17).
     */
    public void enable() {
        this.enabled = true;
    }

    /**
     * Заміна хешу пароля (FR-23, SEC-03).
     */
    public void updatePasswordHash(String newPasswordHash) {
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new DomainException(Reason.INVALID_INPUT, List.of(new FieldError("passwordHash", FieldRule.REQUIRED)));
        }
        this.passwordHash = newPasswordHash;
    }

    /**
     * toString() без пароля чи хешу (SEC-04).
     */
    @Override
    public String toString() {
        return "AppUser[" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", displayName='" + displayName + '\'' +
                ", role=" + role +
                ", enabled=" + enabled +
                ']';
    }

    // --- Геттери (лише читання) ---

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getUsernameKey() {
        return usernameKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }
}

package roombook.accounts.web.api;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import roombook.accounts.application.ChangeUserRoleCommand;
import roombook.kernel.Role;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RoleInput(Role role) {
    public ChangeUserRoleCommand command() { return new ChangeUserRoleCommand(role); }
}

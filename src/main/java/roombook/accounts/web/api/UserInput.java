package roombook.accounts.web.api;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import roombook.accounts.application.CreateUserCommand;
import roombook.kernel.Role;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserInput(String username, String displayName, String password, Role role) {
    public CreateUserCommand command() { return new CreateUserCommand(username, displayName, password, role); }
    @Override public String toString() { return "UserInput[password=***]"; }
}

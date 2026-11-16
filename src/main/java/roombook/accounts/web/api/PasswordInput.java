package roombook.accounts.web.api;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import roombook.accounts.application.ResetPasswordCommand;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PasswordInput(String password) {
    public ResetPasswordCommand command() { return new ResetPasswordCommand(password); }
    @Override public String toString() { return "PasswordInput[password=***]"; }
}

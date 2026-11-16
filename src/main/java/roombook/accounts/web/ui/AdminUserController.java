package roombook.accounts.web.ui;

import jakarta.servlet.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import roombook.accounts.application.*;
import roombook.kernel.*;
import roombook.platform.UiForms;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {
    private final AccountQueries queries;
    private final AccountService service;
    private final UiForms forms;
    public AdminUserController(AccountQueries queries, AccountService service, UiForms forms) {
        this.queries = queries; this.service = service; this.forms = forms;
    }
    @GetMapping public String list(Actor actor, Model model) {
        model.addAttribute("users", queries.listUsers(actor)); return "accounts/admin-users";
    }
    @GetMapping("/new") public String newForm(Model model) {
        model.addAttribute("user", new UserForm("", "", "EMPLOYEE", "")); return "accounts/admin-user-form";
    }
    @PostMapping public ModelAndView create(Actor actor, @ModelAttribute UserForm form, Model model,
            HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirect) {
        try {
            service.createUser(actor, new CreateUserCommand(form.username(), form.displayName(), form.password(), role(form.role())));
            redirect.addFlashAttribute("successMessage", "Користувача створено.");
            return UiForms.redirect(request, response, "/admin/users");
        } catch (DomainException exception) {
            model.addAttribute("user", new UserForm(form.username(), form.displayName(), form.role(), ""));
            return forms.failure("accounts/admin-user-form" + (UiForms.htmx(request) ? " :: form" : ""), model, exception);
        }
    }
    @GetMapping("/{id}") public String panel(Actor actor, @PathVariable long id, Model model) {
        var user = queries.getUser(actor, id);
        model.addAttribute("user", user); model.addAttribute("userId", id);
        model.addAttribute("roleForm", new RoleForm(user.role().name()));
        return "accounts/admin-user-panel";
    }
    @PostMapping("/{id}/role") public String changeRole(Actor actor, @PathVariable long id,
            @ModelAttribute RoleForm form, Model model, HttpServletRequest request) {
        var user = service.changeUserRole(actor, id, new ChangeUserRoleCommand(role(form.role())));
        model.addAttribute("user", user); model.addAttribute("userId", id);
        model.addAttribute("roleForm", new RoleForm(user.role().name()));
        return UiForms.htmx(request) ? "accounts/admin-user-panel :: panel" : "redirect:/admin/users/" + id;
    }
    @PostMapping("/{id}/password") public ModelAndView password(Actor actor, @PathVariable long id,
            @ModelAttribute PasswordForm form, Model model, HttpServletRequest request) {
        // Resolve the resource before re-rendering field errors, including the missing-resource case.
        model.addAttribute("user", queries.getUser(actor, id)); model.addAttribute("userId", id);
        try {
            service.resetPassword(actor, id, new ResetPasswordCommand(form.password()));
            model.addAttribute("success", "Пароль змінено.");
            return UiForms.htmx(request) ? new ModelAndView("accounts/admin-user-password-form :: form", model.asMap())
                    : new ModelAndView("redirect:/admin/users/" + id);
        } catch (DomainException exception) {
            return forms.failure("accounts/admin-user-password-form :: form", model, exception);
        }
    }
    @PostMapping("/{id}/disable") public String disable(Actor actor, @PathVariable long id, Model model, HttpServletRequest request) {
        model.addAttribute("user", service.disableUser(actor, id));
        return UiForms.htmx(request) ? "components/user-row :: row" : "redirect:/admin/users";
    }
    @PostMapping("/{id}/enable") public String enable(Actor actor, @PathVariable long id, Model model, HttpServletRequest request) {
        model.addAttribute("user", service.enableUser(actor, id));
        return UiForms.htmx(request) ? "components/user-row :: row" : "redirect:/admin/users";
    }
    private static Role role(String value) {
        if (value == null || value.isBlank()) throw DomainException.invalidInput("role", FieldRule.REQUIRED);
        try { return Role.valueOf(value); } catch (IllegalArgumentException exception) { throw DomainException.invalidInput("role", FieldRule.FORMAT); }
    }
    public record UserForm(String username, String displayName, String role, String password) {
        @Override public String toString() { return "UserForm[redacted]"; }
    }
    public record RoleForm(String role) {}
    public record PasswordForm(String password) {
        @Override public String toString() { return "PasswordForm[redacted]"; }
    }
}

package roombook.accounts.web.ui;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import roombook.accounts.application.*;
import roombook.kernel.*;
import roombook.platform.UiForms;

@Controller
public class InvitationController {
    private final InvitationService service;
    private final InvitationQueries queries;
    private final InvitationRedemption redemption;
    private final UiForms forms;
    public InvitationController(InvitationService service, InvitationQueries queries, InvitationRedemption redemption, UiForms forms) {
        this.service = service; this.queries = queries; this.redemption = redemption; this.forms = forms;
    }
    @GetMapping("/admin/invitations") public String list(Actor actor, Model model) {
        model.addAttribute("invitations", queries.list(actor)); return "accounts/invitations";
    }
    @PostMapping("/admin/invitations") public ModelAndView issue(Actor actor, @RequestParam String username,
            @RequestParam String displayName, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        try {
            model.addAttribute("issued", service.issue(actor, username, displayName));
            model.addAttribute("invitations", queries.list(actor));
            return new ModelAndView("accounts/invitations", model.asMap());
        } catch (DomainException exception) {
            model.addAttribute("invitations", queries.list(actor));
            model.addAttribute("username", username); model.addAttribute("displayName", displayName);
            return forms.failure("accounts/invitations", model, exception);
        }
    }
    @PostMapping("/admin/invitations/{id}/revoke") public String revoke(Actor actor, @PathVariable long id, RedirectAttributes redirect) {
        service.revoke(actor, id); redirect.addFlashAttribute("successMessage", "Запрошення відкликано.");
        return "redirect:/admin/invitations";
    }
    @GetMapping("/join") public String join(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); return "accounts/join";
    }
    @PostMapping("/join") public ModelAndView accept(@ModelAttribute JoinForm form, Model model,
            HttpServletResponse response, RedirectAttributes redirect) {
        response.setHeader("Cache-Control", "no-store");
        try {
            String username = redemption.accept(form.token(), form.password(), form.confirmation());
            redirect.addFlashAttribute("joinedMessage", "Обліковий запис створено. Увійдіть із логіном " + username + ".");
            return new ModelAndView("redirect:/login");
        } catch (DomainException exception) {
            // Retain only a syntactically valid token, never either password.
            if (form.token() != null && form.token().matches("[A-Za-z0-9_-]{43}")) model.addAttribute("invitationToken", form.token());
            return forms.failure("accounts/join", model, exception);
        }
    }
    public record JoinForm(String token, String password, String confirmation) {
        @Override public String toString() { return "JoinForm[redacted]"; }
    }
}

package roombook.platform;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
@ControllerAdvice(basePackages = {"roombook.accounts.web.ui", "roombook.scheduling.web.ui"})
public class UiModelAdvice {
    private final UiModels models;
    public UiModelAdvice(UiModels models) { this.models = models; }
    @ModelAttribute public void common(HttpServletRequest request, Model model) { model.addAllAttributes(models.common(request)); }
}

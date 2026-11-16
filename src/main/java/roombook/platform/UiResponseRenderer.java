package roombook.platform;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ViewResolver;
@Component
public class UiResponseRenderer {
    private final ObjectProvider<ViewResolver> views;
    private final UiModels models;
    private final ClientClassifier clients;
    public UiResponseRenderer(@Qualifier("thymeleafViewResolver") ObjectProvider<ViewResolver> views, UiModels models, ClientClassifier clients) {
        this.views = views; this.models = models; this.clients = clients;
    }
    public void problem(HttpServletRequest request, HttpServletResponse response, ProblemDetail problem) throws IOException {
        boolean fragment = clients.classify(request) == ClientClassifier.Client.HTMX;
        response.setStatus(problem.getStatus()); response.setContentType("text/html;charset=UTF-8");
        if (fragment) { response.setHeader("HX-Retarget", "#alerts"); response.setHeader("HX-Reswap", "innerHTML"); }
        var model = models.common(request);
        model.put("status", problem.getStatus()); model.put("message", problem.getDetail());
        model.put("code", problem.getProperties() == null ? "" : problem.getProperties().get("code"));
        model.put("pageTitle", switch (problem.getStatus()) {
            case 403 -> "Немає доступу"; case 404 -> "Сторінку не знайдено";
            case 500 -> "Помилка на сервері"; default -> "Не вдалося виконати дію";
        });
        try {
            var view = views.getObject().resolveViewName(fragment ? "components/alert :: alert" : "error/page", Locale.forLanguageTag("uk"));
            if (view == null) throw new IllegalStateException("Missing UI error view");
            view.render(model, request, response);
        } catch (Exception exception) { throw new IOException("Unable to render UI error", exception); }
    }
}

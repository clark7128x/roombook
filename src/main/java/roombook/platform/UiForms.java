package roombook.platform;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.web.servlet.ModelAndView;
import roombook.kernel.DomainException;
import roombook.kernel.FieldRule;
@Component
public class UiForms {
    public static boolean htmx(jakarta.servlet.http.HttpServletRequest request) {
        return "true".equalsIgnoreCase(request.getHeader("HX-Request"));
    }
    public static ModelAndView redirect(jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response, String target) {
        if (htmx(request)) { response.setHeader("HX-Redirect", target); response.setStatus(200); return null; }
        return new ModelAndView("redirect:" + target);
    }
    private final ErrorCatalog catalog;
    private final ErrorLogging logging;
    public UiForms(ErrorCatalog catalog, ErrorLogging logging) { this.catalog = catalog; this.logging = logging; }
    public ModelAndView failure(String view, Model model, DomainException exception) {
        logging.expected(exception);
        var problem = catalog.problem(exception);
        var errors = new LinkedHashMap<String, String>();
        if (problem.getProperties() != null && problem.getProperties().get("errors") instanceof List<?> list)
            for (Object item : list) if (item instanceof ErrorCatalog.InputError error) errors.put(error.field(), error.message());
        model.addAttribute("fieldErrors", errors); model.addAttribute("formError", problem.getDetail());
        return new ModelAndView(view, model.asMap(), HttpStatus.valueOf(problem.getStatus()));
    }
    public static Integer integer(String value, String field) {
        if (value == null || value.isBlank()) return null;
        try { return Integer.valueOf(value); }
        catch (NumberFormatException exception) { throw DomainException.invalidInput(field, FieldRule.FORMAT); }
    }
}

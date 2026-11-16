package roombook.platform;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.*;
import roombook.kernel.DomainException;
@ControllerAdvice(basePackages = {"roombook.accounts.web.ui", "roombook.scheduling.web.ui"})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UiExceptionHandler extends org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler {
    private final ErrorCatalog catalog;
    private final ErrorLogging logging;
    private final UiResponseRenderer renderer;
    public UiExceptionHandler(ErrorCatalog catalog, ErrorLogging logging, UiResponseRenderer renderer) {
        this.catalog = catalog; this.logging = logging; this.renderer = renderer;
    }
    @ExceptionHandler(DomainException.class)
    public void domain(DomainException exception, HttpServletRequest request, HttpServletResponse response) throws IOException {
        logging.expected(exception); renderer.problem(request, response, catalog.problem(exception));
    }
    @ExceptionHandler(Exception.class)
    public void unexpected(Exception exception, HttpServletRequest request, HttpServletResponse response) throws IOException {
        logging.unexpected(exception); renderer.problem(request, response, catalog.problem(TransportCode.INTERNAL_ERROR));
    }
    @Override
    protected org.springframework.http.ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            org.springframework.http.HttpHeaders headers, org.springframework.http.HttpStatusCode status,
            org.springframework.web.context.request.WebRequest request) {
        var problem = status.value() == 400 ? catalog.problem(roombook.kernel.Reason.INVALID_INPUT)
                : status.value() < 500 ? catalog.unsupported(status.value()) : catalog.problem(TransportCode.INTERNAL_ERROR);
        if (status.value() >= 500) logging.unexpected(exception);
        var servlet = (org.springframework.web.context.request.ServletWebRequest) request;
        try { renderer.problem(servlet.getRequest(), servlet.getResponse(), problem); }
        catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        return null;
    }
}

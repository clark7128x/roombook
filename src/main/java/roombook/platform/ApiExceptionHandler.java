package roombook.platform;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import roombook.kernel.DomainException;
import roombook.kernel.FieldRule;
import roombook.kernel.Reason;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private final ErrorCatalog catalog;
    private final ErrorLogging logging;
    private final SecurityErrorHandler security;
    private UiResponseRenderer uiRenderer;
    @org.springframework.beans.factory.annotation.Autowired
    void uiRenderer(UiResponseRenderer renderer) { this.uiRenderer = renderer; }

    public ApiExceptionHandler(ErrorCatalog catalog, ErrorLogging logging, SecurityErrorHandler security) {
        this.catalog = catalog;
        this.logging = logging;
        this.security = security;
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Object> domain(DomainException exception) {
        logging.expected(exception);
        return response(catalog.problem(exception), HttpHeaders.EMPTY);
    }

    @ExceptionHandler(AuthenticationException.class)
    public void unauthenticated(AuthenticationException exception, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        security.commence(request, response, exception);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public void denied(AccessDeniedException exception, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        security.handle(request, response, exception);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(Exception exception) {
        logging.unexpected(exception);
        return response(catalog.problem(TransportCode.INTERNAL_ERROR), HttpHeaders.EMPTY);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object ignored,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem;
        if (status.value() == 400) {
            problem = catalog.problem(Reason.INVALID_INPUT);
            if (exception instanceof MissingServletRequestParameterException missing) {
                field(problem, missing.getParameterName(), FieldRule.REQUIRED);
            } else if (exception instanceof TypeMismatchException mismatch && mismatch.getPropertyName() != null) {
                field(problem, mismatch.getPropertyName(), FieldRule.FORMAT);
            } else if (exception instanceof MethodArgumentNotValidException invalid) {
                problem.setProperty("errors", invalid.getBindingResult().getFieldErrors().stream()
                        .map(error -> new ErrorCatalog.InputError(error.getField(), catalog.message(FieldRule.FORMAT, Map.of()))).toList());
            } else if (exception instanceof HttpMessageNotReadableException unreadable
                    && unreadable.getCause() instanceof JacksonException json) {
                String path = json.getPath().stream().map(JacksonException.Reference::getPropertyName)
                        .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.joining("."));
                if (!path.isEmpty()) field(problem, path, FieldRule.FORMAT);
            }
        } else if (status.value() == 404 || status.value() == 405 || status.value() == 406 || status.value() == 415) {
            problem = catalog.unsupported(status.value());
        } else {
            logging.unexpected(exception);
            problem = catalog.problem(TransportCode.INTERNAL_ERROR);
        }
        if (uiRenderer != null && request instanceof org.springframework.web.context.request.ServletWebRequest servlet
                && new ClientClassifier().classify(servlet.getRequest()) != ClientClassifier.Client.API) {
            try { uiRenderer.problem(servlet.getRequest(), servlet.getResponse(), problem); }
            catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
            return null;
        }
        return response(problem, headers);
    }

    private void field(ProblemDetail problem, String name, FieldRule rule) {
        problem.setProperty("errors", List.of(new ErrorCatalog.InputError(name, catalog.message(rule, Map.of()))));
    }

    private ResponseEntity<Object> response(ProblemDetail problem, HttpHeaders original) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(original);
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(problem, headers, HttpStatusCode.valueOf(problem.getStatus()));
    }
}

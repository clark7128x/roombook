package roombook.platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.stereotype.Component;
import roombook.kernel.DomainException;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@Component
public class ErrorLogging {
    private static final Logger log = LoggerFactory.getLogger(ErrorLogging.class);

    public void expected(DomainException exception) {
        log.atInfo().addKeyValue("code", exception.getReason().name())
                .addKeyValue("rule", exception.getRule()).addKeyValue("requestId", MDC.get("requestId"))
                .log("Request rejected: code={} rule={} requestId={}", exception.getReason(),
                        exception.getRule(), MDC.get("requestId"));
    }

    public void unexpected(Throwable exception) {
        log.atError().addKeyValue("code", TransportCode.INTERNAL_ERROR.name())
                .addKeyValue("failure", failure(exception)).addKeyValue("requestId", MDC.get("requestId"))
                .setCause(exception).log("Unexpected request failure: code=INTERNAL_ERROR failure={} requestId={}",
                        failure(exception), MDC.get("requestId"));
    }

    public String failure(Throwable exception) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean pool = false;
        for (Throwable current = exception; current != null && seen.add(current); current = current.getCause()) {
            if (current instanceof SQLException sql && "55P03".equals(sql.getSQLState())) return "LOCK_TIMEOUT";
            if (current instanceof CannotGetJdbcConnectionException || current instanceof SQLTransientConnectionException) pool = true;
        }
        return pool ? "POOL_TIMEOUT" : "UNEXPECTED";
    }
}

package roombook.platform;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("D-19")
@Tag("OPS-03")
class ProductionLoggingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("D-19, OPS-03: Журнал прода є валідним JSON із requestId та часом у UTC (із закінченням Z)")
    void productionLogFormatIsStrictJsonWithUtcTimestampAndRequestId() throws Exception {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        // StructuredLogEncoder очікує Spring Environment у контексті логера
        context.putObject(Environment.class.getName(), new MockEnvironment());

        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setContext(context);
        encoder.setFormat("ecs");
        encoder.start();

        String expectedRequestId = "req-prod-test-99999";
        LoggingEvent event = new LoggingEvent(
                RequestLoggingFilter.class.getName(),
                context.getLogger(RequestLoggingFilter.class),
                Level.INFO,
                "Request completed: method=POST path=/api/v1/bookings status=201 durationMs=45",
                null,
                null
        );
        event.setMDCPropertyMap(Map.of("requestId", expectedRequestId));
        event.setTimeStamp(Instant.parse("2026-10-29T20:30:00Z").toEpochMilli());

        byte[] encodedBytes = encoder.encode(event);
        assertNotNull(encodedBytes, "Encoder має повернути байти JSON");
        String jsonLine = new String(encodedBytes, StandardCharsets.UTF_8).trim();

        // 1. Журнал є валідним JSON
        JsonNode root = objectMapper.readTree(jsonLine);
        assertTrue(root.isObject(), "Журнал прода має бути валідним JSON-об'єктом: " + jsonLine);

        // 2. Час у UTC (ISO-8601 із Z)
        JsonNode timestampNode = root.get("@timestamp");
        assertNotNull(timestampNode, "JSON має містити поле @timestamp");
        String timestampStr = timestampNode.asText();
        assertTrue(timestampStr.endsWith("Z") || timestampStr.endsWith("+00:00"),
                "@timestamp має бути в UTC (із закінченням Z або +00:00), отримано: " + timestampStr);
        // Перевіряємо можливість парсингу як Instant
        Instant parsedInstant = Instant.parse(timestampStr);
        assertNotNull(parsedInstant);

        // 3. Наявність requestId з MDC
        JsonNode requestIdNode = root.at("/mdc/requestId");
        if (requestIdNode.isMissingNode()) {
            requestIdNode = root.get("requestId");
        }
        assertNotNull(requestIdNode);
        assertFalse(requestIdNode.isMissingNode(), "requestId має бути присутній у структурованому журналі: " + jsonLine);
        assertEquals(expectedRequestId, requestIdNode.asText(), "requestId має збігатися зі значенням MDC");

        // 4. Повідомлення та рівень
        JsonNode messageNode = root.get("message");
        assertNotNull(messageNode);
        assertTrue(messageNode.asText().contains("Request completed"));

        encoder.stop();
    }
}

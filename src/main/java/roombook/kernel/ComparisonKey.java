package roombook.kernel;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/**
 * Ключ порівняння для Room.name та User.username за правилом A-15 та D-05.
 * Використовує Unicode NFC, нижній регістр без залежності від локалі (Locale.ROOT)
 * та згорнуті пробіли за правилом A-55.
 */
public record ComparisonKey(String value) {

    public ComparisonKey {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static ComparisonKey of(String input) {
        return new ComparisonKey(calculate(input));
    }

    /**
     * Обчислення нормалізованого ключа за правилом A-15.
     */
    public static String calculate(String input) {
        Objects.requireNonNull(input, "input must not be null");
        String stripped = TextRules.strip(input);
        String collapsed = TextRules.collapseWhitespaces(stripped);
        String lower = collapsed.toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lower, Normalizer.Form.NFC);
    }

    @Override
    public String toString() {
        return value;
    }
}

package roombook.kernel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.text.Normalizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("TS-04")
@Tag("A-15")
@Tag("INV-11")
@Tag("INV-13")
class ComparisonKeyTest {

    @Test
    @DisplayName("TS-04: «Альфа» і « альфа » мають однаковий ключ")
    void alphaWithDifferentCasesAndWhitespacesYieldsSameKey() {
        String key1 = ComparisonKey.calculate("Альфа");
        String key2 = ComparisonKey.calculate(" альфа ");

        assertEquals("альфа", key1);
        assertEquals("альфа", key2);
        assertEquals(key1, key2);

        ComparisonKey obj1 = ComparisonKey.of("Альфа");
        ComparisonKey obj2 = ComparisonKey.of(" альфа ");
        assertEquals(obj1, obj2);
        assertEquals(obj1.hashCode(), obj2.hashCode());
    }

    @Test
    @DisplayName("TS-04: Літера «й» у складеній і розкладеній формі дає один ключ")
    void letterYiInComposedAndDecomposedFormsYieldsSameKey() {
        // \u0439 — складена (NFC) маленька "й"
        // \u0438\u0306 — розкладена (NFD) "и" + combining breve
        String composed = "Київ \u0439";
        String decomposed = "Київ \u0438\u0306";

        assertNotEquals(composed, decomposed);

        String keyComposed = ComparisonKey.calculate(composed);
        String keyDecomposed = ComparisonKey.calculate(decomposed);

        assertEquals("київ й", keyComposed);
        assertEquals("київ й", keyDecomposed);
        assertEquals(keyComposed, keyDecomposed);

        // Також перевіряємо велику літеру "Й" у складеній та розкладеній формі
        String upperComposed = "ЙОД";
        String upperDecomposed = "\u0418\u0306ОД";
        assertEquals(ComparisonKey.calculate(upperComposed), ComparisonKey.calculate(upperDecomposed));
        assertEquals("йод", ComparisonKey.calculate(upperComposed));
    }

    @Test
    @DisplayName("TS-04: Нерозривний пробіл (U+00A0) обрізається та згортається за A-55")
    void nonBreakingSpaceHandledAccordingToA55() {
        // Нерозривні пробіли на початку та в кінці
        String inputWithNbsp = "\u00A0Альфа\u00A0";
        assertEquals("альфа", ComparisonKey.calculate(inputWithNbsp));

        // Нерозривні пробіли всередині рядка замінюються на одинарний U+0020
        String internalNbsp = "Велика\u00A0Переговорна\u00A0\u00A0Кімната";
        assertEquals("велика переговорна кімната", ComparisonKey.calculate(internalNbsp));
    }

    @Test
    @DisplayName("TS-04: Нижній регістр кирилиці без залежності від локалі")
    void lowerCaseCyrillicIndependentOfLocale() {
        String mixedCyrillic = "Іванна Ґудзик Євгеній";
        String expected = "іванна ґудзик євгеній";

        assertEquals(expected, ComparisonKey.calculate(mixedCyrillic));
    }

    @Test
    @DisplayName("TS-04: Ключ найдовшої назви (200 символів) вміщується в 200 символів")
    void longestNameKeyFitsWithin200Characters() {
        // Рівно 200 символів кирилиці (8 * 25 = 200)
        String longestName = "Кімната-".repeat(25);
        assertEquals(200, longestName.length());

        String key = ComparisonKey.calculate(longestName);
        assertTrue(key.length() <= 200, "Довжина ключа не повинна перевищувати 200 символів, отримано: " + key.length());
        assertEquals(200, key.length());
        assertEquals("кімната-".repeat(25), key);
    }

    @Test
    @DisplayName("A-15: Згортання різних типів пробілів Unicode у звичайний одинарний пробіл")
    void variousUnicodeWhitespacesCollapsed() {
        // \u2003 (em space), \u2002 (en space), \u3000 (ideographic space), \t, \n
        String messyWhitespaces = "\u2003Синій  \t \u2002 зал \u3000 \n 1 \u2003";
        assertEquals("синій зал 1", ComparisonKey.calculate(messyWhitespaces));
    }

    @Test
    @DisplayName("ComparisonKey кидає NullPointerException при null вхідних даних")
    void nullInputThrowsException() {
        assertThrows(NullPointerException.class, () -> ComparisonKey.calculate(null));
        assertThrows(NullPointerException.class, () -> ComparisonKey.of(null));
    }
}

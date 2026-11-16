package roombook.kernel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("A-36")
@Tag("A-55")
class TextRulesTest {

    @Test
    @DisplayName("A-55: Пробільний символ визначається за Character.isWhitespace або категорією Zs")
    void isWhitespaceIdentifiesStandardAndZsSeparators() {
        // Стандартні пробіли ASCII (Character.isWhitespace)
        assertTrue(TextRules.isWhitespace(' '));
        assertTrue(TextRules.isWhitespace('\t'));
        assertTrue(TextRules.isWhitespace('\n'));
        assertTrue(TextRules.isWhitespace('\r'));
        assertTrue(TextRules.isWhitespace('\f'));

        // Категорія Zs (зокрема ті, для яких Character.isWhitespace повертає false)
        assertTrue(TextRules.isWhitespace(0x00A0)); // NO-BREAK SPACE
        assertTrue(TextRules.isWhitespace(0x2000)); // EN QUAD
        assertTrue(TextRules.isWhitespace(0x2007)); // FIGURE SPACE
        assertTrue(TextRules.isWhitespace(0x202F)); // NARROW NO-BREAK SPACE
        assertTrue(TextRules.isWhitespace(0x205F)); // MEDIUM MATHEMATICAL SPACE
        assertTrue(TextRules.isWhitespace(0x3000)); // IDEOGRAPHIC SPACE

        // Непробільні символи
        assertFalse(TextRules.isWhitespace('A'));
        assertFalse(TextRules.isWhitespace('1'));
        assertFalse(TextRules.isWhitespace('я'));
        assertFalse(TextRules.isWhitespace('-'));
    }

    @Test
    @DisplayName("A-55: strip обрізає пробільні символи з початку і кінця рядка")
    void stripRemovesLeadingAndTrailingWhitespaces() {
        assertNull(TextRules.strip(null));
        assertEquals("", TextRules.strip(""));
        assertEquals("", TextRules.strip("   \t  \u00A0  "));
        assertEquals("Привіт", TextRules.strip("   Привіт   "));
        assertEquals("Привіт Світ", TextRules.strip("\u00A0\u2002Привіт Світ\u3000"));
        assertEquals("Внутрішні   пробіли", TextRules.strip("  Внутрішні   пробіли  "));
    }

    @Test
    @DisplayName("A-55: collapseWhitespaces згортає послідовності будь-яких пробілів в один U+0020")
    void collapseWhitespacesCollapsesSequencesIntoSingleSpace() {
        assertNull(TextRules.collapseWhitespaces(null));
        assertEquals(" ", TextRules.collapseWhitespaces("   "));
        assertEquals("A B C", TextRules.collapseWhitespaces("A   \t  \u00A0  B\u3000\u2000C"));
    }

    @Test
    @DisplayName("A-36: codePointLength коректно рахує кодові точки включно з емодзі та сурогатними парами")
    void codePointLengthHandlesSurrogatePairs() {
        assertEquals(0, TextRules.codePointLength(null));
        assertEquals(0, TextRules.codePointLength(""));
        assertEquals(5, TextRules.codePointLength("Hello"));
        assertEquals(5, TextRules.codePointLength("Київ!"));

        // Емодзі 🏨 (U+1F3E8 HOTEL): у Java String має 2 char'и (сурогатна пара), але це 1 кодова точка
        String emojiString = "Кімната 🏨";
        assertEquals(10, emojiString.length(), "Java String length() рахує UTF-16 code units (2 для емодзі)");
        assertEquals(9, TextRules.codePointLength(emojiString), "codePointLength має повернути 9 кодових точок");

        // Декілька сурогатних пар поспіль: 🇺🇦 (два Regional Indicator Symbol Letters: U+1F1FA, U+1F1E6 = 4 chars, 2 code points)
        String flagString = "🇺🇦";
        assertEquals(4, flagString.length());
        assertEquals(2, TextRules.codePointLength(flagString));
    }

    @Test
    @DisplayName("A-36: hasControlCharacters виявляє керувальні символи категорії Cc")
    void hasControlCharactersDetectsCcCategory() {
        assertFalse(TextRules.hasControlCharacters(null));
        assertFalse(TextRules.hasControlCharacters("Нормальний текст 123"));

        // Символи керування (ASCII 0..31, 127)
        assertTrue(TextRules.hasControlCharacters("Текст\u0000з нуль-байтом"));
        assertTrue(TextRules.hasControlCharacters("Текст\u0007з дзвінком"));
        assertTrue(TextRules.hasControlCharacters("Текст\tз табуляцією"));
        assertTrue(TextRules.hasControlCharacters("Текст\nз новим рядком"));
        assertTrue(TextRules.hasControlCharacters("Текст\rз поверненням каретки"));
        assertTrue(TextRules.hasControlCharacters("Текст\u007Fз DELETE"));
    }

    @Test
    @DisplayName("A-36, A-55: hasControlCharacters з allowNewlines=true дозволяє \\n та \\r (для equipment)")
    void hasControlCharactersWithAllowNewlinesPermitsLfAndCr() {
        String equipmentWithNewlines = "Проєктор\nМаркерна дошка\r\nВідеокамера";
        assertFalse(TextRules.hasControlCharacters(equipmentWithNewlines, true),
                "Для equipment переноси рядків \\n та \\r мають бути дозволені");

        // Але інші керувальні символи (наприклад, табуляція чи NUL) усе одно заборонені
        String equipmentWithTab = "Проєктор\tМаркерна дошка";
        assertTrue(TextRules.hasControlCharacters(equipmentWithTab, true),
                "Табуляція не є переносом рядка і має бути заборонена");

        String equipmentWithNullByte = "Проєктор\u0000";
        assertTrue(TextRules.hasControlCharacters(equipmentWithNullByte, true),
                "Нуль-байт має бути заборонений");
    }
}

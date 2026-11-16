package roombook.kernel;

/**
 * Правила обробки та валідації текстових полів (A-15, A-36, A-55).
 */
public final class TextRules {

    private TextRules() {
    }

    /**
     * Перевірка, чи є кодова точка пробільним символом за правилом A-55:
     * Character.isWhitespace або категорія Zs (зокрема нерозривний пробіл U+00A0).
     */
    public static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.getType(codePoint) == Character.SPACE_SEPARATOR;
    }

    /**
     * Обрізання пробільних символів за правилом A-55 з початку і кінця рядка.
     */
    public static String strip(String text) {
        if (text == null) {
            return null;
        }
        int len = text.length();
        int start = 0;
        while (start < len) {
            int cp = text.codePointAt(start);
            if (!isWhitespace(cp)) {
                break;
            }
            start += Character.charCount(cp);
        }
        if (start >= len) {
            return "";
        }
        int end = len;
        while (end > start) {
            int cp = text.codePointBefore(end);
            if (!isWhitespace(cp)) {
                break;
            }
            end -= Character.charCount(cp);
        }
        return text.substring(start, end);
    }

    /**
     * Згортання будь-якої послідовності з одного чи кількох пробільних символів (A-55)
     * в один пробіл U+0020 (' ').
     */
    public static String collapseWhitespaces(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(text.length());
        boolean inWhitespace = false;
        int len = text.length();
        for (int i = 0; i < len; ) {
            int cp = text.codePointAt(i);
            if (isWhitespace(cp)) {
                if (!inWhitespace) {
                    sb.append(' ');
                    inWhitespace = true;
                }
            } else {
                sb.appendCodePoint(cp);
                inWhitespace = false;
            }
            i += Character.charCount(cp);
        }
        return sb.toString();
    }

    /**
     * Довжина рядка в кодових точках Unicode (A-36).
     * Коректно враховує сурогатні пари (емодзі тощо).
     */
    public static int codePointLength(String text) {
        if (text == null) {
            return 0;
        }
        return text.codePointCount(0, text.length());
    }

    /**
     * Перевірка, чи є кодова точка керувальним символом Unicode (категорія Cc).
     */
    public static boolean isControl(int codePoint) {
        return Character.getType(codePoint) == Character.CONTROL;
    }

    /**
     * Перевірка наявності заборонених керувальних символів за правилом A-36.
     * За замовчуванням (allowNewlines = false) відхиляються всі керувальні символи категорії Cc.
     * Якщо allowNewlines = true (для equipment), дозволяються \n (U+000A) та \r (U+000D).
     */
    public static boolean hasControlCharacters(String text, boolean allowNewlines) {
        if (text == null) {
            return false;
        }
        int len = text.length();
        for (int i = 0; i < len; ) {
            int cp = text.codePointAt(i);
            if (isControl(cp)) {
                if (!allowNewlines || (cp != '\n' && cp != '\r')) {
                    return true;
                }
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    public static boolean hasControlCharacters(String text) {
        return hasControlCharacters(text, false);
    }
}

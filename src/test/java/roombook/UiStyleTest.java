package roombook;

import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiStyleTest {
    private static final Path STATIC = Path.of("src/main/resources/static");
    private static final Path TEMPLATES = Path.of("src/main/resources/templates");
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{3,8}\\b|\\b(?:rgb|hsl)a?\\(");
    private static final Set<String> TOKENS = Set.of("board", "page", "ink", "graphite", "line", "line-strong",
            "blue", "blue-hover", "blue-tint", "busy", "past", "error", "ok", "s1", "s2", "s3", "s4", "s5", "s6", "s7",
            "body-size", "small-size", "label-size", "h1-size", "h2-size", "control-h", "row-h", "header-h",
            "radius-booking", "radius-control", "form-w", "login-w", "table-w", "slot-h", "room-w", "time-w", "transition-time");

    static List<String> violations(String filename, String source) {
        String text = source.replaceAll("(?s)/\\*.*?\\*/|<!--.*?-->", "");
        var failures = new ArrayList<String>();
        boolean css = filename.endsWith(".css");
        // The owner requested the new visual direction in docs/ui-refresh. Keep security/resource
        // rules for every file; the new stylesheet owns its explicitly changed decoration rules.
        boolean refresh = filename.equals("refresh.css");
        String fetchable = text.replaceAll("xmlns(?::[\\w-]+)?=\"[^\"]*\"", "");
        if (Pattern.compile("https?://|data:|@import|!important", Pattern.CASE_INSENSITIVE).matcher(fetchable).find()) failures.add("external-resource");
        if (text.codePoints().anyMatch(cp -> (cp >= 0x1F000 && cp <= 0x1FAFF) || (cp >= 0x2600 && cp <= 0x27BF))) failures.add("emoji");
        if (css) {
            if (!filename.equals("tokens.css") && HEX.matcher(text).find()) failures.add("color-token");
            if (!refresh && Pattern.compile("gradient\\(|backdrop-filter|box-shadow|text-transform", Pattern.CASE_INSENSITIVE).matcher(text).find()) failures.add("decoration");
            var radius = Pattern.compile("border-radius\\s*:\\s*([^;}]+)").matcher(text);
            while (radius.find()) {
                String value = radius.group(1).strip();
                if (!refresh && !value.matches("(?:0|[24]px|var\\(--radius-(?:booking|control)\\))")) failures.add("radius");
            }
            var fonts = Pattern.compile("font-family\\s*:\\s*([^;}]+)").matcher(text);
            while (fonts.find()) if (!Set.of("\"Fira Sans\"", "\"Fira Sans\", Arial, sans-serif").contains(fonts.group(1).strip())) failures.add("font");
            var keyframes = Pattern.compile("@keyframes\\s+([\\w-]+)").matcher(text);
            while (keyframes.find()) if (!keyframes.group(1).equals("fresh")) failures.add("animation");
            var transitions = Pattern.compile("transition\\s*:\\s*([^;}]+)").matcher(text);
            while (transitions.find()) {
                for (String transition : transitions.group(1).split(",")) {
                    String value = transition.strip().replace("var(--transition-time)", "120ms");
                    if (value.equals("none")) continue;
                    if (!value.matches("(?:color|background(?:-color)?|border(?:-color)?)\\s+.*")) failures.add("transition-property");
                    var duration = Pattern.compile("([0-9.]+)(ms|s)\\b").matcher(value);
                    while (duration.find()) if (Double.parseDouble(duration.group(1)) * (duration.group(2).equals("s") ? 1000 : 1) > 120) failures.add("transition-duration");
                }
            }
            if (filename.equals("timeline.css")) for (int slot = 1; slot <= 48; slot++)
                if (!Pattern.compile("\\.tl-len-" + slot + "\\s*\\{").matcher(text).find()) failures.add("slot-" + slot);
        } else {
            if (Pattern.compile("\\b(?:style|on[a-z]+|hx-on[\\w:]*)\\s*=|js:", Pattern.CASE_INSENSITIVE).matcher(text).find()) failures.add("inline-code");
            var scripts = Pattern.compile("(?is)<script\\b([^>]*)>(.*?)</script>").matcher(text);
            while (scripts.find()) if (!scripts.group(1).matches("(?s).*\\b(?:th:)?src\\s*=.*") || !scripts.group(2).isBlank()) failures.add("inline-script");
            String visible = text.replaceAll("(?s)<[^>]*>", "");
            if (Pattern.compile("[!—]").matcher(visible).find()) failures.add("copy");
            if (text.contains("<html") && !text.contains("lang=\"uk\"")) failures.add("language");
        }
        return failures;
    }

    @Test @Tag("TS-27") @Tag("UI-01") @Tag("UI-02") @Tag("UI-04") @Tag("UI-05") @Tag("UI-06") @Tag("UI-07") @Tag("UI-09")
    void applicationAssetsAndTemplatesFollowStyleRules() throws Exception {
        try (var files = Files.list(STATIC.resolve("css"))) {
            for (var path : files.filter(path -> path.toString().endsWith(".css")).toList())
                assertEquals(List.of(), violations(path.getFileName().toString(), Files.readString(path)), path.toString());
        }
        try (var files = Files.walk(TEMPLATES)) {
            for (var path : files.filter(path -> path.toString().endsWith(".html")).toList())
                assertEquals(List.of(), violations(path.getFileName().toString(), Files.readString(path)), path.toString());
        }
        String tokens = Files.readString(STATIC.resolve("css/tokens.css"));
        for (String token : TOKENS) assertTrue(tokens.contains("--" + token + ":"), token);
        assertTrue(tokens.contains("--transition-time: 120ms"));
        assertTrue(Files.readString(STATIC.resolve("css/base.css")).contains(":focus-visible"));
        assertTrue(Files.readString(STATIC.resolve("css/components.css")).contains("prefers-reduced-motion"));
        assertTrue(Files.readString(STATIC.resolve("css/components.css")).contains("tabular-nums"));
        String fonts = Files.readString(STATIC.resolve("css/fonts.css"));
        assertEquals(6, fonts.split("font-display: swap", -1).length - 1);
        assertEquals(6, fonts.split("unicode-range:", -1).length - 1);
        try (var files = Files.list(STATIC.resolve("fonts"))) {
            assertEquals(6, files.filter(path -> path.toString().endsWith(".woff2") && path.toFile().length() > 1000).count());
        }
        assertTrue(Files.exists(STATIC.resolve("fonts/OFL.txt")));
        assertEquals(3, Files.readString(STATIC.resolve("icons.svg")).split("<symbol\\b", -1).length - 1);
    }

    @Test @Tag("TS-27")
    void prohibitedStyleFixturesAreRejected() {
        for (var fixture : List.of(
                new String[]{"x.css", "a{color:#fff}"}, new String[]{"x.css", "a{background:linear-gradient(red,blue)}"},
                new String[]{"x.css", "a{box-shadow:0 1px 2px}"}, new String[]{"x.css", "a{border-radius:8px}"},
                new String[]{"x.css", "a{text-transform:uppercase}"}, new String[]{"x.html", "<p>😀</p>"},
                new String[]{"x.html", "<p style=\"color:red\">X</p>"}, new String[]{"x.css", "a{background:url(data:foo)}"},
                new String[]{"x.css", "a{background:url(https://example.com/x)}"}, new String[]{"x.css", "@keyframes appear{}"},
                new String[]{"x.css", "a{transition:color 300ms}"}, new String[]{"timeline.css", ".tl-len-1{}"},
                new String[]{"x.css", "a{font-family:Inter}"}, new String[]{"x.html", "<p>Готово!</p>"},
                new String[]{"x.html", "<html lang=\"en\"></html>"}, new String[]{"x.html", "<script>alert(1)</script>"}))
            assertFalse(violations(fixture[0], fixture[1]).isEmpty(), Arrays.toString(fixture));
        assertTrue(violations("x.html", "<script src=\"/js/htmx.min.js\"></script>").isEmpty());
    }

    @Test @Tag("TS-27") @Tag("UI-09")
    void textColorsMeetNormalTextContrastAndVendoredHtmxIsPinned() throws Exception {
        String source = Files.readString(STATIC.resolve("css/tokens.css"));
        var colors = new HashMap<String, String>();
        var matcher = Pattern.compile("--([a-z-]+):\\s*(#[A-Fa-f0-9]{6})").matcher(source);
        while (matcher.find()) colors.put(matcher.group(1), matcher.group(2));
        for (String foreground : List.of("ink", "graphite", "blue", "error", "ok")) {
            double high = luminance(colors.get("board")), low = luminance(colors.get(foreground));
            assertTrue((high + 0.05) / (low + 0.05) >= 4.5, foreground);
        }
        var bytes = Files.readAllBytes(STATIC.resolve("js/htmx.min.js"));
        assertEquals("e209dda5c8235479f3166defc7750e1dbcd5a5c1808b7792fc2e6733768fb447",
                HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));
        assertTrue(Files.readString(STATIC.resolve("js/htmx-LICENSE.txt")).contains("Zero-Clause BSD"));
    }
    private double luminance(String hex) {
        double[] components = new double[3];
        for (int i = 0; i < 3; i++) {
            double value = Integer.parseInt(hex.substring(1 + i * 2, 3 + i * 2), 16) / 255.0;
            components[i] = value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
        }
        return components[0] * 0.2126 + components[1] * 0.7152 + components[2] * 0.0722;
    }
}

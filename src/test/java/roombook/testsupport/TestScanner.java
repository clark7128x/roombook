package roombook.testsupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Сканер тестових вихідних файлів та валідатор індексу обов'язкових ID (A-53, КЗ-3, T-03).
 */
public final class TestScanner {

    private static final Pattern THREAD_SLEEP_PATTERN = Pattern.compile("\\bThread\\.sleep\\s*\\(");
    private static final Pattern BARE_DISABLED_PATTERN = Pattern.compile("@Disabled\\b(?!\\s*\\(\\s*[\"'].*T-\\d+.*[\"']\\s*\\))");
    private static final Pattern TAG_PATTERN = Pattern.compile("@Tag\\s*\\(\\s*[\"']([^\"']+)[\"']\\s*\\)");

    private TestScanner() {
    }

    /**
     * Завантаження списку обов'язкових ID з файлу ресурсів.
     */
    public static Set<String> loadRequiredIds(Path filePath) throws IOException {
        Set<String> ids = new HashSet<>();
        List<String> lines = Files.readAllLines(filePath);
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                ids.add(trimmed);
            }
        }
        return ids;
    }

    /**
     * Сканування одного рядка / фрагмента коду на порушення (КЗ-3: Thread.sleep, @Disabled без T-...).
     */
    public static List<String> scanSourceContent(String content, String filename) {
        List<String> violations = new ArrayList<>();

        if (THREAD_SLEEP_PATTERN.matcher(content).find()) {
            violations.add(String.format("File %s contains forbidden Thread.sleep (Rule КЗ-3: Thread.sleep forbidden)", filename));
        }

        Matcher disabledMatcher = BARE_DISABLED_PATTERN.matcher(content);
        if (disabledMatcher.find()) {
            violations.add(String.format("File %s contains @Disabled without mandatory task reference (e.g. @Disabled(\"T-01\"))", filename));
        }

        return violations;
    }

    /**
     * Сканування всіх тестових файлів у каталозі src/test/java.
     */
    public static List<String> scanDirectory(Path directory) throws IOException {
        List<String> violations = new ArrayList<>();
        if (!Files.exists(directory)) {
            return violations;
        }

        try (Stream<Path> stream = Files.walk(directory)) {
            List<Path> javaFiles = stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    // Не скануємо сам TestScanner і тести конвенцій, де наведені фікстури-зразки
                    .filter(p -> !p.getFileName().toString().equals("TestScanner.java"))
                    .filter(p -> !p.getFileName().toString().equals("TestConventionsTest.java"))
                    .toList();

            for (Path file : javaFiles) {
                String content = Files.readString(file);
                violations.addAll(scanSourceContent(content, file.toString()));
            }
        }
        return violations;
    }

    /**
     * Витягнення всіх @Tag з тестових файлів.
     */
    public static Set<String> extractTags(Path directory) throws IOException {
        Set<String> tags = new HashSet<>();
        if (!Files.exists(directory)) {
            return tags;
        }

        try (Stream<Path> stream = Files.walk(directory)) {
            List<Path> javaFiles = stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();

            for (Path file : javaFiles) {
                String content = Files.readString(file);
                Matcher matcher = TAG_PATTERN.matcher(content);
                while (matcher.find()) {
                    tags.add(matcher.group(1));
                }
            }
        }
        return tags;
    }

    /**
     * Перевірка валідності індексу ID: кожен перевірений ID повинен бути зареєстрований у required-ids.txt.
     */
    public static List<String> findUnregisteredTags(Set<String> presentTags, Set<String> requiredIds) {
        List<String> unregistered = new ArrayList<>();
        for (String tag : presentTags) {
            // Тестові теги, як-от slow, integration, ar-* можуть бути в requiredIds або як спеціальні
            if (!requiredIds.contains(tag) && !tag.startsWith("T-") && !tag.equals("smoke") && !tag.equals("architecture")) {
                unregistered.add("Tag @" + tag + " is not registered in required-ids.txt");
            }
        }
        return unregistered;
    }
}

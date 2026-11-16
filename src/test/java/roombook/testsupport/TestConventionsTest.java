package roombook.testsupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тести конвенцій тестів та обов'язкового індексу ID (T-03, Критерій 4, КЗ-3, A-53):
 * - Самотест: фікстура з Thread.sleep падає
 * - Самотест: фікстура з @Disabled без посилання на задачу падає
 * - Самотест: незареєстрований ID падає
 * - Реальний аудит усіх тестових вихідних файлів проєкту
 */
class TestConventionsTest {

    @Test
    @Tag("A-53")
    @DisplayName("Self-test: fixture with Thread.sleep triggers failure")
    void selfTestFixtureWithThreadSleepFails() {
        String badCode = """
                class BadTest {
                    @Test
                    void someTest() throws Exception {
                        Thread.sleep(100);
                    }
                }
                """;

        List<String> violations = TestScanner.scanSourceContent(badCode, "BadTest.java");
        assertFalse(violations.isEmpty(), "Scanner must detect Thread.sleep violation");
        assertTrue(violations.stream().anyMatch(v -> v.contains("Thread.sleep")),
                "Violation must mention Thread.sleep");
    }

    @Test
    @Tag("A-53")
    @DisplayName("Self-test: fixture with @Disabled without task reference triggers failure")
    void selfTestFixtureWithBareDisabledFails() {
        String badCode = """
                class BadDisabledTest {
                    @Disabled
                    @Test
                    void brokenTest() {
                    }
                }
                """;

        List<String> violations = TestScanner.scanSourceContent(badCode, "BadDisabledTest.java");
        assertFalse(violations.isEmpty(), "Scanner must detect @Disabled without task reference");
        assertTrue(violations.stream().anyMatch(v -> v.contains("@Disabled")),
                "Violation must mention @Disabled");
    }

    @Test
    @Tag("A-53")
    @DisplayName("Self-test: fixture with valid @Disabled(\"T-01: description\") passes scanner")
    void selfTestFixtureWithValidDisabledPasses() {
        String validCode = """
                class ValidDisabledTest {
                    @Disabled("T-01: Known issue being fixed")
                    @Test
                    void temporarilyDisabledTest() {
                    }
                }
                """;

        List<String> violations = TestScanner.scanSourceContent(validCode, "ValidDisabledTest.java");
        assertTrue(violations.isEmpty(), "Valid @Disabled with task reference must not trigger violations");
    }

    @Test
    @Tag("A-53")
    @DisplayName("Self-test: unregistered tag detection triggers failure")
    void selfTestUnregisteredTagFails() {
        Set<String> requiredIds = Set.of("FR-01", "AR-01");
        Set<String> presentTags = Set.of("FR-01", "UNKNOWN-TAG-999");

        List<String> unregistered = TestScanner.findUnregisteredTags(presentTags, requiredIds);
        assertFalse(unregistered.isEmpty(), "Must detect unregistered tag");
        assertTrue(unregistered.get(0).contains("UNKNOWN-TAG-999"));
    }

    @Test
    @Tag("A-53")
    @DisplayName("Audit: all tests in project adhere to conventions (no Thread.sleep, no bare @Disabled)")
    void auditAllProjectTestsAdhereToConventions() throws IOException {
        Path testDir = Paths.get("src/test/java");
        List<String> violations = TestScanner.scanDirectory(testDir);
        assertTrue(violations.isEmpty(), "Found test violations: " + String.join("\n", violations));
    }

    @Test
    @Tag("A-53")
    @DisplayName("Audit: all tags present in test classes are registered in required-ids.txt")
    void auditAllTagsAreRegisteredInIndex() throws IOException {
        Path testDir = Paths.get("src/test/java");
        Path requiredIdsPath = Paths.get("src/test/resources/required-ids.txt");

        Set<String> requiredIds = TestScanner.loadRequiredIds(requiredIdsPath);
        Set<String> presentTags = TestScanner.extractTags(testDir);

        List<String> unregistered = TestScanner.findUnregisteredTags(presentTags, requiredIds);
        assertTrue(unregistered.isEmpty(), "Found unregistered tags in tests: " + String.join("\n", unregistered));
    }
}

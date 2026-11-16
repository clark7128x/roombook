package roombook;

import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Архітектурні тести (TS-07) для перевірки правил AR-01...AR-09, AR-13, AR-14, AR-15.
 */
@Tag("TS-07")
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("roombook");
    }

    @Test
    @Tag("AR-01")
    @DisplayName("AR-01: Layer dependencies and boundaries")
    void ar01_layerDependencies() {
        // domain не залежить від application, persistence, web у просторі імен roombook
        noClasses().that().resideInAPackage("roombook..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("roombook..application..", "roombook..persistence..", "roombook..web..")
                .check(classes);

        // persistence не залежить від application, web
        noClasses().that().resideInAPackage("roombook..persistence..")
                .should().dependOnClassesThat().resideInAnyPackage("roombook..application..", "roombook..web..")
                .check(classes);

        // application не залежить від web
        noClasses().that().resideInAPackage("roombook..application..")
                .should().dependOnClassesThat().resideInAPackage("roombook..web..")
                .check(classes);

        // web (крім accounts.security) не звертається до persistence
        noClasses().that().resideInAPackage("roombook..web..")
                .and().resideOutsideOfPackage("roombook.accounts.security..")
                .should().dependOnClassesThat().resideInAPackage("roombook..persistence..")
                .check(classes);
    }

    @Test
    @Tag("AR-02")
    @DisplayName("AR-02: Domain depends only on kernel, JDK, jakarta.persistence, and itself")
    void ar02_domainDependencies() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "org.hibernate..",
                        "jakarta.servlet.."
                )
                .check(classes);

        classes().that().resideInAPackage("..domain..")
                .should().onlyDependOnClassesThat().resideInAnyPackage(
                        "roombook.kernel..",
                        "roombook..domain..",
                        "jakarta.persistence..",
                        "java..",
                        "javax.."
                )
                .check(classes);
    }

    @Test
    @Tag("AR-03")
    @DisplayName("AR-03: Top-level module dependencies and cycle freedom")
    void ar03_moduleDependencies() {
        // Циклів між пакетами верхнього рівня немає
        slices().matching("roombook.(*)..")
                .should().beFreeOfCycles()
                .check(classes);

        // kernel не залежить від platform, scheduling, accounts
        noClasses().that().resideInAPackage("roombook.kernel..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "roombook.platform..",
                        "roombook.scheduling..",
                        "roombook.accounts.."
                )
                .check(classes);

        // platform не залежить від scheduling, accounts
        noClasses().that().resideInAPackage("roombook.platform..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "roombook.scheduling..",
                        "roombook.accounts.."
                )
                .check(classes);

        // accounts не залежить від scheduling
        noClasses().that().resideInAPackage("roombook.accounts..")
                .should().dependOnClassesThat().resideInAPackage("roombook.scheduling..")
                .check(classes);

        // scheduling залежить від accounts лише за маршрутом scheduling.application -> accounts.api
        noClasses().that().resideInAPackage("roombook.scheduling..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "roombook.accounts.domain..",
                        "roombook.accounts.application..",
                        "roombook.accounts.persistence..",
                        "roombook.accounts.security..",
                        "roombook.accounts.web..",
                        "roombook.accounts.configuration.."
                )
                .check(classes);

        noClasses().that().resideInAPackage("roombook.scheduling..")
                .and().resideOutsideOfPackage("roombook.scheduling.application..")
                .should().dependOnClassesThat().resideInAPackage("roombook.accounts..")
                .check(classes);
    }

    @Test
    @Tag("AR-04")
    @DisplayName("AR-04: Entity isolation, no public setters, minimal repositories without delete*")
    void ar04_entityAndRepositoryRules() {
        // Сутність не має публічних set*
        classes().that().areAnnotatedWith(Entity.class)
                .should(new ArchCondition<JavaClass>("not have public setters") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        for (JavaMethod method : item.getMethods()) {
                            if (method.getModifiers().contains(JavaModifier.PUBLIC) && method.getName().startsWith("set")) {
                                events.add(SimpleConditionEvent.violated(method, "Entity " + item.getName() + " has public setter: " + method.getName()));
                            }
                        }
                    }
                })
                .check(classes);

        // Сутність не залежить від іншої сутності
        classes().that().areAnnotatedWith(Entity.class)
                .should(new ArchCondition<JavaClass>("not depend on other entities") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        for (JavaClass target : item.getDirectDependenciesFromSelf().stream().map(d -> d.getTargetClass()).toList()) {
                            if (!target.equals(item) && target.isAnnotatedWith(Entity.class)) {
                                events.add(SimpleConditionEvent.violated(item, "Entity " + item.getName() + " depends on entity " + target.getName()));
                            }
                        }
                    }
                })
                .check(classes);

        // Репозиторії розширюють Spring Data Repository і не мають delete*
        classes().that().resideInAPackage("..persistence..")
                .and().haveSimpleNameEndingWith("Repository")
                .should(new ArchCondition<JavaClass>("extend Repository and not declare delete methods") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if (!item.isAssignableTo(org.springframework.data.repository.Repository.class)) {
                            events.add(SimpleConditionEvent.violated(item, item.getName() + " does not extend Spring Data Repository"));
                        }
                        for (JavaMethod method : item.getMethods()) {
                            if (method.getName().startsWith("delete")) {
                                events.add(SimpleConditionEvent.violated(method, "Repository declares delete method: " + method.getName()));
                            }
                        }
                    }
                })
                .check(classes);
    }

    @Test
    @Tag("AR-05")
    @DisplayName("AR-05: Web layer does not depend on @Entity, repositories, and api/ui do not depend on each other")
    void ar05_webDependencies() {
        noClasses().that().resideInAPackage("..web..")
                .should().dependOnClassesThat().areAnnotatedWith(Entity.class)
                .check(classes);

        noClasses().that().resideInAPackage("..web..")
                .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                .check(classes);

        noClasses().that().resideInAPackage("..web.api..")
                .should().dependOnClassesThat().resideInAPackage("..web.ui..")
                .check(classes);

        noClasses().that().resideInAPackage("..web.ui..")
                .should().dependOnClassesThat().resideInAPackage("..web.api..")
                .check(classes);
    }

    @Test
    @Tag("AR-06")
    @DisplayName("AR-06: Domain and application do not use SecurityContext, servlet, web, thymeleaf")
    void ar06_domainAndApplicationSecurityRestrictions() {
        noClasses().that().resideInAnyPackage("..domain..", "..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.security.core.context..",
                        "jakarta.servlet..",
                        "org.springframework.web..",
                        "org.thymeleaf.."
                )
                .check(classes);

        noClasses().that().resideInAnyPackage("..domain..", "..application..")
                .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.security.core.Authentication")
                .check(classes);

        // PasswordEncoder дозволений лише в accounts.application
        noClasses().that().resideInAPackage("roombook.scheduling.application..")
                .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.security.crypto.password.PasswordEncoder")
                .check(classes);
    }

    @Test
    @Tag("AR-07")
    @DisplayName("AR-07: Public methods of *Service and *Queries have Actor as first parameter")
    void ar07_actorFirstParameterInPublicMethods() {
        classes().that().resideInAPackage("..application..")
                .and().haveSimpleNameEndingWith("Service")
                .or().haveSimpleNameEndingWith("Queries")
                .should(new ArchCondition<JavaClass>("have Actor as first parameter in public methods") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        if ("CredentialsQueries".equals(item.getSimpleName())) {
                            return;
                        }
                        boolean implementsApi = item.getAllRawInterfaces().stream()
                                .anyMatch(i -> i.getPackageName().contains(".api"));
                        if (implementsApi) {
                            return;
                        }
                        for (JavaMethod method : item.getMethods()) {
                            if (method.getModifiers().contains(JavaModifier.PUBLIC)
                                    && !method.getModifiers().contains(JavaModifier.STATIC)
                                    && !method.getOwner().isEquivalentTo(Object.class)) {
                                List<JavaClass> paramTypes = method.getRawParameterTypes();
                                if (paramTypes.isEmpty() || !paramTypes.get(0).getName().equals("roombook.kernel.Actor")) {
                                    events.add(SimpleConditionEvent.violated(method,
                                            String.format("Method %s.%s must have Actor as first parameter", item.getName(), method.getName())));
                                }
                            }
                        }
                    }
                })
                .check(classes);
    }

    @Test
    @Tag("AR-08")
    @DisplayName("AR-08: System time read only via OfficeClock")
    void ar08_systemTimeOnlyViaOfficeClock() {
        classes().that().doNotHaveFullyQualifiedName("roombook.kernel.OfficeClock")
                .should(noDirectSystemTime())
                .check(classes);
    }

    private static ArchCondition<JavaClass> noDirectSystemTime() {
        return new ArchCondition<JavaClass>("not read system time directly") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        for (JavaCall<?> call : item.getMethodCallsFromSelf()) {
                            String targetOwner = call.getTargetOwner().getName();
                            String targetName = call.getTarget().getName();
                            if (targetOwner.startsWith("java.time.") && "now".equals(targetName)) {
                                events.add(SimpleConditionEvent.violated(call, "Direct call to java.time.*.now() in " + item.getName()));
                            } else if ("java.time.Clock".equals(targetOwner) && ("instant".equals(targetName) || "millis".equals(targetName))) {
                                events.add(SimpleConditionEvent.violated(call, "Direct call to Clock." + targetName + "() in " + item.getName()));
                            } else if ("java.lang.System".equals(targetOwner) && "currentTimeMillis".equals(targetName)) {
                                events.add(SimpleConditionEvent.violated(call, "Direct call to System.currentTimeMillis() in " + item.getName()));
                            }
                        }
                    }
                };
    }

    @Test
    @Tag("AR-08")
    void ar08_rejectsDirectTimeReadFixture() {
        var fixture = new ClassFileImporter().importClasses(DirectTimeRead.class);
        assertThrows(AssertionError.class, () -> classes().should(noDirectSystemTime()).check(fixture));
    }

    static class DirectTimeRead {
        java.time.LocalDate read() { return java.time.LocalDate.now(); }
    }

    @Test
    @Tag("AR-09")
    @DisplayName("AR-09: @Transactional only in application layer, *Queries is readOnly, *Service independent")
    void ar09_transactionalAndServiceDependencies() {
        // @Transactional стоїть лише в ..application..
        classes().that().areAnnotatedWith(Transactional.class)
                .should().resideInAPackage("..application..")
                .check(classes);

        methods().should(new ArchCondition<JavaMethod>("declare @Transactional only in application") {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        if (method.isAnnotatedWith(Transactional.class)
                                && !method.getOwner().getPackageName().contains(".application")) {
                            events.add(SimpleConditionEvent.violated(method, method.getFullName() + " declares @Transactional outside application"));
                        }
                    }
                })
                .check(classes);

        // *Queries має readOnly = true
        classes().that().resideInAPackage("..application..")
                .and().haveSimpleNameEndingWith("Queries")
                .and().areAnnotatedWith(Transactional.class)
                .should(new ArchCondition<JavaClass>("have @Transactional(readOnly = true)") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        Transactional tx = item.getAnnotationOfType(Transactional.class);
                        if (tx != null && !tx.readOnly()) {
                            events.add(SimpleConditionEvent.violated(item, item.getName() + " must have readOnly = true in @Transactional"));
                        }
                    }
                })
                .check(classes);

        // *Service не залежить від інших *Service
        classes().that().resideInAPackage("..application..")
                .and().haveSimpleNameEndingWith("Service")
                .should(new ArchCondition<JavaClass>("not depend on other *Service classes") {
                    @Override
                    public void check(JavaClass item, ConditionEvents events) {
                        for (JavaClass dep : item.getDirectDependenciesFromSelf().stream().map(d -> d.getTargetClass()).toList()) {
                            if (!dep.equals(item) && dep.getSimpleName().endsWith("Service") && dep.getPackageName().contains(".application")) {
                                events.add(SimpleConditionEvent.violated(item, item.getName() + " depends on service " + dep.getName()));
                            }
                        }
                    }
                })
                .check(classes);
    }

    @Test
    @Tag("AR-13")
    @DisplayName("AR-13: Templates must not contain inline scripts, styles, event handlers or js: expressions")
    void ar13_templatesDoNotContainInlineScriptsOrStyles() throws IOException {
        Path templatesDir = Paths.get("src/main/resources/templates");
        assertFalse(!Files.isDirectory(templatesDir), "Templates directory must exist");
        try (Stream<Path> paths = Files.walk(templatesDir)) {
            List<Path> htmlFiles = paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".html"))
                    .toList();
            assertFalse(htmlFiles.isEmpty(), "Templates selection must not be empty");
            for (Path file : htmlFiles) {
                String content = Files.readString(file);
                var scripts = java.util.regex.Pattern.compile("(?is)<script\\b([^>]*)>(.*?)</script>").matcher(content);
                while (scripts.find()) {
                    assertFalse(!scripts.group(1).matches("(?s).*\\b(?:th:)?src\\s*=.*") || !scripts.group(2).isBlank(),
                            "Inline <script> found in " + file);
                }
                assertFalse(content.matches("(?s).*\\bstyle\\s*=.*"), "Inline style attribute found in " + file);
                assertFalse(content.matches("(?s).*\\bon[a-zA-Z]+\\s*=.*"), "Inline on* handler found in " + file);
                assertFalse(content.matches("(?s).*\\bhx-on[a-zA-Z:]*\\s*=.*"), "Inline hx-on* found in " + file);
                assertFalse(content.contains("js:"), "Inline js: found in " + file);
            }
        }
    }

    @Test
    @Tag("AR-14")
    @DisplayName("AR-14: @GetMapping methods do not call *Service")
    void ar14_getMappingMethodsDoNotCallService() {
        methods().that().areAnnotatedWith(GetMapping.class)
                .should(new ArchCondition<JavaMethod>("not call any *Service methods") {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        for (JavaCall<?> call : method.getMethodCallsFromSelf()) {
                            String targetOwner = call.getTargetOwner().getSimpleName();
                            if (targetOwner.endsWith("Service")) {
                                events.add(SimpleConditionEvent.violated(call,
                                        String.format("Method %s annotated with @GetMapping calls service %s.%s",
                                                method.getFullName(), targetOwner, call.getTarget().getName())));
                            }
                        }
                    }
                })
                .check(classes);
    }

    @Test
    @Tag("AR-15")
    @DisplayName("AR-15: Kernel is independent from Spring, Hibernate, servlet, and platform")
    void ar15_kernelFrameworkIndependence() {
        noClasses().that().resideInAPackage("roombook.kernel..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "org.hibernate..",
                        "jakarta.servlet..",
                        "roombook.platform..",
                        "roombook.scheduling..",
                        "roombook.accounts.."
                )
                .check(classes);
    }
}

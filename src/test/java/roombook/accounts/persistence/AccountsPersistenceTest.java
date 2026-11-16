package roombook.accounts.persistence;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import roombook.accounts.domain.AppUser;
import roombook.kernel.DomainException;
import roombook.kernel.Reason;
import roombook.kernel.Role;
import roombook.testsupport.InterleavingUtility;
import roombook.testsupport.PostgresContainerHolder;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Інтеграційні тести збереження модуля accounts (T-12, D-04, D-09, INV-13, INV-14, AR-04, AR-10, AR-12, TS-15).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class AccountsPersistenceTest {

    @DynamicPropertySource
    static void configureDatabaseProperties(DynamicPropertyRegistry registry) {
        PostgreSQLContainer<?> c = PostgresContainerHolder.getInstance();
        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/roombook", c.getHost(), c.getMappedPort(5432));
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", () -> "roombook_app");
        registry.add("spring.datasource.password", () -> "app_pass");
        registry.add("spring.flyway.url", () -> jdbcUrl);
        registry.add("spring.flyway.user", () -> "roombook_owner");
        registry.add("spring.flyway.password", () -> "owner_pass");
    }

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Environment environment;

    private TransactionTemplate txTemplate;

    @BeforeEach
    void setUp() {
        PostgresContainerHolder.cleanDatabase();
        txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    @Tag("TS-15")
    @Tag("D-09")
    @DisplayName("TS-15: AppUser entity mapping roundtrip without shift")
    void appUserMappingRoundTrip() {
        AppUser saved = txTemplate.execute(status -> {
            AppUser u = new AppUser("john.doe", "John Doe", "$2a$12$someBcryptHashValueForTest00000000000000000000000000000", Role.ADMIN);
            return appUserRepository.saveAndFlush(u);
        });

        assertNotNull(saved);
        Long id = saved.getId();
        assertNotNull(id);

        txTemplate.execute(status -> {
            entityManager.clear();
            AppUser read = appUserRepository.findById(id).orElseThrow();
            assertEquals("john.doe", read.getUsername());
            assertEquals("john.doe", read.getUsernameKey());
            assertEquals("John Doe", read.getDisplayName());
            assertEquals(Role.ADMIN, read.getRole());
            assertTrue(read.isEnabled());
            assertEquals("$2a$12$someBcryptHashValueForTest00000000000000000000000000000", read.getPasswordHash());
            return null;
        });
    }

    @Test
    @Tag("AR-10")
    @Tag("D-09")
    @DisplayName("AR-10: OSIV is disabled and Hibernate schema validation passes for AppUser")
    void osivDisabledAndHibernateValidationPasses() {
        Boolean osivEnabled = environment.getProperty("spring.jpa.open-in-view", Boolean.class);
        assertEquals(Boolean.FALSE, osivEnabled, "OSIV must be disabled");

        String ddlAuto = environment.getProperty("spring.jpa.hibernate.ddl-auto");
        assertEquals("validate", ddlAuto);

        assertNotNull(entityManager);
        assertTrue(entityManager.isOpen());
    }

    @Test
    @Tag("D-04")
    @Tag("INV-13")
    @DisplayName("D-04: AppUserRepository.lockById blocks concurrent transaction until commit")
    void lockByIdBlocksConcurrentTransaction() throws Exception {
        AppUser user = txTemplate.execute(status ->
                appUserRepository.saveAndFlush(new AppUser("lock.user", "Lock User", "$2a$12$someValidHash00000000000000000000000000000000000000000000000", Role.EMPLOYEE))
        );
        Long userId = user.getId();

        AppUser locked = InterleavingUtility.runWithRowLock(
                "app_user",
                "id = " + userId,
                holderConn -> {
                    try (Statement stmt = holderConn.createStatement()) {
                        stmt.execute("UPDATE app_user SET display_name = 'Modified by Holder' WHERE id = " + userId);
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                },
                () -> txTemplate.execute(status -> {
                    try {
                        entityManager.createNativeQuery("SET application_name = '" + InterleavingUtility.TARGET_APP_NAME + "'").executeUpdate();
                        return appUserRepository.lockById(userId).orElseThrow();
                    } finally {
                        entityManager.createNativeQuery("SET application_name = 'roombook-app'").executeUpdate();
                    }
                })
        );

        assertNotNull(locked);
        assertEquals(userId, locked.getId());
        assertEquals("Modified by Holder", locked.getDisplayName());
    }

    @Test
    @Tag("D-04")
    @Tag("INV-14")
    @DisplayName("D-04, INV-14: lockWithActiveAdmins returns target user and all active admins in strict id ASC order")
    void lockWithActiveAdminsReturnsInIdOrder() {
        txTemplate.execute(status -> {
            AppUser emp = new AppUser("employee1", "Employee One", "$2a$12$hash1111111111111111111111111111111111111111111111111111111", Role.EMPLOYEE);
            AppUser adm1 = new AppUser("admin1", "Admin One", "$2a$12$hash2222222222222222222222222222222222222222222222222222222", Role.ADMIN);
            AppUser adm2 = new AppUser("admin2", "Admin Two", "$2a$12$hash3333333333333333333333333333333333333333333333333333333", Role.ADMIN);
            AppUser admDisabled = new AppUser("admin.disabled", "Admin Disabled", "$2a$12$hash4444444444444444444444444444444444444444444444444444444", Role.ADMIN);
            admDisabled.disable();

            appUserRepository.saveAndFlush(emp);
            appUserRepository.saveAndFlush(adm1);
            appUserRepository.saveAndFlush(adm2);
            appUserRepository.saveAndFlush(admDisabled);
            return null;
        });

        txTemplate.execute(status -> {
            entityManager.clear();
            AppUser emp = appUserRepository.findByUsernameKey("employee1").orElseThrow();
            AppUser adm1 = appUserRepository.findByUsernameKey("admin1").orElseThrow();
            AppUser adm2 = appUserRepository.findByUsernameKey("admin2").orElseThrow();
            AppUser admDisabled = appUserRepository.findByUsernameKey("admin.disabled").orElseThrow();

            // Випадок 1: Ціль - звичайний Employee. Має повернути Employee + 2 активних Admin у порядку id
            List<AppUser> lockedForEmp = appUserRepository.lockWithActiveAdmins(emp.getId());
            assertEquals(3, lockedForEmp.size());
            assertEquals(List.of(emp.getId(), adm1.getId(), adm2.getId()),
                    lockedForEmp.stream().map(AppUser::getId).toList(),
                    "Результат має бути впорядкований за зростанням id");
            assertTrue(lockedForEmp.stream().noneMatch(u -> u.getId().equals(admDisabled.getId())),
                    "Вимкнений адмін не повинен потрапляти у вибірку активних адмінів");

            // Випадок 2: Ціль - один з активних Admin. Має повернути рівно 2 активних Admin без дублювання
            List<AppUser> lockedForAdm1 = appUserRepository.lockWithActiveAdmins(adm1.getId());
            assertEquals(2, lockedForAdm1.size());
            assertEquals(List.of(adm1.getId(), adm2.getId()),
                    lockedForAdm1.stream().map(AppUser::getId).toList());

            // Випадок 3: Ціль - вимкнений Admin. Має повернути його + 2 активних Admin
            List<AppUser> lockedForDisabledAdm = appUserRepository.lockWithActiveAdmins(admDisabled.getId());
            assertEquals(3, lockedForDisabledAdm.size());
            assertEquals(List.of(adm1.getId(), adm2.getId(), admDisabled.getId()),
                    lockedForDisabledAdm.stream().map(AppUser::getId).toList());

            return null;
        });
    }

    @Test
    @Tag("D-04")
    @Tag("INV-14")
    @DisplayName("D-04, INV-14: Query plan for lockWithActiveAdmins includes LockRows and sorting by id")
    void lockWithActiveAdminsQueryPlanVerification() throws SQLException {
        try (Connection conn = PostgresContainerHolder.getAppConnection();
             Statement stmt = conn.createStatement()) {

            String explainSql = "EXPLAIN (FORMAT TEXT) " +
                    "SELECT u.id, u.role, u.enabled FROM app_user u " +
                    "WHERE u.id = 1 OR (u.role = 'ADMIN' AND u.enabled = true) " +
                    "ORDER BY u.id ASC FOR UPDATE";

            List<String> planLines = new ArrayList<>();
            try (ResultSet rs = stmt.executeQuery(explainSql)) {
                while (rs.next()) {
                    planLines.add(rs.getString(1));
                }
            }

            String fullPlan = String.join("\n", planLines);
            assertTrue(fullPlan.contains("LockRows"), "План запиту має містити вузол LockRows для блокування рядків: " + fullPlan);
            assertTrue(fullPlan.contains("Sort") || fullPlan.contains("Index Scan"),
                    "План запиту має гарантувати порядок сортування (Sort або Index Scan): " + fullPlan);
        }
    }

    @Test
    @Tag("D-04")
    @Tag("INV-14")
    @DisplayName("D-04, INV-14: Concurrent lockWithActiveAdmins across two transactions does not deadlock")
    void concurrentLockWithActiveAdminsDoesNotDeadlock() throws Exception {
        Long admin1Id = txTemplate.execute(status -> {
            AppUser a1 = new AppUser("admin.first", "First Admin", "$2a$12$hash1111111111111111111111111111111111111111111111111111111", Role.ADMIN);
            return appUserRepository.saveAndFlush(a1).getId();
        });

        Long admin2Id = txTemplate.execute(status -> {
            AppUser a2 = new AppUser("admin.second", "Second Admin", "$2a$12$hash2222222222222222222222222222222222222222222222222222222", Role.ADMIN);
            return appUserRepository.saveAndFlush(a2).getId();
        });

        assertTrue(admin1Id < admin2Id, "ID першого адміна має бути меншим за ID другого");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch tx1Locked = new CountDownLatch(1);
        CountDownLatch finishSignal = new CountDownLatch(1);

        try {
            // Транзакція 1: ціль admin1Id (блокує admin1Id потім admin2Id)
            Future<List<Long>> future1 = executor.submit(() -> txTemplate.execute(status -> {
                List<AppUser> locked = appUserRepository.lockWithActiveAdmins(admin1Id);
                tx1Locked.countDown();
                try {
                    // Чекаємо сигналу на коміт
                    finishSignal.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return locked.stream().map(AppUser::getId).toList();
            }));

            // Чекаємо, поки транзакція 1 візьме блокування
            assertTrue(tx1Locked.await(5, TimeUnit.SECONDS));

            // Транзакція 2: ціль admin2Id (також намагається блокувати в порядку id: admin1Id потім admin2Id)
            Future<List<Long>> future2 = executor.submit(() -> txTemplate.execute(status -> {
                List<AppUser> locked = appUserRepository.lockWithActiveAdmins(admin2Id);
                return locked.stream().map(AppUser::getId).toList();
            }));

            // Відпускаємо транзакцію 1 на коміт
            finishSignal.countDown();

            List<Long> result1 = future1.get(5, TimeUnit.SECONDS);
            List<Long> result2 = future2.get(5, TimeUnit.SECONDS);

            assertEquals(List.of(admin1Id, admin2Id), result1);
            assertEquals(List.of(admin1Id, admin2Id), result2);

        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Tag("INV-13")
    @DisplayName("INV-13: findByUsernameKey searches user by case-insensitive normalized key")
    void findByUsernameKeyReturnsUser() {
        txTemplate.execute(status -> {
            AppUser user = new AppUser("Petro.Poroshenko", "Petro Poroshenko", "$2a$12$hash1111111111111111111111111111111111111111111111111111111", Role.EMPLOYEE);
            return appUserRepository.saveAndFlush(user);
        });

        txTemplate.execute(status -> {
            // Ключ обчислено як "petro.poroshenko"
            Optional<AppUser> found = appUserRepository.findByUsernameKey("petro.poroshenko");
            assertTrue(found.isPresent());
            assertEquals("Petro.Poroshenko", found.get().getUsername());

            // Неіснуючий ключ
            Optional<AppUser> notFound = appUserRepository.findByUsernameKey("unknown.key");
            assertTrue(notFound.isEmpty());
            return null;
        });
    }

    @Test
    @Tag("AR-12")
    @Tag("INV-13")
    @DisplayName("AR-12, INV-13: Duplicate username key throws USERNAME_TAKEN and rolls back cleanly")
    void duplicateUsernameKeyThrowsUsernameTakenAndRollsBack() {
        // 1. Зберігаємо першого користувача
        txTemplate.execute(status -> {
            AppUser u1 = new AppUser("alice", "Alice Cooper", "$2a$12$hash1111111111111111111111111111111111111111111111111111111", Role.EMPLOYEE);
            return appUserRepository.saveAndFlush(u1);
        });

        // 2. Спроба створити користувача з логіном "ALICE" (однаковий username_key = "alice")
        DomainException ex = assertThrows(DomainException.class, () ->
                txTemplate.execute(status -> {
                    AppUser duplicate = new AppUser("ALICE", "Alice Second", "$2a$12$hash2222222222222222222222222222222222222222222222222222222", Role.EMPLOYEE);
                    try {
                        return appUserRepository.saveAndFlush(duplicate);
                    } catch (Throwable t) {
                        status.setRollbackOnly();
                        throw AccountsConstraints.translate(t);
                    }
                })
        );

        assertEquals(Reason.USERNAME_TAKEN, ex.getReason());

        // 3. Перевіряємо, що транзакція відкотилася: у базі рівно один користувач із цим ключем
        Long count = txTemplate.execute(status ->
                entityManager.createQuery("SELECT COUNT(u) FROM AppUser u WHERE u.usernameKey = :k", Long.class)
                        .setParameter("k", "alice")
                        .getSingleResult()
        );
        assertEquals(1L, count);
    }

    @Test
    @Tag("AR-12")
    @DisplayName("AR-12: AccountsConstraints utility methods translate properly")
    void accountsConstraintsUtilityTests() {
        String res = AccountsConstraints.execute(() -> "accounts_ok");
        assertEquals("accounts_ok", res);

        assertDoesNotThrow(() -> AccountsConstraints.execute(() -> {}));

        RuntimeException normalEx = assertThrows(RuntimeException.class, () ->
                AccountsConstraints.execute(() -> {
                    throw new IllegalArgumentException("Generic error");
                })
        );
        assertEquals("Generic error", normalEx.getMessage());
    }
}

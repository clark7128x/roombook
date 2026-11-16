package roombook.accounts.application;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import roombook.accounts.domain.AppUser;
import roombook.accounts.persistence.AppUserRepository;
import roombook.kernel.Actor;
import roombook.kernel.DomainException;
import roombook.kernel.Reason;
import roombook.kernel.Role;
import roombook.testsupport.PostgresContainerHolder;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Інтеграційні тести конкуренції модуля accounts (T-13, TS-19, CS-09, CS-10, CS-11, INV-14, D-04).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Tag("TS-19")
class AccountConcurrencyTest {

    @DynamicPropertySource
    static void configureDatabaseProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
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
    private AccountService accountService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    private TransactionTemplate txTemplate;
    private final Actor adminActor = new Actor(1L, Role.ADMIN);

    @BeforeEach
    void setUp() {
        PostgresContainerHolder.cleanDatabase();
        txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    private Long createAdmin(String username, String displayName) {
        return txTemplate.execute(status -> {
            AppUser u = new AppUser(username, displayName, "$2a$12$hash1111111111111111111111111111111111111111111111111111111", Role.ADMIN);
            return appUserRepository.saveAndFlush(u).getId();
        });
    }

    private Long createEmployee(String username, String displayName) {
        return txTemplate.execute(status -> {
            AppUser u = new AppUser(username, displayName, "$2a$12$hash1111111111111111111111111111111111111111111111111111111", Role.EMPLOYEE);
            return appUserRepository.saveAndFlush(u).getId();
        });
    }

    @Test
    @Tag("CS-09")
    @Tag("INV-14")
    @Tag("D-04")
    @DisplayName("CS-09: Утримання блокування двох останніх ADMIN; паралельний disableUser над одним чекає, отримує LAST_ADMIN після коміту вимкнення іншого")
    void cs09_disableLastAdminDuringConcurrentLock() throws Exception {
        Long admin1Id = createAdmin("admin.one", "Admin One");
        Long admin2Id = createAdmin("admin.two", "Admin Two");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch tx1HoldingLock = new CountDownLatch(1);
        CountDownLatch tx2Started = new CountDownLatch(1);
        AtomicReference<Throwable> tx2Error = new AtomicReference<>();
        AtomicReference<UserView> tx2Result = new AtomicReference<>();

        try {
            // Транзакція 1: бере блокування через lockWithActiveAdmins і тримає його
            Future<?> tx1Future = executor.submit(() -> {
                txTemplate.execute(status -> {
                    List<AppUser> locked = appUserRepository.lockWithActiveAdmins(admin1Id);
                    assertEquals(2, locked.size());

                    // Сигналізуємо, що блокування взято
                    tx1HoldingLock.countDown();

                    // Чекаємо, поки потік 2 почне виклик disableUser
                    try {
                        tx2Started.await(5, TimeUnit.SECONDS);
                        // Невелика пауза в блокуванні, щоб потік 2 точно дійшов до очікування блокування рядка в Postgres
                        java.util.concurrent.locks.LockSupport.parkNanos(100_000_000L); // 100 мс
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    // Вимикаємо admin2 всередині транзакції 1
                    AppUser admin2 = locked.stream().filter(u -> u.getId().equals(admin2Id)).findFirst().orElseThrow();
                    admin2.disable();
                    appUserRepository.saveAndFlush(admin2);

                    return null;
                });
            });

            // Чекаємо, поки транзакція 1 заблокує адмінів
            assertTrue(tx1HoldingLock.await(5, TimeUnit.SECONDS));

            // Транзакція 2: намагається вимкнути admin1
            Future<?> tx2Future = executor.submit(() -> {
                tx2Started.countDown();
                try {
                    UserView view = accountService.disableUser(adminActor, admin1Id);
                    tx2Result.set(view);
                } catch (Throwable t) {
                    tx2Error.set(t);
                }
            });

            tx1Future.get(5, TimeUnit.SECONDS);
            tx2Future.get(5, TimeUnit.SECONDS);

            // Перевіряємо помилку транзакції 2: вона повинна отримати LAST_ADMIN
            assertNotNull(tx2Error.get(), "Транзакція 2 мала завершитися помилкою");
            assertInstanceOf(DomainException.class, tx2Error.get());
            DomainException de = (DomainException) tx2Error.get();
            assertEquals(Reason.LAST_ADMIN, de.getReason());

            // Перевіряємо стан у БД: admin1 лишився активним ADMIN, admin2 вимкнено (INV-14 дотримано)
            txTemplate.execute(status -> {
                AppUser a1 = appUserRepository.findById(admin1Id).orElseThrow();
                AppUser a2 = appUserRepository.findById(admin2Id).orElseThrow();

                assertTrue(a1.isEnabled(), "Admin 1 має залишитися активним");
                assertEquals(Role.ADMIN, a1.getRole());

                assertFalse(a2.isEnabled(), "Admin 2 має бути вимкненим");
                return null;
            });

        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Tag("CS-10")
    @Tag("INV-14")
    @Tag("D-04")
    @DisplayName("CS-10: 200 повторів конкурентних пар disableUser / changeUserRole між двома останніми ADMIN: рівно один успіх, один LAST_ADMIN, без deadlock (40P01)")
    void cs10_concurrentAdminDemotionsNoDeadlockAndInvariantPreserved() throws Exception {
        int iterations = 200;
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            for (int i = 0; i < iterations; i++) {
                final int iteration = i;

                // Перед кожною ітерацією вимикаємо адмінів з попередньої ітерації,
                // щоб гарантувати умову CS-10: "двоє останніх активних ADMIN"
                txTemplate.execute(status -> {
                    entityManager.createQuery("UPDATE AppUser u SET u.enabled = false WHERE u.enabled = true").executeUpdate();
                    return null;
                });

                // Створюємо двох свіжих єдиних активних адмінів
                final Long idA = createAdmin("admin.a." + i, "Admin A " + i);
                final Long idB = createAdmin("admin.b." + i, "Admin B " + i);

                final int mode = i % 3; // 0: disable/disable, 1: role/role, 2: disable/role

                CyclicBarrier barrier = new CyclicBarrier(2);
                AtomicInteger successCount = new AtomicInteger(0);
                AtomicInteger lastAdminCount = new AtomicInteger(0);
                AtomicReference<Throwable> otherError = new AtomicReference<>();

                Callable<Void> taskA = () -> {
                    try {
                        barrier.await();
                        if (mode == 0 || mode == 2) {
                            accountService.disableUser(adminActor, idA);
                        } else {
                            accountService.changeUserRole(adminActor, idA, new ChangeUserRoleCommand(Role.EMPLOYEE));
                        }
                        successCount.incrementAndGet();
                    } catch (DomainException de) {
                        if (de.getReason() == Reason.LAST_ADMIN) {
                            lastAdminCount.incrementAndGet();
                        } else {
                            otherError.set(de);
                        }
                    } catch (Throwable t) {
                        otherError.set(t);
                    }
                    return null;
                };

                Callable<Void> taskB = () -> {
                    try {
                        barrier.await();
                        if (mode == 0) {
                            accountService.disableUser(adminActor, idB);
                        } else {
                            accountService.changeUserRole(adminActor, idB, new ChangeUserRoleCommand(Role.EMPLOYEE));
                        }
                        successCount.incrementAndGet();
                    } catch (DomainException de) {
                        if (de.getReason() == Reason.LAST_ADMIN) {
                            lastAdminCount.incrementAndGet();
                        } else {
                            otherError.set(de);
                        }
                    } catch (Throwable t) {
                        otherError.set(t);
                    }
                    return null;
                };

                Future<Void> fA = executor.submit(taskA);
                Future<Void> fB = executor.submit(taskB);

                fA.get(5, TimeUnit.SECONDS);
                fB.get(5, TimeUnit.SECONDS);

                if (otherError.get() != null) {
                    fail("Неочікувана помилка в ітерації " + iteration + ": " + otherError.get().getMessage(), otherError.get());
                }

                assertEquals(1, successCount.get(), "В ітерації " + iteration + " мав успішно завершитися рівно один запит");
                assertEquals(1, lastAdminCount.get(), "В ітерації " + iteration + " рівно один запит мав отримати LAST_ADMIN");

                // Перевіряємо збереження інваріанта INV-14 у БД
                txTemplate.execute(status -> {
                    AppUser uA = appUserRepository.findById(idA).orElseThrow();
                    AppUser uB = appUserRepository.findById(idB).orElseThrow();

                    long activeAdminCount = 0;
                    if (uA.getRole() == Role.ADMIN && uA.isEnabled()) activeAdminCount++;
                    if (uB.getRole() == Role.ADMIN && uB.isEnabled()) activeAdminCount++;

                    assertEquals(1, activeAdminCount, "В ітерації " + iteration + " мав залишитися рівно 1 активний ADMIN");
                    return null;
                });
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Tag("CS-11")
    @Tag("D-04")
    @DisplayName("CS-11: Конкурентні resetPassword і disableUser над одним користувачем: блокування рядка усуває lost update (enabled лишається false)")
    void cs11_resetPasswordAndDisableUserNoLostUpdate() throws Exception {
        final Long targetId = createEmployee("target.emp", "Target Employee");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch tx1Locked = new CountDownLatch(1);
        CountDownLatch tx2Started = new CountDownLatch(1);

        try {
            // Транзакція 1: блокує рядок користувача, чекає старту resetPassword, потім вимикає й комітить
            Future<?> tx1Future = executor.submit(() -> {
                txTemplate.execute(status -> {
                    AppUser target = appUserRepository.lockById(targetId).orElseThrow();
                    tx1Locked.countDown();

                    try {
                        tx2Started.await(5, TimeUnit.SECONDS);
                        java.util.concurrent.locks.LockSupport.parkNanos(100_000_000L); // 100 мс
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    target.disable();
                    appUserRepository.saveAndFlush(target);
                    return null;
                });
            });

            assertTrue(tx1Locked.await(5, TimeUnit.SECONDS));

            // Транзакція 2: викликає resetPassword
            Future<?> tx2Future = executor.submit(() -> {
                tx2Started.countDown();
                return accountService.resetPassword(adminActor, targetId, new ResetPasswordCommand("freshPassword123"));
            });

            tx1Future.get(5, TimeUnit.SECONDS);
            tx2Future.get(5, TimeUnit.SECONDS);

            // Перевіряємо в БД: enabled лишається false, а пароль змінено
            txTemplate.execute(status -> {
                AppUser target = appUserRepository.findById(targetId).orElseThrow();
                assertFalse(target.isEnabled(), "enabled має залишатися false (деактивацію не перетерто)");
                assertNotEquals("$2a$12$hash1111111111111111111111111111111111111111111111111111111", target.getPasswordHash());
                return null;
            });

        } finally {
            executor.shutdownNow();
        }
    }
}

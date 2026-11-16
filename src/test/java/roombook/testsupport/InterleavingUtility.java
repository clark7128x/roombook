package roombook.testsupport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/**
 * Утиліта детермінованого чергування транзакцій за правилом D-24 (Варіант B) та T-03.
 * Утримує блокування рядка у власній транзакції, запускає операцію в окремому потоці,
 * чекає появи очікування блокування в pg_stat_activity, знімає блокування й повертає результат.
 */
public final class InterleavingUtility {

    public static final String HOLDER_APP_NAME = "interleaver-holder";
    public static final String MONITOR_APP_NAME = "interleaver-monitor";
    public static final String TARGET_APP_NAME = "interleaver-target";
    private static final Duration DEFAULT_DEADLINE = Duration.ofSeconds(5);

    private InterleavingUtility() {
    }

    /**
     * Виконує операцію з гарантованим чергуванням блокувань рядка.
     */
    public static <T> T runWithRowLock(
            String tableName,
            String whereClause,
            Consumer<Connection> actionBeforeCommit,
            Callable<T> concurrentOperation
    ) throws Exception {
        return runWithRowLock(tableName, whereClause, DEFAULT_DEADLINE, actionBeforeCommit, concurrentOperation);
    }

    public static <T> T runWithRowLock(
            String tableName,
            String whereClause,
            Duration deadlineDuration,
            Consumer<Connection> actionBeforeCommit,
            Callable<T> concurrentOperation
    ) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection holderConn = PostgresContainerHolder.getAppConnection(HOLDER_APP_NAME);
             Connection monitorConn = PostgresContainerHolder.getAppConnection(MONITOR_APP_NAME)) {

            holderConn.setAutoCommit(false);
            monitorConn.setAutoCommit(true);

            // 1. Утримувач блокує рядок
            String lockSql = "SELECT * FROM " + tableName + " WHERE " + whereClause + " FOR UPDATE";
            try (PreparedStatement ps = holderConn.prepareStatement(lockSql);
                 ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("No row found to lock with query: " + lockSql);
                }
            }

            // 2. Запуск паралельної операції в окремому потоці
            CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return concurrentOperation.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, executor);

            // 3. Опитування pg_stat_activity через monitorConn без Thread.sleep до дедлайну
            long deadlineNanos = System.nanoTime() + deadlineDuration.toNanos();
            boolean waitingForLock = false;

            while (System.nanoTime() < deadlineNanos) {
                if (isTargetWaitingForLock(monitorConn, TARGET_APP_NAME)) {
                    waitingForLock = true;
                    break;
                }
                if (future.isDone()) {
                    // Операція завершилась раніше, не чекаючи блокування
                    break;
                }
                LockSupport.parkNanos(1_000_000L); // 1 мс пауза очікування
            }

            if (!waitingForLock) {
                holderConn.rollback();
                if (future.isDone()) {
                    throw new IllegalStateException("Operation finished without waiting for row lock in pg_stat_activity");
                }
                throw new TimeoutException("Operation did not wait for row lock within deadline: " + deadlineDuration);
            }

            // 4. Виконання дії перед комітом (якщо задано) та зняття блокування
            if (actionBeforeCommit != null) {
                actionBeforeCommit.accept(holderConn);
            }
            holderConn.commit();

            // 5. Отримання результату операції
            try {
                return future.get(deadlineDuration.toMillis(), TimeUnit.MILLISECONDS);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof Exception ex) {
                    throw ex;
                }
                throw e;
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Перевірка наявності активного очікування блокування в pg_stat_activity для цільової сесії.
     */
    public static boolean isTargetWaitingForLock(Connection conn, String targetAppName) throws SQLException {
        String query = "SELECT count(*) FROM pg_stat_activity " +
                "WHERE application_name = ? " +
                "AND wait_event_type = 'Lock' " +
                "AND state = 'active'";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, targetAppName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    /**
     * Бар'єр для одночасного старту N потоків (D-24, B).
     */
    public static CyclicBarrier createStartBarrier(int threadCount) {
        return new CyclicBarrier(threadCount);
    }
}

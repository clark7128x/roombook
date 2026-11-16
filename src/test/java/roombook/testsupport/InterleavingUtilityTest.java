package roombook.testsupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Самотест утиліти чергування (T-03, Критерій 2):
 * - Операція чекає до коміту утримувача
 * - Якщо блокування немає, утиліта падає з помилкою очікування, а не зависає
 */
class InterleavingUtilityTest {

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection conn = PostgresContainerHolder.getOwnerConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS test_interleaving");
            stmt.execute("CREATE TABLE test_interleaving (id int primary key, val varchar(50))");
            stmt.execute("INSERT INTO test_interleaving (id, val) VALUES (1, 'initial')");
        }
    }

    @AfterEach
    void tearDown() throws SQLException {
        try (Connection conn = PostgresContainerHolder.getOwnerConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS test_interleaving");
        }
    }

    @Test
    @Tag("CS-01")
    @DisplayName("Self-test: operation waits for holder's row lock and completes after commit")
    void interleavingWaitsForLockAndCompletes() throws Exception {
        String result = InterleavingUtility.runWithRowLock(
                "test_interleaving",
                "id = 1",
                Duration.ofSeconds(4),
                holderConn -> {
                    // Утримувач бачить, що цільова сесія заблокована, і змінює стан перед комітом
                    try (PreparedStatement ps = holderConn.prepareStatement(
                            "UPDATE test_interleaving SET val = 'modified_by_holder' WHERE id = 1")) {
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                },
                () -> {
                    try (Connection targetConn = PostgresContainerHolder.getAppConnection(InterleavingUtility.TARGET_APP_NAME)) {
                        targetConn.setAutoCommit(true);
                        try (PreparedStatement ps = targetConn.prepareStatement(
                                "UPDATE test_interleaving SET val = 'updated_by_target' WHERE id = 1 RETURNING val")) {
                            try (ResultSet rs = ps.executeQuery()) {
                                rs.next();
                                return rs.getString(1);
                            }
                        }
                    }
                }
        );

        assertEquals("updated_by_target", result);

        // Перевіряємо кінцеве значення в БД
        try (Connection conn = PostgresContainerHolder.getAppConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT val FROM test_interleaving WHERE id = 1")) {
            assertTrue(rs.next());
            assertEquals("updated_by_target", rs.getString(1));
        }
    }

    @Test
    @Tag("CS-01")
    @DisplayName("Self-test: fails with timeout error and does not hang when no lock is requested")
    void interleavingFailsWhenTargetDoesNotRequestLock() {
        // Цільова дія взагалі не чіпає заблокований рядок і не чекає на блокування
        Exception ex = assertThrows(Exception.class, () ->
                InterleavingUtility.runWithRowLock(
                        "test_interleaving",
                        "id = 1",
                        Duration.ofMillis(800), // Короткий дедлайн для швидкого падіння
                        null,
                        () -> {
                            // Операція завершується відразу без взяття блокування
                            return "finished_without_waiting";
                        }
                )
        );

        assertTrue(ex instanceof TimeoutException || ex instanceof IllegalStateException,
                "Expected TimeoutException or IllegalStateException when operation does not wait for lock, got: " + ex);
    }
}

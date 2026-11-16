package roombook.testsupport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke-тест тестового середовища (T-03, Критерій 1):
 * - Контейнер PostgreSQL 16 стартує
 * - Ролі roombook_owner, roombook_app, roombook_backup створені
 * - roombook_app не може DELETE і DDL на таблиці
 * - Таймаути ролі мають значення A-43 (lock_timeout = 5s, statement_timeout = 10s)
 */
class SmokeEnvironmentTest {

    @Test
    @Tag("OPS-10")
    @DisplayName("Smoke: PostgreSQL container starts, roles created, permissions and timeouts match A-43")
    void smokePostgresEnvironmentAndPermissions() throws SQLException {
        // 1. Перевірка створення ролей
        try (Connection conn = PostgresContainerHolder.getSuperuserConnection(
                PostgresContainerHolder.getInstance(), "roombook");
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery(
                    "SELECT rolname FROM pg_roles WHERE rolname IN ('roombook_owner', 'roombook_app', 'roombook_backup')"
            );
            int roleCount = 0;
            while (rs.next()) {
                roleCount++;
            }
            assertEquals(3, roleCount, "All 3 roles must be created");
        }

        // 2. Створення тестової таблиці власником (roombook_owner)
        try (Connection ownerConn = PostgresContainerHolder.getOwnerConnection();
             Statement stmt = ownerConn.createStatement()) {

            stmt.execute("DROP TABLE IF EXISTS test_smoke_permissions");
            stmt.execute("CREATE TABLE test_smoke_permissions (id serial primary key, name varchar(50))");
            stmt.execute("INSERT INTO test_smoke_permissions (name) VALUES ('initial')");
        }

        // 3. Перевірка дозволених та заборонених операцій для roombook_app
        try (Connection appConn = PostgresContainerHolder.getAppConnection();
             Statement stmt = appConn.createStatement()) {

            // SELECT дозволено
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM test_smoke_permissions")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }

            // INSERT дозволено
            stmt.executeUpdate("INSERT INTO test_smoke_permissions (name) VALUES ('added_by_app')");

            // UPDATE дозволено
            stmt.executeUpdate("UPDATE test_smoke_permissions SET name = 'updated' WHERE id = 1");

            // DELETE заборонено (SQLState 42501)
            SQLException deleteEx = assertThrows(SQLException.class, () ->
                    stmt.executeUpdate("DELETE FROM test_smoke_permissions WHERE id = 1")
            );
            assertEquals("42501", deleteEx.getSQLState(), "DELETE must fail with SQLState 42501 (permission denied)");

            // DDL (DROP TABLE) заборонено (SQLState 42501)
            SQLException ddlEx = assertThrows(SQLException.class, () ->
                    stmt.execute("DROP TABLE test_smoke_permissions")
            );
            assertEquals("42501", ddlEx.getSQLState(), "DDL must fail with SQLState 42501 (permission denied)");

            // 4. Перевірка таймаутів ролі застосунку (A-43: lock_timeout 5s, statement_timeout 10s)
            try (ResultSet rs = stmt.executeQuery("SHOW lock_timeout")) {
                assertTrue(rs.next());
                String lockTimeout = rs.getString(1);
                assertTrue(lockTimeout.equals("5s") || lockTimeout.equals("5000ms"),
                        "lock_timeout must be 5s (A-43), got: " + lockTimeout);
            }

            try (ResultSet rs = stmt.executeQuery("SHOW statement_timeout")) {
                assertTrue(rs.next());
                String statementTimeout = rs.getString(1);
                assertTrue(statementTimeout.equals("10s") || statementTimeout.equals("10000ms"),
                        "statement_timeout must be 10s (A-43), got: " + statementTimeout);
            }
        }

        // Прибирання таблиці
        try (Connection ownerConn = PostgresContainerHolder.getOwnerConnection();
             Statement stmt = ownerConn.createStatement()) {
            stmt.execute("DROP TABLE test_smoke_permissions");
        }
    }
}

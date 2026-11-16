package roombook.testsupport;

import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Керування єдиним інстансом PostgreSQL 16 у Testcontainers (T-03, T-06).
 * Забезпечує наявність двох баз (roombook та roombook_c з локаллю C),
 * трьох ролей (roombook_owner, roombook_app, roombook_backup) та запуск міграцій Flyway.
 */
public final class PostgresContainerHolder {

    private static final String IMAGE = "postgres:16";
    private static volatile PostgreSQLContainer<?> container;

    private PostgresContainerHolder() {
    }

    public static PostgreSQLContainer<?> getInstance() {
        if (container == null) {
            synchronized (PostgresContainerHolder.class) {
                if (container == null) {
                    PostgreSQLContainer<?> c = new PostgreSQLContainer<>(IMAGE)
                            .withDatabaseName("roombook")
                            .withUsername("postgres")
                            .withPassword("postgres");
                    c.start();
                    initializeDatabases(c);
                    container = c;
                }
            }
        }
        return container;
    }

    private static void initializeDatabases(PostgreSQLContainer<?> c) {
        // 1. Виконання ops/db/init.sql у базі roombook під суперкористувачем postgres
        executeScript(c, "roombook", Paths.get("ops/db/init.sql"));

        // 2. Створення другої бази даних roombook_c з локаллю C для тестів TS-04, TS-14, TS-15
        try (Connection conn = getSuperuserConnection(c, "postgres");
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery("SELECT 1 FROM pg_database WHERE datname = 'roombook_c'");
            if (!rs.next()) {
                stmt.execute("CREATE DATABASE roombook_c WITH TEMPLATE template0 LC_COLLATE = 'C' LC_CTYPE = 'C' OWNER roombook_owner");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create roombook_c database with C locale", e);
        }

        // 3. Налаштування прав у базі roombook_c
        try (Connection conn = getSuperuserConnection(c, "roombook_c");
             Statement stmt = conn.createStatement()) {

            stmt.execute("ALTER SCHEMA public OWNER TO roombook_owner");
            stmt.execute("REVOKE ALL ON SCHEMA public FROM PUBLIC");
            stmt.execute("GRANT USAGE ON SCHEMA public TO roombook_app");
            stmt.execute("GRANT ALL ON SCHEMA public TO roombook_owner");
            stmt.execute("GRANT CONNECT ON DATABASE roombook_c TO roombook_owner");
            stmt.execute("GRANT CONNECT ON DATABASE roombook_c TO roombook_app");
            stmt.execute("GRANT CONNECT ON DATABASE roombook_c TO roombook_backup");
            stmt.execute("ALTER DEFAULT PRIVILEGES FOR ROLE roombook_owner IN SCHEMA public GRANT SELECT, INSERT, UPDATE ON TABLES TO roombook_app");
            stmt.execute("ALTER DEFAULT PRIVILEGES FOR ROLE roombook_owner IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO roombook_app");
            stmt.execute("ALTER ROLE roombook_app SET lock_timeout = '5s'");
            stmt.execute("ALTER ROLE roombook_app SET statement_timeout = '10s'");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to configure permissions in roombook_c", e);
        }

        // 4. Застосування міграцій Flyway від імені roombook_owner
        applyMigrations(c, "roombook");
        applyMigrations(c, "roombook_c");
    }

    public static void applyMigrations(String database) {
        applyMigrations(getInstance(), database);
    }

    private static void applyMigrations(PostgreSQLContainer<?> c, String database) {
        String url = String.format("jdbc:postgresql://%s:%d/%s", c.getHost(), c.getMappedPort(5432), database);
        Flyway flyway = Flyway.configure()
                .dataSource(url, "roombook_owner", "owner_pass")
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();
    }

    private static void executeScript(PostgreSQLContainer<?> c, String database, Path scriptPath) {
        try {
            String script = Files.readString(scriptPath);
            try (Connection conn = getSuperuserConnection(c, database);
                 Statement stmt = conn.createStatement()) {
                stmt.execute(script);
            }
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Failed to execute script " + scriptPath + " on database " + database, e);
        }
    }

    public static Connection getSuperuserConnection(PostgreSQLContainer<?> c, String database) throws SQLException {
        String url = String.format("jdbc:postgresql://%s:%d/%s", c.getHost(), c.getMappedPort(5432), database);
        return DriverManager.getConnection(url, "postgres", "postgres");
    }

    public static Connection getOwnerConnection() throws SQLException {
        PostgreSQLContainer<?> c = getInstance();
        String url = String.format("jdbc:postgresql://%s:%d/roombook", c.getHost(), c.getMappedPort(5432));
        return DriverManager.getConnection(url, "roombook_owner", "owner_pass");
    }

    public static Connection getAppConnection() throws SQLException {
        return getAppConnection("roombook-app-test");
    }

    public static Connection getAppConnection(String applicationName) throws SQLException {
        PostgreSQLContainer<?> c = getInstance();
        String url = String.format("jdbc:postgresql://%s:%d/roombook?ApplicationName=%s",
                c.getHost(), c.getMappedPort(5432), applicationName);
        Connection conn = DriverManager.getConnection(url, "roombook_app", "app_pass");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SET application_name = '" + applicationName + "'");
        }
        return conn;
    }

    public static Connection getBackupConnection() throws SQLException {
        PostgreSQLContainer<?> c = getInstance();
        String url = String.format("jdbc:postgresql://%s:%d/roombook", c.getHost(), c.getMappedPort(5432));
        return DriverManager.getConnection(url, "roombook_backup", "backup_pass");
    }

    public static Connection getCLocaleAppConnection() throws SQLException {
        PostgreSQLContainer<?> c = getInstance();
        String url = String.format("jdbc:postgresql://%s:%d/roombook_c?ApplicationName=roombook-app-c",
                c.getHost(), c.getMappedPort(5432));
        return DriverManager.getConnection(url, "roombook_app", "app_pass");
    }

    public static Connection getCLocaleOwnerConnection() throws SQLException {
        PostgreSQLContainer<?> c = getInstance();
        String url = String.format("jdbc:postgresql://%s:%d/roombook_c", c.getHost(), c.getMappedPort(5432));
        return DriverManager.getConnection(url, "roombook_owner", "owner_pass");
    }

    /**
     * Очищення всіх таблиць схеми public (крім flyway_schema_history) перед кожним тестом.
     */
    public static void cleanDatabase() {
        cleanDatabase("roombook");
    }

    public static void cleanDatabase(String database) {
        PostgreSQLContainer<?> c = getInstance();
        String url = String.format("jdbc:postgresql://%s:%d/%s", c.getHost(), c.getMappedPort(5432), database);
        try (Connection conn = DriverManager.getConnection(url, "roombook_owner", "owner_pass");
             Statement stmt = conn.createStatement()) {

            ResultSet rs = stmt.executeQuery(
                    "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename != 'flyway_schema_history'"
            );
            List<String> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add("\"" + rs.getString(1) + "\"");
            }
            if (!tables.isEmpty()) {
                stmt.execute("TRUNCATE TABLE " + String.join(", ", tables) + " CASCADE");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to clean database " + database, e);
        }
    }
}

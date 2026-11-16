package roombook.testsupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Тестова утиліта для виконання перевірок аудиту інваріантів (T-07).
 * Запити знаходяться у каталозі ops/audit/ і виконуються від імені ролі roombook_backup (лише читання).
 */
public final class InvariantAudit {

    private static final Path AUDIT_DIR = Path.of("ops/audit");

    private InvariantAudit() {
    }

    public static String readQuery(String filename) {
        Path path = AUDIT_DIR.resolve(filename);
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read audit query from " + path.toAbsolutePath(), e);
        }
    }

    public static List<Map<String, Object>> checkInv07(Connection conn) throws SQLException {
        return executeQuery(conn, readQuery("inv-07-cancel-state.sql"));
    }

    public static List<Map<String, Object>> checkInv08(Connection conn) throws SQLException {
        return executeQuery(conn, readQuery("inv-08-no-overlap.sql"));
    }

    public static List<Map<String, Object>> checkInv09(Connection conn) throws SQLException {
        return executeQuery(conn, readQuery("inv-09-inactive-room-bookings.sql"));
    }

    public static List<Map<String, Object>> checkInv10(Connection conn) throws SQLException {
        return executeQuery(conn, readQuery("inv-10-foreign-keys.sql"));
    }

    public static List<Map<String, Object>> checkInv14(Connection conn) throws SQLException {
        return executeQuery(conn, readQuery("inv-14-admin-count.sql"));
    }

    public static List<Map<String, Object>> checkInformativeInv02To05(
            Connection conn,
            int slotGridMinutes,
            LocalTime workDayStart,
            LocalTime workDayEnd,
            int minDurationMinutes,
            int maxDurationMinutes
    ) throws SQLException {
        String sql = readQuery("informative-inv-02-05.sql");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, slotGridMinutes);
            ps.setTime(2, Time.valueOf(workDayStart));
            ps.setTime(3, Time.valueOf(workDayEnd));
            ps.setInt(4, minDurationMinutes);
            ps.setInt(5, maxDurationMinutes);
            try (ResultSet rs = ps.executeQuery()) {
                return extractRows(rs);
            }
        }
    }

    public static void assertAllInvariantsHold(Connection conn) throws SQLException {
        List<Map<String, Object>> inv07 = checkInv07(conn);
        if (!inv07.isEmpty()) {
            throw new AssertionError("INV-07 violated: " + inv07);
        }
        List<Map<String, Object>> inv08 = checkInv08(conn);
        if (!inv08.isEmpty()) {
            throw new AssertionError("INV-08 violated: " + inv08);
        }
        List<Map<String, Object>> inv09 = checkInv09(conn);
        if (!inv09.isEmpty()) {
            throw new AssertionError("INV-09 violated: " + inv09);
        }
        List<Map<String, Object>> inv10 = checkInv10(conn);
        if (!inv10.isEmpty()) {
            throw new AssertionError("INV-10 violated: " + inv10);
        }
        List<Map<String, Object>> inv14 = checkInv14(conn);
        if (!inv14.isEmpty()) {
            throw new AssertionError("INV-14 violated (no active admins): " + inv14);
        }
    }

    public static void assertAllInvariantsHold() {
        try (Connection conn = PostgresContainerHolder.getBackupConnection()) {
            assertAllInvariantsHold(conn);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to run invariant audit via roombook_backup", e);
        }
    }

    private static List<Map<String, Object>> executeQuery(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return extractRows(rs);
        }
    }

    private static List<Map<String, Object>> extractRows(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int count = md.getColumnCount();
        List<Map<String, Object>> list = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (int i = 1; i <= count; i++) {
                map.put(md.getColumnLabel(i), rs.getObject(i));
            }
            list.add(map);
        }
        return list;
    }
}

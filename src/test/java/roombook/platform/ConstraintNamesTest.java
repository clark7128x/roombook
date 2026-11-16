package roombook.platform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import roombook.testsupport.PostgresContainerHolder;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("OPS-01")
@Tag("AR-12")
class ConstraintNamesTest {

    @Test
    @DisplayName("OPS-01, AR-12: Порушення унікальності (UNIQUE) повертає ім'я обмеження та SQLState 23505")
    void uniqueConstraintViolation() throws Exception {
        try (Connection conn = PostgresContainerHolder.getOwnerConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TEMP TABLE temp_uq_table (id serial primary key, val text, constraint uq_temp_test UNIQUE (val))");
            stmt.execute("INSERT INTO temp_uq_table (val) VALUES ('duplicate')");
            SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.execute("INSERT INTO temp_uq_table (val) VALUES ('duplicate')"));

            assertEquals("uq_temp_test", ConstraintNames.getConstraintName(ex));
            assertEquals("23505", ConstraintNames.getSqlState(ex));
            assertTrue(ConstraintNames.findConstraintName(ex).isPresent());
            assertEquals("uq_temp_test", ConstraintNames.findConstraintName(ex).get());
        }
    }

    @Test
    @DisplayName("OPS-01, AR-12: Порушення виключення (EXCLUSION) повертає ім'я обмеження та SQLState 23P01")
    void exclusionConstraintViolation() throws Exception {
        try (Connection conn = PostgresContainerHolder.getOwnerConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TEMP TABLE temp_ex_table (id serial primary key, period tsrange, constraint ex_temp_test EXCLUDE USING gist (period WITH &&))");
            stmt.execute("INSERT INTO temp_ex_table (period) VALUES (tsrange('2026-10-25 10:00:00', '2026-10-25 11:00:00'))");
            SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.execute("INSERT INTO temp_ex_table (period) VALUES (tsrange('2026-10-25 10:30:00', '2026-10-25 11:30:00'))"));

            assertEquals("ex_temp_test", ConstraintNames.getConstraintName(ex));
            assertEquals("23P01", ConstraintNames.getSqlState(ex));
            assertTrue(ConstraintNames.findConstraintName(ex).isPresent());
            assertEquals("ex_temp_test", ConstraintNames.findConstraintName(ex).get());
        }
    }

    @Test
    @DisplayName("OPS-01, AR-12: Порушення CHECK обмеження повертає ім'я обмеження та SQLState 23514")
    void checkConstraintViolation() throws Exception {
        try (Connection conn = PostgresContainerHolder.getOwnerConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TEMP TABLE temp_ck_table (id serial primary key, val int, constraint ck_temp_test CHECK (val > 0))");
            SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.execute("INSERT INTO temp_ck_table (val) VALUES (-5)"));

            assertEquals("ck_temp_test", ConstraintNames.getConstraintName(ex));
            assertEquals("23514", ConstraintNames.getSqlState(ex));
            assertTrue(ConstraintNames.findConstraintName(ex).isPresent());
            assertEquals("ck_temp_test", ConstraintNames.findConstraintName(ex).get());
        }
    }

    @Test
    @DisplayName("OPS-01, AR-12: Порушення зовнішнього ключа (FOREIGN KEY) повертає ім'я обмеження та SQLState 23503")
    void foreignKeyConstraintViolation() throws Exception {
        try (Connection conn = PostgresContainerHolder.getOwnerConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TEMP TABLE temp_fk_parent (id int primary key)");
            stmt.execute("CREATE TEMP TABLE temp_fk_child (id serial primary key, parent_id int, constraint fk_temp_test FOREIGN KEY (parent_id) REFERENCES temp_fk_parent(id))");
            SQLException ex = assertThrows(SQLException.class, () ->
                    stmt.execute("INSERT INTO temp_fk_child (parent_id) VALUES (999999)"));

            assertEquals("fk_temp_test", ConstraintNames.getConstraintName(ex));
            assertEquals("23503", ConstraintNames.getSqlState(ex));
            assertTrue(ConstraintNames.findConstraintName(ex).isPresent());
            assertEquals("fk_temp_test", ConstraintNames.findConstraintName(ex).get());
        }
    }

    @Test
    @DisplayName("ConstraintNames повертає empty для null або винятків без обмежень")
    void nonConstraintExceptionsReturnEmpty() {
        Exception genericEx = new RuntimeException("Generic failure");
        assertTrue(ConstraintNames.findConstraintName(genericEx).isEmpty());
        assertTrue(ConstraintNames.findSqlState(genericEx).isEmpty());
        assertTrue(ConstraintNames.findConstraintName(null).isEmpty());
        assertTrue(ConstraintNames.findSqlState(null).isEmpty());
    }
}

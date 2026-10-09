package io.yak.ops.connector.jdbc.database.converter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.connector.jdbc.database.dialect.AnsiJdbcDialect;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowData;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLDataException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** H2-backed codecs exercise true ResultSet metadata, null values and exact type boundaries. */
class JdbcDialectConverterTest {

    @Test
    void convertsJDBCResultSetIntoCanonicalTypedRowData() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:converter;DB_CLOSE_DELAY=-1", "sa", "");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE IF NOT EXISTS DOCS ("
                    + "ID BIGINT NOT NULL, VALUE_INT INTEGER, VALUE_DEC DECIMAL(18,2), "
                    + "VALUE_STR VARCHAR(32), VALUE_BIN VARBINARY(16), VALUE_DATE DATE, "
                    + "VALUE_TS TIMESTAMP(6), VALUE_BOOL BOOLEAN, VALUE_NULL VARCHAR(10))");
            sql.execute("DELETE FROM DOCS");
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO DOCS VALUES (?,?,?,?,?,?,?,?,?)")) {
                insert.setLong(1, 77L);
                insert.setInt(2, 42);
                insert.setBigDecimal(3, new BigDecimal("123.40"));
                insert.setString(4, "hello");
                insert.setBytes(5, new byte[] {7, 8, 9});
                insert.setDate(6, java.sql.Date.valueOf("2026-10-09"));
                insert.setTimestamp(7, java.sql.Timestamp.valueOf("2026-10-09 12:34:56.123456"));
                insert.setBoolean(8, true);
                insert.setNull(9, java.sql.Types.VARCHAR);
                insert.executeUpdate();
            }
            try (var records = sql.executeQuery("SELECT * FROM DOCS")) {
                JdbcDialectConverter converter = new AnsiJdbcDialect().createRowConverter(records.getMetaData());
                assertTrue(records.next());
                RowData row = converter.toInternal(records);
                assertInstanceOf(GenericRowData.class, row);
                assertEquals(9, row.getArity());
                assertEquals(77L, row.getLong(0));
                assertEquals(42, row.getInt(1));
                assertEquals(new BigDecimal("123.40"), row.getDecimal(2, 18, 2));
                assertEquals("hello", row.getString(3));
                assertArrayEquals(new byte[] {7, 8, 9}, row.getBinary(4));
                assertEquals(LocalDate.of(2026, 10, 9), row.getDate(5));
                assertEquals(LocalDateTime.parse("2026-10-09T12:34:56.123456"), row.getTimestamp(6, 6));
                assertEquals(true, row.getBoolean(7));
                assertTrue(row.isNullAt(8));
                assertEquals(false, converter.schema().column(0).nullable());
                assertEquals("DECIMAL(18, 2)", converter.schema().column(2).dataType().asSerializableString());
                assertEquals(32, converter.schema().column(3).length());

                try (PreparedStatement outbound =
                        connection.prepareStatement("INSERT INTO DOCS VALUES (?,?,?,?,?,?,?,?,?)")) {
                    converter.toExternal(row, outbound);
                    assertEquals(1, outbound.executeUpdate());
                }
                try (var count = sql.executeQuery("SELECT COUNT(*) FROM DOCS")) {
                    assertTrue(count.next());
                    assertEquals(2, count.getInt(1));
                }
            }
        }
    }

    @Test
    void rejectsUnresolvedPrecisionRatherThanReturningUnstableDriverObject() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:decimal-reject", "sa", "");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE NUMBERS (AMOUNT DECIMAL(50,2))");
            try (var rows = sql.executeQuery("SELECT AMOUNT FROM NUMBERS")) {
                assertThrows(SQLDataException.class, () -> new AnsiJdbcDialect().createRowConverter(rows.getMetaData()));
            }
        }
    }

    @Test
    void rejectsOutOfRangeIntegerAndScaleLosingDecimals() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:range-reject", "sa", "");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE NUMBERS (SMALL SMALLINT, AMOUNT DECIMAL(6,2))");
            sql.execute("INSERT INTO NUMBERS VALUES (10, 1.25)");
            try (var rows = sql.executeQuery("SELECT * FROM NUMBERS")) {
                assertTrue(rows.next());
                JdbcDialectConverter converter = new AnsiJdbcDialect().createRowConverter(rows.getMetaData());
                assertThrows(
                        SQLDataException.class,
                        () -> {
                            try (PreparedStatement update =
                                    connection.prepareStatement("INSERT INTO NUMBERS VALUES (?,?)")) {
                                converter.toExternal(
                                        GenericRowData.of((short) 10, new BigDecimal("1.255")), update);
                            }
                        });
            }
        }
    }

    @Test
    void unsupportedJDBCNestedTypesFailExplicitly() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:array-reject", "sa", "");
                Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE COMPLEX (ITEMS INTEGER ARRAY)");
            try (var rows = sql.executeQuery("SELECT ITEMS FROM COMPLEX")) {
                assertThrows(
                        java.sql.SQLFeatureNotSupportedException.class,
                        () -> new AnsiJdbcDialect().createRowConverter(rows.getMetaData()));
            }
        }
    }
}

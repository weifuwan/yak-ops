package io.yak.ops.connector.jdbc.sink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.configuration.Configuration;
import io.yak.ops.connector.base.factory.TableSinkFactoryContext;
import io.yak.ops.connector.base.factory.SinkFactoryContext;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.database.internal.dialect.MySqlJdbcDialect;
import io.yak.ops.core.data.GenericRowData;
import io.yak.ops.core.data.RowKind;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies the exact-row contract and public JDBC RETRACT admission. */
class JdbcExactRowValidationTest {
    private static final TableId TABLE = new TableId(null, "public", "entries");
    private static final TableSchema KEYLESS = new TableSchema(
            List.of(
                    new Column("bucket", LogicalTypes.BIGINT.copy(false)),
                    new Column("amount", LogicalTypes.decimal(14, 2))),
            List.of());

    @Test
    void admitsOnlyFullRowKeylessCompatibleMetadata() {
        JdbcSinkSchemaValidator.validateExactRow(KEYLESS, KEYLESS);
        var keyed = new TableSchema(KEYLESS.columns(), List.of("bucket"));
        assertThrows(IllegalArgumentException.class,
                () -> JdbcSinkSchemaValidator.validateExactRow(keyed, KEYLESS));
        assertThrows(IllegalArgumentException.class,
                () -> JdbcSinkSchemaValidator.validateExactRow(KEYLESS, keyed));
        var incomplete = new TableSchema(List.of(KEYLESS.column(0)), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> JdbcSinkSchemaValidator.validateExactRow(KEYLESS, incomplete));
        var renamed = new TableSchema(
                List.of(new Column("different", LogicalTypes.BIGINT.copy(false)), KEYLESS.column(1)),
                List.of());
        assertThrows(IllegalArgumentException.class,
                () -> JdbcSinkSchemaValidator.validateExactRow(KEYLESS, renamed));
        var coerced = new TableSchema(
                List.of(new Column("bucket", LogicalTypes.decimal(20, 0).copy(false)), KEYLESS.column(1)),
                List.of());
        assertThrows(IllegalArgumentException.class,
                () -> JdbcSinkSchemaValidator.validateExactRow(KEYLESS, coerced));
    }

    @Test
    void rejectsTextColumnsUntilTheirDatabaseCollationHasBeenVerified() {
        var schema = new TableSchema(
                List.of(new Column("name", LogicalTypes.varchar(64))), List.of());
        var error = assertThrows(IllegalArgumentException.class,
                () -> JdbcSinkSchemaValidator.validateExactRow(schema, schema));
        assertTrue(error.getMessage().contains("verified database semantics"));
    }

    @Test
    void supportsRetractionPlansAndValidatesFactorySchema() {
        var options = new Configuration()
                .set(JdbcSinkOptions.WRITE_MODE, JdbcWriteMode.RETRACT)
                .set(JdbcSinkOptions.XA_ENABLED, true)
                .set(JdbcSinkOptions.XA_INSTANCE_ID, "keyless-test")
                .set(JdbcConnectionOptions.URL, "jdbc:mysql://localhost:3306/test")
                .set(JdbcConnectionOptions.USERNAME, "user")
                .set(JdbcConnectionOptions.PASSWORD, "secret");
        var factory = new MySqlDataSinkFactory();
        var withoutXa = new Configuration().set(JdbcSinkOptions.WRITE_MODE, JdbcWriteMode.RETRACT);
        assertThrows(UnsupportedOperationException.class, () -> factory.validate(withoutXa.toReadonlyConfig(), true));
        factory.validate(options.toReadonlyConfig(), true);
        factory.validateSchema(options.toReadonlyConfig(), KEYLESS, KEYLESS, true);
        factory.createDataSink(new SinkFactoryContext(List.of(new TableSinkFactoryContext(
                options.toReadonlyConfig(), TABLE, KEYLESS, TABLE, KEYLESS)), new Configuration(), getClass().getClassLoader()));
        var plan = new JdbcTableWritePlan(TABLE, TABLE, KEYLESS, JdbcWriteMode.RETRACT);
        assertEquals(JdbcWriteMode.RETRACT, plan.writeMode());
        assertTrue(plan.sql(new MySqlJdbcDialect()).startsWith("INSERT INTO"));
        assertEquals(2, plan.project(GenericRowData.of(1L, null), RowKind.DELETE).getArity());
        assertEquals(1L, plan.project(GenericRowData.of(1L, null), RowKind.UPDATE_BEFORE).getField(0));
        assertTrue(new MySqlJdbcDialect().exactRowDeleteSql(TABLE, KEYLESS).contains(" <=> ?"));
        assertTrue(new MySqlJdbcDialect().exactRowDeleteSql(TABLE, KEYLESS).endsWith(" LIMIT 1"));
        assertEquals(JdbcWriteMode.RETRACT, options.get(JdbcSinkOptions.WRITE_MODE));
    }

    @Test
    void keepsAppendAndUpsertContractsIntact() {
        var keyed = new TableSchema(KEYLESS.columns(), List.of("bucket"));
        assertEquals(JdbcWriteMode.APPEND,
                new JdbcTableWritePlan(TABLE, TABLE, KEYLESS, JdbcWriteMode.APPEND).writeMode());
        assertEquals(JdbcWriteMode.UPSERT,
                new JdbcTableWritePlan(TABLE, TABLE, keyed, JdbcWriteMode.UPSERT).writeMode());
    }
}

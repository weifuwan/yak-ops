package io.yak.ops.flow.connector.jdbc.dialect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.api.row.YakTypes;
import io.yak.ops.plugin.datasource.api.catalog.DataSourceTablePath;
import java.util.List;
import org.junit.jupiter.api.Test;

class JdbcUpsertDialectTest {

    private static final YakTableSchema SCHEMA = new YakTableSchema(
            List.of(
                    new YakColumn("id", YakTypes.BIGINT, false, null),
                    new YakColumn("name", YakTypes.STRING, false, 100),
                    new YakColumn("amount", YakTypes.decimal(10, 2), true, null)),
            List.of("id"));

    @Test
    void shouldGenerateMysqlNativeUpsert() {
        assertEquals(
                "INSERT INTO `yakflow`.`target_table` (`id`, `name`, `amount`) VALUES (?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `amount` = VALUES(`amount`)",
                JdbcDialects.forType("MYSQL")
                        .upsertSql(new DataSourceTablePath("yakflow", null, "target_table"), SCHEMA));
    }

    @Test
    void shouldGeneratePostgresqlNativeUpsert() {
        assertEquals(
                "INSERT INTO \"public\".\"target_table\" (\"id\", \"name\", \"amount\") VALUES (?, ?, ?) "
                        + "ON CONFLICT (\"id\") DO UPDATE SET \"name\" = EXCLUDED.\"name\", "
                        + "\"amount\" = EXCLUDED.\"amount\"",
                JdbcDialects.forType("POSTGRE_SQL")
                        .upsertSql(new DataSourceTablePath("yakflow", "public", "target_table"), SCHEMA));
    }

    @Test
    void shouldGenerateOracleNativeMerge() {
        assertEquals(
                "MERGE INTO \"target_table\" t USING (SELECT ? AS \"id\", ? AS \"name\", ? AS \"amount\" FROM DUAL) "
                        + "s ON (t.\"id\" = s.\"id\") WHEN MATCHED THEN UPDATE SET "
                        + "t.\"name\" = s.\"name\", t.\"amount\" = s.\"amount\" "
                        + "WHEN NOT MATCHED THEN INSERT (\"id\", \"name\", \"amount\") "
                        + "VALUES (s.\"id\", s.\"name\", s.\"amount\")",
                JdbcDialects.forType("ORACLE")
                        .upsertSql(new DataSourceTablePath(null, null, "target_table"), SCHEMA));
    }
}

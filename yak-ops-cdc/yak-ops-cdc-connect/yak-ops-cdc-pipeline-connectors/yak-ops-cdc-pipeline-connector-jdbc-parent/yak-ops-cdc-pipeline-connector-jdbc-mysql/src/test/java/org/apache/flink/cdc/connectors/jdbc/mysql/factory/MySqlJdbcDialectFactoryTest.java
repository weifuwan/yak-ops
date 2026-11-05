package org.apache.flink.cdc.connectors.jdbc.mysql.factory;

import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.types.DataTypes;
import org.apache.flink.cdc.connectors.jdbc.config.JdbcSinkConfig;
import org.apache.flink.cdc.connectors.jdbc.internal.data.RichJdbcRowData;
import org.apache.flink.cdc.connectors.jdbc.internal.data.RowKind;
import org.apache.flink.cdc.connectors.jdbc.internal.dialect.JdbcDialect;
import org.apache.flink.cdc.connectors.jdbc.internal.dialect.JdbcDialectFactory;
import org.apache.flink.cdc.connectors.jdbc.mysql.converter.MySqlRowConverter;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates dialect discovery and the MySQL driver's temporal null binding. */
class MySqlJdbcDialectFactoryTest {

    @Test
    void serviceLoaderFindsMySqlDialectAndCatalog() {
        JdbcDialectFactory<?> factory = null;
        for (JdbcDialectFactory<?> candidate : ServiceLoader.load(JdbcDialectFactory.class)) {
            if ("mysql".equals(candidate.identifier())) {
                factory = candidate;
                break;
            }
        }
        assertNotNull(factory, "MySQL dialect SPI is not registered");
        JdbcSinkConfig config =
                new JdbcSinkConfig.Builder<>()
                        .connUrl("jdbc:mysql://localhost:3306")
                        .username("root")
                        .password("password")
                        .build();
        assertTrue(new MySqlJdbcDialectFactory().createCatalog(
                config, new MySqlJdbcDialectFactory().createDialect(config))
                instanceof org.apache.flink.cdc.connectors.jdbc.mysql.catalog.MySqlCatalog);
    }

    @Test
    void mysqlDialectBindsTimestampLtzNullAsJdbcTimestamp() throws Exception {
        JdbcDialect dialect = new MySqlJdbcDialectFactory()
                .createDialect(
                        new JdbcSinkConfig.Builder<>()
                                .connUrl("jdbc:mysql://localhost:3306")
                                .build());
        assertTrue(dialect.createRowConverter() instanceof MySqlRowConverter);
        Schema schema =
                Schema.newBuilder()
                        .physicalColumn("recorded_at", DataTypes.TIMESTAMP_LTZ(3))
                        .build();
        RichJdbcRowData row =
                new RichJdbcRowData.Builder()
                        .setTableId(TableId.tableId("db", "events"))
                        .setSchema(schema)
                        .setRowKind(RowKind.INSERT)
                        .setRows(new Object[] {null})
                        .build();

        int[] type = {-1};
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[] {PreparedStatement.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("setNull")) {
                        type[0] = (Integer) args[1];
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        dialect.createRowConverter()
                .createStatementBuilder(schema, schema.getColumnNames(), schema.getColumnNames())
                .accept(statement, row);
        assertEquals(Types.TIMESTAMP, type[0]);
    }
}

package io.yak.ops.connector.cdc.mysql.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.base.factory.TableSourceFactoryContext;
import io.yak.ops.connector.base.metrics.TableMetrics;
import io.yak.ops.connector.cdc.mysql.factory.MySqlDataSourceFactory;
import io.yak.ops.connector.base.factory.SourceFactoryContext;
import io.yak.ops.configuration.Configuration;
import io.yak.ops.core.source.DataSource;
import io.yak.ops.core.api.connector.source.Boundedness;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceOptions;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.core.data.TableId;
import io.yak.ops.core.types.Column;
import io.yak.ops.core.types.LogicalTypes;
import io.yak.ops.core.types.TableSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Source factory must keep one physical snapshot chunk key per explicitly routed CDC table. */
class MySqlDataSourceFactoryChunkKeyTest {

    private static final TableId ORDERS = new TableId("app", null, "orders");
    private static final TableId USERS = new TableId("app", null, "users");

    @Test
    void createsOneSourceWithDistinctChunkKeysForTwoTables() {
        TableMetrics metrics = new TableMetrics();
        MySqlDataSourceFactory factory = new MySqlDataSourceFactory();
        DataSource source = factory.createDataSource(new SourceFactoryContext(List.of(
                context(ORDERS, "bucket", metrics), context(USERS, "id", metrics)),
                new Configuration(), getClass().getClassLoader()));
        assertEquals(List.of(ORDERS, USERS), source.getMetadataAccessor().listTables("app", null));
        assertEquals(List.of("id"), source.getMetadataAccessor().getTableSchema(ORDERS).primaryKeys());
        assertEquals(Boundedness.CONTINUOUS_UNBOUNDED, source.getEventSourceProvider().getSource().getBoundedness());
        source.getEventSourceProvider().getSource().validateParallelism(2);
        source.getEventSourceProvider().getSource().validateParallelism(16);
        assertThrows(IllegalArgumentException.class,
                () -> source.getEventSourceProvider().getSource().validateParallelism(17));
    }

    @Test
    void rejectsUnknownChunkKeyInOneOfTheTables() {
        TableMetrics metrics = new TableMetrics();
        MySqlDataSourceFactory factory = new MySqlDataSourceFactory();
        assertThrows(
                IllegalArgumentException.class,
                () -> factory.createDataSource(new SourceFactoryContext(List.of(
                        context(ORDERS, "bucket", metrics),
                        context(USERS, "missing", metrics)), new Configuration(), getClass().getClassLoader())));
    }

    private static TableSourceFactoryContext context(TableId table, String chunkKey, TableMetrics metrics) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put(JdbcConnectionOptions.URL.key(), "jdbc:mysql://127.0.0.1:3306/app");
        options.put(JdbcConnectionOptions.USERNAME.key(), "test_user");
        options.put(JdbcConnectionOptions.PASSWORD.key(), "test_password");
        options.put(MySqlSourceOptions.SERVER_ID.key(), "5400");
        options.put(MySqlSourceOptions.TOPIC_PREFIX.key(), "cdc_chunk_key_test");
        options.put(MySqlSourceOptions.SCAN_STARTUP_MODE.key(), "initial");
        options.put(MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_KEY_COLUMN.key(), chunkKey);
        TableSchema schema = new TableSchema(
                List.of(
                        new Column("id", LogicalTypes.BIGINT.copy(false)),
                        new Column("bucket", LogicalTypes.INTEGER.copy(false))),
                List.of("id"));
        return new TableSourceFactoryContext(ReadonlyConfig.fromMap(options), table, schema, metrics);
    }
}

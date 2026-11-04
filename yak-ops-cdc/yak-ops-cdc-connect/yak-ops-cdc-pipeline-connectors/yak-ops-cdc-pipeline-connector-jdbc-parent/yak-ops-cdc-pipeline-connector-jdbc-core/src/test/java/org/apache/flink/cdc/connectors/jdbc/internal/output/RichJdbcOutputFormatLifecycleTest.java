package org.apache.flink.cdc.connectors.jdbc.internal.output;

import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.types.DataTypes;
import org.apache.flink.cdc.connectors.jdbc.internal.JdbcOutputFormatBuilder;
import org.apache.flink.cdc.connectors.jdbc.internal.converter.DefaultJdbcRowConverter;
import org.apache.flink.cdc.connectors.jdbc.internal.data.RichJdbcRowData;
import org.apache.flink.cdc.connectors.jdbc.internal.data.RowKind;
import org.apache.flink.cdc.connectors.jdbc.internal.dialect.JdbcDialect;
import org.apache.flink.cdc.connectors.jdbc.internal.writer.JdbcTableWriterRegistry;
import org.apache.flink.cdc.connectors.jdbc.sink.v2.JdbcTableWriter;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.datasource.connections.JdbcConnectionProvider;
import org.apache.flink.connector.jdbc.internal.JdbcOutputSerializer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises real Flink JDBC output formats with simulated prepared statements.
 *
 * <p>Unlike a fake writer, this reproduces JdbcOutputFormat.close() exiting early when its
 * final executeBatch() fails and verifies that the underlying connection is still released.
 */
class RichJdbcOutputFormatLifecycleTest {
    private static final TableId TABLE = TableId.tableId("db", "orders");
    private static final Schema SCHEMA =
            Schema.newBuilder()
                    .physicalColumn("id", DataTypes.BIGINT().notNull())
                    .primaryKey("id")
                    .build();

    @Test
    void flushFailureOnCloseStillReleasesPhysicalConnection() throws Exception {
        TrackingConnectionProvider provider = new TrackingConnectionProvider(true, false);
        RichJdbcOutputFormat format = outputFormat(provider);
        format.writeRecord(insert(1L));

        RuntimeException failure = assertThrows(RuntimeException.class, format::close);
        assertTrue(failure.getMessage().contains("Writing records to JDBC failed"));
        assertEquals(1, provider.closeCalls);
        assertTrue(provider.connectionClosed);
        assertEquals(1, provider.batchAttempts);
    }

    @Test
    void preservesFlushErrorAndSuppressesConnectionCleanupFailure() throws Exception {
        TrackingConnectionProvider provider = new TrackingConnectionProvider(true, true);
        RichJdbcOutputFormat format = outputFormat(provider);
        format.writeRecord(insert(1L));

        RuntimeException failure = assertThrows(RuntimeException.class, format::close);
        assertTrue(failure.getMessage().contains("Writing records to JDBC failed"));
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(failure.getSuppressed()[0].getMessage().contains("close connection failed"));
        assertEquals(1, provider.closeCalls);
    }

    @Test
    void successfulCloseUsesTheUpstreamConnectionCloseExactlyOnce() throws Exception {
        TrackingConnectionProvider provider = new TrackingConnectionProvider(false, false);
        RichJdbcOutputFormat format = outputFormat(provider);
        format.writeRecord(insert(1L));

        format.close();

        assertEquals(1, provider.closeCalls);
        assertEquals(1, provider.batchAttempts);
        assertTrue(provider.connectionClosed);
    }

    @Test
    void failingTableDoesNotPreventAnotherRealTableWriterFromClosing() throws Exception {
        TrackingConnectionProvider failing = new TrackingConnectionProvider(true, false);
        TrackingConnectionProvider healthy = new TrackingConnectionProvider(false, false);
        List<TrackingConnectionProvider> providers = new ArrayList<>();
        providers.add(failing);
        providers.add(healthy);

        JdbcTableWriterRegistry<JdbcTableWriter> registry =
                new JdbcTableWriterRegistry<>(
                        2,
                        (tableId, schema) -> {
                            TrackingConnectionProvider provider = providers.remove(0);
                            return new JdbcTableWriter(tableId, schema, builder(provider));
                        },
                        JdbcTableWriter::flush);

        registry.getOrCreate(TABLE, SCHEMA).write(insert(1L));
        TableId otherTable = TableId.tableId("db", "customers");
        registry.getOrCreate(otherTable, SCHEMA)
                .write(row(otherTable, 2L));

        IOException error = assertThrows(IOException.class, registry::close);

        assertTrue(error.getMessage().contains("Failed to close JDBC table writer"));
        assertEquals(1, failing.closeCalls);
        assertEquals(1, healthy.closeCalls);
        assertTrue(failing.connectionClosed);
        assertTrue(healthy.connectionClosed);
        assertEquals(0, registry.size());
    }

    private static RichJdbcOutputFormat outputFormat(TrackingConnectionProvider provider)
            throws IOException {
        return builder(provider).build(TABLE, SCHEMA);
    }

    private static JdbcOutputFormatBuilder builder(JdbcConnectionProvider provider) {
        JdbcDialect dialect =
                (JdbcDialect)
                        Proxy.newProxyInstance(
                                RichJdbcOutputFormatLifecycleTest.class.getClassLoader(),
                                new Class<?>[] {JdbcDialect.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("getUpsertStatement")) {
                                        return "INSERT INTO orders (id) VALUES (?)";
                                    }
                                    if (method.getName().equals("getDeleteStatement")) {
                                        return "DELETE FROM orders WHERE id = ?";
                                    }
                                    throw new UnsupportedOperationException(method.getName());
                                });
        JdbcExecutionOptions options =
                JdbcExecutionOptions.builder()
                        .withBatchSize(32)
                        .withBatchIntervalMs(0)
                        .withMaxRetries(0)
                        .build();
        return new JdbcOutputFormatBuilder(
                options,
                provider,
                JdbcOutputSerializer.<Object>of(null),
                dialect,
                new DefaultJdbcRowConverter());
    }

    private static RichJdbcRowData insert(long id) {
        return row(TABLE, id);
    }

    private static RichJdbcRowData row(TableId tableId, long id) {
        return new RichJdbcRowData.Builder()
                .setTableId(tableId)
                .setSchema(SCHEMA)
                .setRowKind(RowKind.INSERT)
                .setRows(new Object[] {id})
                .build();
    }

    private static final class TrackingConnectionProvider implements JdbcConnectionProvider {
        private final boolean failBatch;
        private final boolean failClose;
        private final Connection connection;
        private int closeCalls;
        private int batchAttempts;
        private boolean connectionClosed;

        private TrackingConnectionProvider(boolean failBatch, boolean failClose) {
            this.failBatch = failBatch;
            this.failClose = failClose;
            PreparedStatement statement =
                    (PreparedStatement)
                            Proxy.newProxyInstance(
                                    getClass().getClassLoader(),
                                    new Class<?>[] {PreparedStatement.class},
                                    (proxy, method, args) -> {
                                        switch (method.getName()) {
                                            case "setObject":
                                            case "addBatch":
                                            case "close":
                                                return null;
                                            case "executeBatch":
                                                batchAttempts++;
                                                if (this.failBatch) {
                                                    throw new SQLException("executeBatch failed");
                                                }
                                                return new int[] {1};
                                            default:
                                                throw new UnsupportedOperationException(
                                                        method.getName());
                                        }
                                    });
            this.connection =
                    (Connection)
                            Proxy.newProxyInstance(
                                    getClass().getClassLoader(),
                                    new Class<?>[] {Connection.class},
                                    (proxy, method, args) -> {
                                        if (method.getName().equals("prepareStatement")) {
                                            return statement;
                                        }
                                        if (method.getName().equals("close")) {
                                            connectionClosed = true;
                                            return null;
                                        }
                                        throw new UnsupportedOperationException(method.getName());
                                    });
        }

        @Override
        public Connection getConnection() {
            return connection;
        }

        @Override
        public Properties getProperties() {
            return new Properties();
        }

        @Override
        public boolean isConnectionValid() {
            return !connectionClosed;
        }

        @Override
        public Connection getOrEstablishConnection() {
            return connection;
        }

        @Override
        public Connection reestablishConnection() {
            return connection;
        }

        @Override
        public void closeConnection() {
            closeCalls++;
            connectionClosed = true;
            if (failClose) {
                throw new IllegalStateException("close connection failed");
            }
        }
    }
}

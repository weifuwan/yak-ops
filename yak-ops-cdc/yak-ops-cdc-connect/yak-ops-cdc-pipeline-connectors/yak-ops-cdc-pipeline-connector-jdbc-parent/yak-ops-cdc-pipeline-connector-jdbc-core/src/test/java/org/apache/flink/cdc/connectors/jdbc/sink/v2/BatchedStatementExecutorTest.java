package org.apache.flink.cdc.connectors.jdbc.sink.v2;

import org.apache.flink.cdc.connectors.jdbc.internal.JdbcOutputFormatBuilder;
import org.apache.flink.cdc.connectors.jdbc.internal.data.JdbcRowData;
import org.apache.flink.cdc.connectors.jdbc.internal.data.RichJdbcRowData;
import org.apache.flink.cdc.connectors.jdbc.internal.data.RowKind;
import org.apache.flink.cdc.connectors.jdbc.internal.executor.BatchedStatementExecutor;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.types.DataTypes;
import org.apache.flink.connector.jdbc.internal.executor.JdbcBatchStatementExecutor;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ensures typed primary-key rows preserve the batching contract. */
class BatchedStatementExecutorTest {

    private static final TableId TABLE_ID = TableId.tableId("cdc_lab", "orders");

    @Test
    void updateToSameKeyIsReducedToTheLatestUpsert() throws Exception {
        Schema schema =
                Schema.newBuilder()
                        .physicalColumn("id", DataTypes.BIGINT().notNull())
                        .physicalColumn("customer", DataTypes.VARCHAR(64))
                        .primaryKey("id")
                        .build();
        RecordingExecutor upserts = new RecordingExecutor();
        RecordingExecutor deletes = new RecordingExecutor();
        BatchedStatementExecutor batch =
                new BatchedStatementExecutor(
                        upserts, deletes, JdbcOutputFormatBuilder.createRowKeyExtractor(TABLE_ID, schema));

        batch.addToBatch(row(schema, RowKind.DELETE, 1L, "before"));
        batch.addToBatch(row(schema, RowKind.INSERT, 1L, "after"));
        batch.executeBatch();

        assertEquals(1, upserts.rows.size());
        assertTrue(deletes.rows.isEmpty());
        assertEquals("after", upserts.rows.get(0).getRows()[1]);

        // The reduce buffer must not replay rows when the next flush is empty.
        batch.executeBatch();
        assertEquals(1, upserts.rows.size());
    }

    @Test
    void changeOfPrimaryKeyProducesADeleteAndAnUpsert() throws Exception {
        Schema schema =
                Schema.newBuilder()
                        .physicalColumn("id", DataTypes.BIGINT().notNull())
                        .physicalColumn("customer", DataTypes.VARCHAR(64))
                        .primaryKey("id")
                        .build();
        RecordingExecutor upserts = new RecordingExecutor();
        RecordingExecutor deletes = new RecordingExecutor();
        BatchedStatementExecutor batch =
                new BatchedStatementExecutor(
                        upserts, deletes, JdbcOutputFormatBuilder.createRowKeyExtractor(TABLE_ID, schema));

        batch.addToBatch(row(schema, RowKind.DELETE, 1L, "before"));
        batch.addToBatch(row(schema, RowKind.INSERT, 2L, "after"));
        batch.executeBatch();

        assertEquals(1, upserts.rows.size());
        assertEquals(1, deletes.rows.size());
        assertEquals(2L, upserts.rows.get(0).getRows()[0]);
        assertEquals(1L, deletes.rows.get(0).getRows()[0]);
    }

    @Test
    void equalBinaryPrimaryKeysUseOneBatchSlot() throws Exception {
        Schema schema =
                Schema.newBuilder()
                        .physicalColumn("binary_id", DataTypes.VARBINARY(8).notNull())
                        .physicalColumn("customer", DataTypes.VARCHAR(64))
                        .primaryKey("binary_id")
                        .build();
        RecordingExecutor upserts = new RecordingExecutor();
        RecordingExecutor deletes = new RecordingExecutor();
        BatchedStatementExecutor batch =
                new BatchedStatementExecutor(
                        upserts, deletes, JdbcOutputFormatBuilder.createRowKeyExtractor(TABLE_ID, schema));

        batch.addToBatch(row(schema, RowKind.DELETE, new byte[] {1, 2, 3}, "before"));
        batch.addToBatch(row(schema, RowKind.INSERT, new byte[] {1, 2, 3}, "after"));
        batch.executeBatch();

        assertEquals(1, upserts.rows.size());
        assertTrue(deletes.rows.isEmpty());
        assertEquals("after", upserts.rows.get(0).getRows()[1]);
    }

    private static JdbcRowData row(Schema schema, RowKind rowKind, Object... values) {
        return new RichJdbcRowData.Builder()
                .setRowKind(rowKind)
                .setTableId(TABLE_ID)
                .setSchema(schema)
                .setRows(values)
                .build();
    }

    private static final class RecordingExecutor
            implements JdbcBatchStatementExecutor<JdbcRowData> {

        private final List<JdbcRowData> rows = new ArrayList<>();

        @Override
        public void prepareStatements(Connection connection) throws SQLException {}

        @Override
        public void addToBatch(JdbcRowData record) {
            rows.add(record);
        }

        @Override
        public void executeBatch() throws SQLException {}

        @Override
        public void closeStatements() throws SQLException {}
    }
}

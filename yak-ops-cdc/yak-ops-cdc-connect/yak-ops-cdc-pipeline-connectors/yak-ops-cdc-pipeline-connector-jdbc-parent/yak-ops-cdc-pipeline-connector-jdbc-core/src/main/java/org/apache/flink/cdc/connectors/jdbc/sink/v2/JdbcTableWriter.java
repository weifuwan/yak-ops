package org.apache.flink.cdc.connectors.jdbc.sink.v2;

import org.apache.flink.cdc.connectors.jdbc.internal.data.JdbcRowData;
import org.apache.flink.cdc.connectors.jdbc.internal.output.RichJdbcOutputFormat;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.connectors.jdbc.internal.JdbcOutputFormatBuilder;
import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.MetricGroup;

import java.io.IOException;

import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/** Owns the JDBC output format and statements for exactly one physical target table. */
public class JdbcTableWriter implements AutoCloseable {
    private static final String COMMITTED_RECORDS = "numCommittedRecords";

    private final TableId tableId;
    private final Schema schema;
    private final RichJdbcOutputFormat outputFormat;
    private final Counter executionCommittedRecords;
    private final Counter tableCommittedRecords;
    private long pendingRecords;

    public JdbcTableWriter(
            TableId tableId, Schema schema, JdbcOutputFormatBuilder outputFormatBuilder)
            throws IOException {
        this(tableId, schema, outputFormatBuilder, null, null);
    }

    public JdbcTableWriter(
            TableId tableId,
            Schema schema,
            JdbcOutputFormatBuilder outputFormatBuilder,
            MetricGroup parentMetricGroup)
            throws IOException {
        this(tableId, schema, outputFormatBuilder, parentMetricGroup, null);
    }

    public JdbcTableWriter(
            TableId tableId,
            Schema schema,
            JdbcOutputFormatBuilder outputFormatBuilder,
            MetricGroup parentMetricGroup,
            Counter executionCommittedRecords)
            throws IOException {
        this.tableId = checkNotNull(tableId);
        this.schema = checkNotNull(schema);
        this.outputFormat = checkNotNull(outputFormatBuilder).build(tableId, schema);
        this.executionCommittedRecords = executionCommittedRecords;
        this.tableCommittedRecords = parentMetricGroup == null
                ? null
                : parentMetricGroup
                        .addGroup("namespace", tableId.getNamespace() == null ? "" : tableId.getNamespace())
                        .addGroup("schema", tableId.getSchemaName() == null ? "" : tableId.getSchemaName())
                        .addGroup("table", tableId.getTableName())
                        .counter(COMMITTED_RECORDS);
    }

    public TableId getTableId() {
        return tableId;
    }

    public Schema getSchema() {
        return schema;
    }

    public void write(JdbcRowData rowData) throws IOException {
        checkArgument(tableId.equals(rowData.getTableId()), "JDBC table writer received wrong table");
        outputFormat.writeRecord(rowData);
        pendingRecords++;
        if (!rowData.hasPrimaryKey()) {
            // Keep the pre-refactor non-PK flush behavior until append-only semantics are defined.
            flush();
        }
    }

    /**
     * Flushes buffered rows and advances committed counters only after JDBC confirms success.
     */
    public void flush() throws IOException {
        outputFormat.flush();
        if (pendingRecords > 0) {
            if (executionCommittedRecords != null) {
                executionCommittedRecords.inc(pendingRecords);
            }
            if (tableCommittedRecords != null) {
                tableCommittedRecords.inc(pendingRecords);
            }
            pendingRecords = 0;
        }
    }

    @Override
    public void close() {
        outputFormat.close();
    }
}

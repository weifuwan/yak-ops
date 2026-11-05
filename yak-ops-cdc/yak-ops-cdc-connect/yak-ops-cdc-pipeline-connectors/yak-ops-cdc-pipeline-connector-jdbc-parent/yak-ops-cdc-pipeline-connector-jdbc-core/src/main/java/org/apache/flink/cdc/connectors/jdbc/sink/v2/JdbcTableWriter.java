package org.apache.flink.cdc.connectors.jdbc.sink.v2;

import org.apache.flink.cdc.connectors.jdbc.internal.data.JdbcRowData;
import org.apache.flink.cdc.connectors.jdbc.internal.output.RichJdbcOutputFormat;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.connectors.jdbc.internal.JdbcOutputFormatBuilder;

import java.io.IOException;

import static org.apache.flink.util.Preconditions.checkArgument;
import static org.apache.flink.util.Preconditions.checkNotNull;

/** Owns the JDBC output format and statements for exactly one physical target table. */
public class JdbcTableWriter implements AutoCloseable {
    private final TableId tableId;
    private final Schema schema;
    private final RichJdbcOutputFormat outputFormat;

    public JdbcTableWriter(
            TableId tableId, Schema schema, JdbcOutputFormatBuilder outputFormatBuilder)
            throws IOException {
        this.tableId = checkNotNull(tableId);
        this.schema = checkNotNull(schema);
        this.outputFormat = checkNotNull(outputFormatBuilder).build(tableId, schema);
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
        if (!rowData.hasPrimaryKey()) {
            // Keep the pre-refactor non-PK flush behavior until append-only semantics are defined.
            outputFormat.flush();
        }
    }

    public void flush() throws IOException {
        outputFormat.flush();
    }

    @Override
    public void close() {
        outputFormat.close();
    }
}

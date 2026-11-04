package org.apache.flink.cdc.connectors.jdbc.sink.v2;

import org.apache.flink.cdc.common.converter.JavaObjectConverter;
import org.apache.flink.cdc.common.data.RecordData;
import org.apache.flink.cdc.common.event.CreateTableEvent;
import org.apache.flink.cdc.common.event.DataChangeEvent;
import org.apache.flink.cdc.common.event.Event;
import org.apache.flink.cdc.common.event.SchemaChangeEvent;
import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Column;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.utils.Preconditions;
import org.apache.flink.cdc.common.utils.SchemaUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Serialization schema between {@link Event} and {@link JdbcRowData}. */
public class EventRecordSerializationSchema implements RecordSerializationSchema<Event> {
    /** Keeping the relationship of TableId and table information. */
    private final Map<TableId, TableInfo> tableInfoMap;

    public EventRecordSerializationSchema() {
        tableInfoMap = new HashMap<>();
    }

    @Override
    public JdbcRowData[] serialize(Event record) throws IOException {
        if (record instanceof SchemaChangeEvent) {
            return new JdbcRowData[] {applySchemaChangeEvent((SchemaChangeEvent) record)};
        } else if (record instanceof DataChangeEvent) {
            return applyDataChangeEvent((DataChangeEvent) record);
        } else {
            throw new UnsupportedOperationException("Unsupported event record type: " + record);
        }
    }

    private JdbcRowData applySchemaChangeEvent(SchemaChangeEvent event) {
        TableId tableId = event.tableId();
        Schema newSchema;
        if (event instanceof CreateTableEvent) {
            newSchema = ((CreateTableEvent) event).getSchema();
        } else {
            TableInfo tableInfo = tableInfoMap.get(tableId);
            if (tableInfo == null) {
                throw new RuntimeException("schema of " + tableId + " is not existed.");
            }
            newSchema = SchemaUtils.applySchemaChangeEvent(tableInfo.schema, event);
        }
        TableInfo tableInfo = new TableInfo(newSchema);
        tableInfoMap.put(tableId, tableInfo);

        return new RichJdbcRowData.Builder()
                .setRowKind(RowKind.SCHEMA_CHANGE)
                .setTableId(event.tableId())
                .build();
    }

    private JdbcRowData[] applyDataChangeEvent(DataChangeEvent event) {
        TableInfo tableInfo = tableInfoMap.get(event.tableId());
        Preconditions.checkNotNull(tableInfo, event.tableId() + " does not exist");

        RichJdbcRowData.Builder builder =
                new RichJdbcRowData.Builder()
                        .setTableId(event.tableId())
                        .setSchema(tableInfo.schema);

        List<JdbcRowData> rows = new ArrayList<>(2);

        if (event.before() != null) {
            builder.setRowKind(RowKind.DELETE);
            builder.setRows(serializeRecord(event.tableId(), tableInfo, event.before()));
            rows.add(builder.build());
        }

        if (event.after() != null) {
            builder.setRowKind(RowKind.INSERT);
            builder.setRows(serializeRecord(event.tableId(), tableInfo, event.after()));
            rows.add(builder.build());
        }

        return rows.toArray(new JdbcRowData[0]);
    }

    private Object[] serializeRecord(TableId tableId, TableInfo tableInfo, RecordData record) {
        Preconditions.checkNotNull(record, tableId + " record is null");

        List<Column> columns = tableInfo.schema.getColumns();
        Preconditions.checkArgument(columns.size() == record.getArity());

        Object[] values = new Object[record.getArity()];
        for (int i = 0; i < record.getArity(); i++) {
            Object field = tableInfo.fieldGetters[i].getFieldOrNull(record);
            values[i] = JavaObjectConverter.convertToJava(field, columns.get(i).getType());
        }
        return values;
    }

    /** Table information. */
    private static class TableInfo {
        Schema schema;
        RecordData.FieldGetter[] fieldGetters;

        public TableInfo(Schema schema) {
            this.schema = schema;
            this.fieldGetters =
                    SchemaUtils.createFieldGetters(schema).toArray(new RecordData.FieldGetter[0]);
        }
    }
}

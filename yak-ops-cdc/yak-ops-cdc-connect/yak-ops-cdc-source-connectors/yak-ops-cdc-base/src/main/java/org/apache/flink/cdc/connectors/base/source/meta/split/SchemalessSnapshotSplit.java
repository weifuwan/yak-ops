package org.apache.flink.cdc.connectors.base.source.meta.split;

import org.apache.flink.cdc.connectors.base.source.meta.offset.Offset;
import org.apache.flink.table.types.logical.RowType;

import io.debezium.relational.TableId;
import io.debezium.relational.history.TableChanges.TableChange;

import java.util.HashMap;
import java.util.Map;

/**
 * A kind of {@link SnapshotSplit} without table schema information, it is useful to reduce memory
 * usage in JobManager.
 */
public class SchemalessSnapshotSplit extends SnapshotSplit {

    public SchemalessSnapshotSplit(
            TableId tableId,
            String splitId,
            RowType splitKeyType,
            Object[] splitStart,
            Object[] splitEnd,
            Offset highWatermark) {
        super(
                tableId,
                splitId,
                splitKeyType,
                splitStart,
                splitEnd,
                highWatermark,
                new HashMap<>(1));
    }

    /**
     * Converts current {@link SchemalessSnapshotSplit} to {@link SnapshotSplit} with given table
     * schema information.
     */
    public final SnapshotSplit toSnapshotSplit(TableChange tableSchema) {
        Map<TableId, TableChange> tableSchemas = new HashMap<>();
        tableSchemas.put(getTableId(), tableSchema);
        return new SnapshotSplit(
                getTableId(),
                splitId(),
                getSplitKeyType(),
                getSplitStart(),
                getSplitEnd(),
                getHighWatermark(),
                tableSchemas);
    }
}

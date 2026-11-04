package org.apache.flink.cdc.connectors.mysql.source.split;

import org.apache.flink.cdc.connectors.mysql.source.offset.BinlogOffset;
import org.apache.flink.table.types.logical.RowType;

import io.debezium.relational.TableId;
import io.debezium.relational.history.TableChanges.TableChange;

import java.util.HashMap;
import java.util.Map;

/**
 * A kind of {@link MySqlSnapshotSplit} without table schema information, it is useful to reduce
 * memory usage in JobManager.
 */
public class MySqlSchemalessSnapshotSplit extends MySqlSnapshotSplit {

    public MySqlSchemalessSnapshotSplit(
            TableId tableId,
            String splitId,
            RowType splitKeyType,
            Object[] splitStart,
            Object[] splitEnd,
            BinlogOffset highWatermark) {
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
     * Converts current {@link MySqlSchemalessSnapshotSplit} to {@link MySqlSnapshotSplit} with
     * given table schema information.
     */
    public final MySqlSnapshotSplit toMySqlSnapshotSplit(TableChange tableSchema) {
        Map<TableId, TableChange> tableSchemas = new HashMap<>();
        tableSchemas.put(getTableId(), tableSchema);
        return new MySqlSnapshotSplit(
                getTableId(),
                splitId(),
                getSplitKeyType(),
                getSplitStart(),
                getSplitEnd(),
                getHighWatermark(),
                tableSchemas);
    }
}

package org.apache.flink.cdc.connectors.mysql.source.split;

import org.apache.flink.api.connector.source.SourceSplit;

import io.debezium.relational.TableId;
import io.debezium.relational.history.TableChanges;

import java.util.Map;
import java.util.Objects;

/** The split of table comes from a Table that splits by primary key. */
public abstract class MySqlSplit implements SourceSplit {

    protected final String splitId;

    public MySqlSplit(String splitId) {
        this.splitId = splitId;
    }

    /** Checks whether this split is a snapshot split. */
    public final boolean isSnapshotSplit() {
        return getClass() == MySqlSnapshotSplit.class
                || getClass() == MySqlSchemalessSnapshotSplit.class;
    }

    /** Checks whether this split is a binlog split. */
    public final boolean isBinlogSplit() {
        return getClass() == MySqlBinlogSplit.class;
    }

    /** Casts this split into a {@link MySqlSnapshotSplit}. */
    public final MySqlSnapshotSplit asSnapshotSplit() {
        return (MySqlSnapshotSplit) this;
    }

    /** Casts this split into a {@link MySqlBinlogSplit}. */
    public final MySqlBinlogSplit asBinlogSplit() {
        return (MySqlBinlogSplit) this;
    }

    @Override
    public String splitId() {
        return splitId;
    }

    public abstract Map<TableId, TableChanges.TableChange> getTableSchemas();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MySqlSplit that = (MySqlSplit) o;
        return Objects.equals(splitId, that.splitId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(splitId);
    }
}

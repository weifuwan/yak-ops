package org.apache.flink.cdc.connectors.base.source.meta.split;

import org.apache.flink.api.connector.source.SourceSplit;
import org.apache.flink.cdc.common.annotation.Experimental;

import io.debezium.relational.TableId;
import io.debezium.relational.history.TableChanges.TableChange;

import java.util.Map;
import java.util.Objects;

/** The split of table comes from a Table that splits by primary key. */
@Experimental
public abstract class SourceSplitBase implements SourceSplit {

    protected final String splitId;

    public SourceSplitBase(String splitId) {
        this.splitId = splitId;
    }

    /** Checks whether this split is a snapshot split. */
    public final boolean isSnapshotSplit() {
        return getClass() == SnapshotSplit.class || getClass() == SchemalessSnapshotSplit.class;
    }

    /** Checks whether this split is a stream split. */
    public final boolean isStreamSplit() {
        return getClass() == StreamSplit.class;
    }

    /** Casts this split into a {@link SnapshotSplit}. */
    public final SnapshotSplit asSnapshotSplit() {
        return (SnapshotSplit) this;
    }

    /** Casts this split into a {@link StreamSplit}. */
    public final StreamSplit asStreamSplit() {
        return (StreamSplit) this;
    }

    @Override
    public String splitId() {
        return splitId;
    }

    public abstract Map<TableId, TableChange> getTableSchemas();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SourceSplitBase that = (SourceSplitBase) o;
        return Objects.equals(splitId, that.splitId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(splitId);
    }
}

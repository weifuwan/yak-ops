package io.yak.ops.core.event;

import io.yak.ops.core.data.TableId;
import io.yak.ops.core.schema.Schema;
import java.io.Serializable;
import java.util.Objects;

/**
 * Replay-stable source-side DDL event. The Sink owns the source-to-target route and DDL plan.
 *
 * <p>A Source never invents a target table ID. Events are admitted on its owning mailbox
 * before the corresponding Binlog offset may be checkpointed.
 */
public record SourceSchemaChange(String eventId, TableId tableId, Schema before, Schema after) implements Serializable {

    private static final long serialVersionUID = 1L;

    public SourceSchemaChange {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Source schema event ID is required");
        }
        tableId = Objects.requireNonNull(tableId, "tableId");
        after = Objects.requireNonNull(after, "after");
        if (after.equals(before)) {
            throw new IllegalArgumentException("Source schema change must modify the schema");
        }
    }

    /** A null previous schema admits a newly discovered table through the schema coordinator. */
    public boolean newlyAdded() {
        return before == null;
    }

    @Override
    public String toString() {
        return "SourceSchemaChange{table=" + tableId + ", eventId=redacted}";
    }
}

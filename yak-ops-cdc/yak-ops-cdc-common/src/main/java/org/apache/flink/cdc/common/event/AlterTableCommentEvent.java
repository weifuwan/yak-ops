package org.apache.flink.cdc.common.event;

import org.apache.flink.cdc.common.annotation.PublicEvolving;
import org.apache.flink.cdc.common.utils.Preconditions;

import java.util.Objects;

/**
 * A {@link SchemaChangeEvent} that represents an {@code ALTER TABLE COMMENT} or {@code ALTER TABLE
 * SET COMMENT} DDL.
 */
@PublicEvolving
public class AlterTableCommentEvent implements SchemaChangeEvent {

    private static final long serialVersionUID = 1L;

    private final TableId tableId;
    private final String comment;

    public AlterTableCommentEvent(TableId tableId, String comment) {
        Preconditions.checkArgument(comment != null, "comment should not be null.");
        this.tableId = tableId;
        this.comment = comment;
    }

    public String getComment() {
        return comment;
    }

    @Override
    public SchemaChangeEventType getType() {
        return SchemaChangeEventType.ALTER_TABLE_COMMENT;
    }

    @Override
    public SchemaChangeEvent copy(TableId newTableId) {
        return new AlterTableCommentEvent(newTableId, comment);
    }

    @Override
    public TableId tableId() {
        return tableId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AlterTableCommentEvent)) {
            return false;
        }
        AlterTableCommentEvent that = (AlterTableCommentEvent) o;
        return Objects.equals(tableId, that.tableId) && Objects.equals(comment, that.comment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tableId, comment);
    }

    @Override
    public String toString() {
        return "AlterTableCommentEvent{"
                + "tableId="
                + tableId
                + ", comment='"
                + comment
                + '\''
                + '}';
    }
}

package org.apache.flink.cdc.common.event;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

/** An enumeration of schema change event types for {@link SchemaChangeEvent}. */
@PublicEvolving
public enum SchemaChangeEventType {
    ADD_COLUMN("add.column"),
    ALTER_COLUMN_TYPE("alter.column.type"),
    CREATE_TABLE("create.table"),
    DROP_COLUMN("drop.column"),
    DROP_TABLE("drop.table"),
    RENAME_COLUMN("rename.column"),
    TRUNCATE_TABLE("truncate.table"),
    ALTER_TABLE_COMMENT("alter.table.comment");

    private final String tag;

    SchemaChangeEventType(String tag) {
        this.tag = tag;
    }

    public String getTag() {
        return tag;
    }

    public static SchemaChangeEventType ofEvent(SchemaChangeEvent event) {
        if (event instanceof AddColumnEvent) {
            return ADD_COLUMN;
        } else if (event instanceof AlterColumnTypeEvent) {
            return ALTER_COLUMN_TYPE;
        } else if (event instanceof CreateTableEvent) {
            return CREATE_TABLE;
        } else if (event instanceof DropColumnEvent) {
            return DROP_COLUMN;
        } else if (event instanceof DropTableEvent) {
            return DROP_TABLE;
        } else if (event instanceof RenameColumnEvent) {
            return RENAME_COLUMN;
        } else if (event instanceof TruncateTableEvent) {
            return TRUNCATE_TABLE;
        } else if (event instanceof AlterTableCommentEvent) {
            return ALTER_TABLE_COMMENT;
        } else {
            throw new RuntimeException("Unknown schema change event type: " + event.getClass());
        }
    }

    public static SchemaChangeEventType ofTag(String tag) {
        switch (tag) {
            case "add.column":
                return ADD_COLUMN;
            case "alter.column.type":
                return ALTER_COLUMN_TYPE;
            case "create.table":
                return CREATE_TABLE;
            case "drop.column":
                return DROP_COLUMN;
            case "drop.table":
                return DROP_TABLE;
            case "rename.column":
                return RENAME_COLUMN;
            case "truncate.table":
                return TRUNCATE_TABLE;
            case "alter.table.comment":
                return ALTER_TABLE_COMMENT;
            default:
                throw new RuntimeException("Unknown schema change event type: " + tag);
        }
    }
}

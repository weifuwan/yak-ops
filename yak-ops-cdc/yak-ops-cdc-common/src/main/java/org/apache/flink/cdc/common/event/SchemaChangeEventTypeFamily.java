package org.apache.flink.cdc.common.event;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

import static org.apache.flink.cdc.common.event.SchemaChangeEventType.ADD_COLUMN;
import static org.apache.flink.cdc.common.event.SchemaChangeEventType.ALTER_COLUMN_TYPE;
import static org.apache.flink.cdc.common.event.SchemaChangeEventType.ALTER_TABLE_COMMENT;
import static org.apache.flink.cdc.common.event.SchemaChangeEventType.CREATE_TABLE;
import static org.apache.flink.cdc.common.event.SchemaChangeEventType.DROP_COLUMN;
import static org.apache.flink.cdc.common.event.SchemaChangeEventType.DROP_TABLE;
import static org.apache.flink.cdc.common.event.SchemaChangeEventType.RENAME_COLUMN;
import static org.apache.flink.cdc.common.event.SchemaChangeEventType.TRUNCATE_TABLE;

/**
 * An enumeration of schema change event families for clustering {@link SchemaChangeEvent}s into
 * categories.
 */
@PublicEvolving
public class SchemaChangeEventTypeFamily {

    public static final SchemaChangeEventType[] ADD = {ADD_COLUMN};

    public static final SchemaChangeEventType[] ALTER = {ALTER_COLUMN_TYPE};

    public static final SchemaChangeEventType[] CREATE = {CREATE_TABLE};

    public static final SchemaChangeEventType[] DROP = {DROP_COLUMN, DROP_TABLE};

    public static final SchemaChangeEventType[] RENAME = {RENAME_COLUMN};

    public static final SchemaChangeEventType[] TABLE = {
        CREATE_TABLE, DROP_TABLE, TRUNCATE_TABLE, ALTER_TABLE_COMMENT
    };

    public static final SchemaChangeEventType[] COLUMN = {
        ADD_COLUMN, ALTER_COLUMN_TYPE, DROP_COLUMN, RENAME_COLUMN
    };

    public static final SchemaChangeEventType[] ALL = {
        ADD_COLUMN,
        ALTER_COLUMN_TYPE,
        CREATE_TABLE,
        DROP_COLUMN,
        DROP_TABLE,
        RENAME_COLUMN,
        TRUNCATE_TABLE,
        ALTER_TABLE_COMMENT
    };

    public static final SchemaChangeEventType[] NONE = {};

    public static SchemaChangeEventType[] ofTag(String tag) {
        switch (tag) {
            case "add":
                return ADD;
            case "alter":
                return ALTER;
            case "create":
                return CREATE;
            case "drop":
                return DROP;
            case "rename":
                return RENAME;
            case "table":
                return TABLE;
            case "column":
                return COLUMN;
            case "all":
                return ALL;
            default:
                return NONE;
        }
    }
}

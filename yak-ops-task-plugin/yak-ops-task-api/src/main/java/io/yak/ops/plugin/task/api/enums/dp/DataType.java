package io.yak.ops.plugin.task.api.enums.dp;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * rule input entry value type
 */
public enum DataType {

    /**
     * 0-string
     * 1-list
     * 2-number
     * 3-sql
     */
    STRING(0, "string"),
    LIST(1, "list"),
    NUMBER(2, "number"),
    LIKE_SQL(3, "sql");

    DataType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    private final int code;
    private final String description;

    @JsonValue
    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    private static final Map<Integer, DataType> DATA_TYPE_MAP = new HashMap<>();

    static {
        for (DataType type : DataType.values()) {
            DATA_TYPE_MAP.put(type.code, type);
        }
    }

    public static DataType of(Integer status) {
        if (DATA_TYPE_MAP.containsKey(status)) {
            return DATA_TYPE_MAP.get(status);
        }
        throw new IllegalArgumentException("invalid code : " + status);
    }
}

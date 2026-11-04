package io.yak.ops.plugin.task.api.enums.dp;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RuleType {

    /**
     * 0-single_table
     * 1-single_table_custom_sql
     * 2-multi_table_accuracy
     * 3-multi_table_comparison
     */
    SINGLE_TABLE(0, "single_table"),
    SINGLE_TABLE_CUSTOM_SQL(1, "single_table_custom_sql"),
    MULTI_TABLE_ACCURACY(2, "multi_table_accuracy"),
    MULTI_TABLE_COMPARISON(3, "multi_table_comparison");

    RuleType(int code, String description) {
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

    private static final Map<Integer, RuleType> VALUES_MAP = new HashMap<>();

    static {
        for (RuleType type : RuleType.values()) {
            VALUES_MAP.put(type.code, type);
        }
    }

    public static RuleType of(Integer status) {
        if (VALUES_MAP.containsKey(status)) {
            return VALUES_MAP.get(status);
        }
        throw new IllegalArgumentException("invalid code : " + status);
    }
}

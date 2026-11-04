package io.yak.ops.plugin.task.api.enums.dp;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ExecuteSqlType {

    /**
     * 0-middle
     * 1-statistics
     * 2-comparison
     */
    MIDDLE(0, "middle"),
    STATISTICS(1, "statistics"),
    COMPARISON(2, "comparison");

    ExecuteSqlType(int code, String description) {
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

    private static final Map<Integer, ExecuteSqlType> VALUES_MAP = new HashMap<>();

    static {
        for (ExecuteSqlType type : ExecuteSqlType.values()) {
            VALUES_MAP.put(type.code, type);
        }
    }

    public static ExecuteSqlType of(Integer status) {
        if (VALUES_MAP.containsKey(status)) {
            return VALUES_MAP.get(status);
        }
        throw new IllegalArgumentException("invalid code : " + status);
    }
}

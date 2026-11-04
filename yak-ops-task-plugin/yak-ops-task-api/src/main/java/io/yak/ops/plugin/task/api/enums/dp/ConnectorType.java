package io.yak.ops.plugin.task.api.enums.dp;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ConnectorType {

    /**
     * 0-jdbc
     * 1-hive
     */
    JDBC(0, "JDBC"),
    HIVE(1, "HIVE");

    ConnectorType(int code, String description) {
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

    private static final Map<Integer, ConnectorType> VALUES_MAP = new HashMap<>();

    static {
        for (ConnectorType type : ConnectorType.values()) {
            VALUES_MAP.put(type.code, type);
        }
    }

    public static ConnectorType of(Integer status) {
        if (VALUES_MAP.containsKey(status)) {
            return VALUES_MAP.get(status);
        }
        throw new IllegalArgumentException("invalid code : " + status);
    }
}

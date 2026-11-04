package io.yak.ops.plugin.task.api.enums.dp;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * form options source type
 */
public enum OptionSourceType {

    /**
     * 0-default
     * 1-datasource_id
     * 2-datasource_type
     * 3-comparison_type
     */
    DEFAULT(0, "default"),
    DATASOURCE_ID(1, "datasource_id"),
    DATASOURCE_TYPE(2, "datasource_type"),
    COMPARISON_TYPE(3, "comparison_type");

    OptionSourceType(int code, String description) {
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

    private static final Map<Integer, OptionSourceType> VALUES_MAP = new HashMap<>();

    static {
        for (OptionSourceType type : OptionSourceType.values()) {
            VALUES_MAP.put(type.code, type);
        }
    }

    public static OptionSourceType of(Integer status) {
        if (VALUES_MAP.containsKey(status)) {
            return VALUES_MAP.get(status);
        }
        throw new IllegalArgumentException("invalid code : " + status);
    }
}

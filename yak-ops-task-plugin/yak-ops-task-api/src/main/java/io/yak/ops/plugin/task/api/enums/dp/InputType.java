package io.yak.ops.plugin.task.api.enums.dp;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * frontend form input entry type
 */
public enum InputType {

    /**
     * 0-default
     * 1-statistics
     * 2-comparison
     * 3-check
     */
    DEFAULT(0, "default"),
    STATISTICS(1, "statistics"),
    COMPARISON(2, "comparison"),
    CHECK(3, "check");

    InputType(int code, String description) {
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

    private static final Map<Integer, InputType> VALUES_MAP = new HashMap<>();

    static {
        for (InputType type : InputType.values()) {
            VALUES_MAP.put(type.code, type);
        }
    }

    public static InputType of(Integer status) {
        if (VALUES_MAP.containsKey(status)) {
            return VALUES_MAP.get(status);
        }
        throw new IllegalArgumentException("invalid code : " + status);
    }
}

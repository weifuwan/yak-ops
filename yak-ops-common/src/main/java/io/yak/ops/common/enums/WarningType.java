package io.yak.ops.common.enums;

import static java.util.stream.Collectors.toMap;

import java.util.Arrays;
import java.util.Map;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.google.common.base.Functions;

import lombok.Getter;

/**
 * types for whether to send warning when workflow instance ends
 */
@Getter
public enum WarningType {

    /**
     * 0 do not send warning;
     * 1 send if workflow success;
     * 2 send if workflow failed;
     * 3 send if workflow ends, whatever the result;
     */
    NONE(0, "none"),
    SUCCESS(1, "success"),
    FAILURE(2, "failure"),
    ALL(3, "all");

    WarningType(int code, String descp) {
        this.code = code;
        this.descp = descp;
    }

    @EnumValue
    private final int code;
    private final String descp;

    private static final Map<String, WarningType> WARNING_TYPE_MAP =
            Arrays.stream(WarningType.values()).collect(toMap(WarningType::getDescp, Functions.identity()));

    public static WarningType of(String descp) {
        if (WARNING_TYPE_MAP.containsKey(descp)) {
            return WARNING_TYPE_MAP.get(descp);
        }
        return null;
    }
}

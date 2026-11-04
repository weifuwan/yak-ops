package io.yak.ops.alert.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ShowType {

    /**
     * 0 TABLE;
     * 1 TEXT;
     * 2 attachment;
     * 3 TABLE+attachment;
     * 4 MARKDOWN;
     */
    TABLE(0, "table"),
    TEXT(1, "text"),
    ATTACHMENT(2, "attachment"),
    TABLE_ATTACHMENT(3, "table attachment"),
    MARKDOWN(4, "markdown"),
    ;

    private final int code;

    private final String descp;

}

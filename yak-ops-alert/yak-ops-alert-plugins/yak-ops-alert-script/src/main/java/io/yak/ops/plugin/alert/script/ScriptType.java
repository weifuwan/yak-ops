package io.yak.ops.plugin.alert.script;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ScriptType {

    SHELL(0, "SHELL"),
    ;

    private final int code;
    private final String descp;
}

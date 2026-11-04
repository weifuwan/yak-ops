package io.yak.ops.common.enums;

import lombok.Getter;

@Getter
public enum ContextType {

    DEPENDENT_RESULT_CONTEXT;

    public static ContextType of(String name) {
        for (ContextType contextType : values()) {
            if (contextType.name().equalsIgnoreCase(name)) {
                return contextType;
            }
        }
        return null;
    }
}

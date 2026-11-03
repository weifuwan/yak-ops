package io.yak.ops.configuration.validation;

import java.util.Objects;

/** Machine-readable error with a safe option path and stable validation category. */
public record ConfigValidationError(String path, String rule, String message) {

    public ConfigValidationError {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(message, "message");
    }
}

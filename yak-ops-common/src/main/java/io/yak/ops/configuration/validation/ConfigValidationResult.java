package io.yak.ops.configuration.validation;

import java.util.List;

/** Immutable validation result suitable for API and CLI adapters. */
public record ConfigValidationResult(boolean valid, List<ConfigValidationError> errors) {

    public ConfigValidationResult {
        errors = List.copyOf(errors);
        if (valid != errors.isEmpty()) {
            throw new IllegalArgumentException("Validation result and errors disagree");
        }
    }

    public static ConfigValidationResult success() {
        return new ConfigValidationResult(true, List.of());
    }

    public static ConfigValidationResult failure(List<ConfigValidationError> errors) {
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("Failure requires at least one error");
        }
        return new ConfigValidationResult(false, errors);
    }
}

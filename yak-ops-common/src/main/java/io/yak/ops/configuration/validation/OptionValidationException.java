package io.yak.ops.configuration.validation;

import java.util.List;

/** Indicates a failed configuration rule without including configured or secret values. */
public final class OptionValidationException extends IllegalArgumentException {

    private final List<ConfigValidationError> errors;

    public OptionValidationException(List<ConfigValidationError> errors) {
        super("Option validation failed (" + errors.size() + " errors)");
        this.errors = List.copyOf(errors);
    }

    public List<ConfigValidationError> errors() {
        return errors;
    }
}

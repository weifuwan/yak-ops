package io.yak.ops.configuration.rule;

import io.yak.ops.configuration.Option;
import java.util.List;
import java.util.Objects;

/** A required, mutually exclusive, bundled, or conditionally required option group. */
public record RequiredOption(Kind kind, List<Option<?>> options, Expression when) {

    public enum Kind {
        REQUIRED,
        EXCLUSIVE,
        BUNDLED,
        CONDITIONAL
    }

    public RequiredOption {
        Objects.requireNonNull(kind, "kind");
        options = List.copyOf(options);
        if (options.isEmpty()) {
            throw new IllegalArgumentException("Required option group must not be empty");
        }
        if (kind == Kind.CONDITIONAL && when == null) {
            throw new IllegalArgumentException("Conditional group requires a condition");
        }
    }
}

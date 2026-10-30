package io.yak.ops.sync.runtime.validation;

/** Marks physical schema errors without exposing untrusted driver or DDL diagnostics. */
public final class SchemaValidationException extends IllegalArgumentException {
    private final RuntimeException failure;

    public SchemaValidationException(RuntimeException failure) {
        super("Physical schema validation failed", failure);
        this.failure = failure;
    }

    public RuntimeException failure() {
        return failure;
    }
}

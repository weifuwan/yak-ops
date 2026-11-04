package io.yak.ops.registry.api;

public final class RegistryException extends RuntimeException {

    public RegistryException(String message, Throwable cause) {
        super(message, cause);
    }

    public RegistryException(String message) {
        super(message);
    }
}

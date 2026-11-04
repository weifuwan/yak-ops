package org.apache.flink.cdc.connectors.jdbc.exception;

/**
 * A {@link RuntimeException} thrown by specific {@link
 * org.apache.flink.cdc.connectors.jdbc.dialect.JdbcSinkDialect}.
 */
public class JdbcSinkDialectException extends RuntimeException {
    public JdbcSinkDialectException(String message) {
        super(message);
    }

    public JdbcSinkDialectException(Throwable cause) {
        super(cause);
    }

    public JdbcSinkDialectException(String message, Throwable cause) {
        super(message, cause);
    }
}

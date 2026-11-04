package org.apache.flink.cdc.connectors.jdbc.catalog;

/** Exception raised when inspecting or changing a JDBC target database schema. */
public class JdbcCatalogException extends RuntimeException {
    public JdbcCatalogException(String message) {
        super(message);
    }

    public JdbcCatalogException(String message, Throwable cause) {
        super(message, cause);
    }
}

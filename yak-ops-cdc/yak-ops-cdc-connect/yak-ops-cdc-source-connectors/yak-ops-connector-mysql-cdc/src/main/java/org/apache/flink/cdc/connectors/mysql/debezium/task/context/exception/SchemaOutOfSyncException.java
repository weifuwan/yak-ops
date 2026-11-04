package org.apache.flink.cdc.connectors.mysql.debezium.task.context.exception;

/**
 * A wrapper class for clearly show the possible reason of a schema-out-of-sync exception thrown
 * inside Debezium.
 */
public class SchemaOutOfSyncException extends Exception {
    public SchemaOutOfSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}

package io.yak.ops.modeling.ddl.doris;

/** Indicates that DDL execution completed but the resulting physical schema did not verify. */
public class SchemaVerificationException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final String physicalSchema;

    public DorisSchemaVerificationException(String message, String physicalSchema) {
        super(message);
        this.physicalSchema = physicalSchema;
    }

    public DorisSchemaVerificationException(
            String message, String physicalSchema, Throwable cause) {
        super(message, cause);
        this.physicalSchema = physicalSchema;
    }

    /** Returns the observed SHOW CREATE TABLE snapshot, when it was readable. */
    public String getPhysicalSchema() {
        return physicalSchema;
    }
}

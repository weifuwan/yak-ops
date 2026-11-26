package io.yak.ops.modeling.ddl.doris;

/** Indicates that DDL execution completed but the resulting physical schema did not verify. */
public class DorisSchemaVerificationException extends IllegalStateException {

    private final String physicalSchema;

    public DorisSchemaVerificationException(String message, String physicalSchema) {
        super(message);
        this.physicalSchema = physicalSchema;
    }

    /** Returns the observed SHOW CREATE TABLE snapshot, when it was readable. */
    public String getPhysicalSchema() {
        return physicalSchema;
    }
}

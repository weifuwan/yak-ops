package io.yak.ops.core.event;

import java.io.Serializable;

/**
 * Database column properties required for faithful initial table creation.
 *
 * <p>A null generation flag means the source metadata could not establish the feature.
 * Default expressions are metadata, never executable SQL until a target dialect validates them.
 */
public record ColumnProperties(
        String defaultExpression,
        Boolean autoIncrement,
        Boolean generated,
        String onUpdateExpression,
        Boolean defaultGenerated)
        implements Serializable {
    private static final long serialVersionUID = 1L;

    public ColumnProperties(String defaultExpression, Boolean autoIncrement, Boolean generated) {
        this(defaultExpression, autoIncrement, generated, null, null);
    }

    public ColumnProperties(
            String defaultExpression, Boolean autoIncrement, Boolean generated, String onUpdateExpression) {
        this(defaultExpression, autoIncrement, generated, onUpdateExpression, null);
    }

    public boolean hasFeatures() {
        return defaultExpression != null
                || Boolean.TRUE.equals(autoIncrement)
                || Boolean.TRUE.equals(generated)
                || onUpdateExpression != null
                || Boolean.TRUE.equals(defaultGenerated);
    }

    public boolean isKnown() {
        return autoIncrement != null && generated != null;
    }
}

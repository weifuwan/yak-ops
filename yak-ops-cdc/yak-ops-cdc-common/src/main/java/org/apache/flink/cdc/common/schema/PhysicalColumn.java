package org.apache.flink.cdc.common.schema;

import org.apache.flink.cdc.common.annotation.PublicEvolving;
import org.apache.flink.cdc.common.types.DataType;

import javax.annotation.Nullable;

/** Representation of a physical column. */
@PublicEvolving
public class PhysicalColumn extends Column {

    private static final long serialVersionUID = 1L;

    public PhysicalColumn(String name, DataType type, @Nullable String comment) {
        super(name, type, comment);
    }

    public PhysicalColumn(
            String name, DataType type, @Nullable String comment, @Nullable String defaultValue) {
        super(name, type, comment, defaultValue);
    }

    @Override
    public boolean isPhysical() {
        return true;
    }

    @Override
    public Column copy(DataType newType) {
        return new PhysicalColumn(name, newType, comment, defaultValueExpression);
    }

    @Override
    public Column copy(String newName) {
        return new PhysicalColumn(newName, type, comment, defaultValueExpression);
    }
}

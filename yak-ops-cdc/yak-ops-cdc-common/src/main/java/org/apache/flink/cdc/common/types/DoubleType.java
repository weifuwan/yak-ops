package org.apache.flink.cdc.common.types;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

import java.util.Collections;
import java.util.List;

/** Data type of an 8-byte double precision floating point number. */
@PublicEvolving
public final class DoubleType extends DataType {

    private static final long serialVersionUID = 1L;

    private static final String FORMAT = "DOUBLE";

    public DoubleType(boolean isNullable) {
        super(isNullable, DataTypeRoot.DOUBLE);
    }

    public DoubleType() {
        this(true);
    }

    @Override
    public DataType copy(boolean isNullable) {
        return new DoubleType(isNullable);
    }

    @Override
    public String asSerializableString() {
        return withNullability(FORMAT);
    }

    @Override
    public List<DataType> getChildren() {
        return Collections.emptyList();
    }

    @Override
    public <R> R accept(DataTypeVisitor<R> visitor) {
        return visitor.visit(this);
    }
}

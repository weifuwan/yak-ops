package org.apache.flink.cdc.common.types;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

import java.util.Collections;
import java.util.List;

/**
 * Data type of a 4-byte single precision floating point number. Compared to the SQL standard, the
 * type does not take parameters.
 */
@PublicEvolving
public final class FloatType extends DataType {

    private static final long serialVersionUID = 1L;

    private static final String FORMAT = "FLOAT";

    public FloatType(boolean isNullable) {
        super(isNullable, DataTypeRoot.FLOAT);
    }

    public FloatType() {
        this(true);
    }

    @Override
    public DataType copy(boolean isNullable) {
        return new FloatType(isNullable);
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

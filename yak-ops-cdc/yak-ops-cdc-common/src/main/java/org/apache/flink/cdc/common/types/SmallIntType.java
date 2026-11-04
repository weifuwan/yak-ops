package org.apache.flink.cdc.common.types;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

import java.util.Collections;
import java.util.List;

/** Data type of a 2-byte signed integer with values from -32,768 to 32,767. */
@PublicEvolving
public final class SmallIntType extends DataType {

    private static final long serialVersionUID = 1L;

    private static final String FORMAT = "SMALLINT";

    public SmallIntType(boolean isNullable) {
        super(isNullable, DataTypeRoot.SMALLINT);
    }

    public SmallIntType() {
        this(true);
    }

    @Override
    public DataType copy(boolean isNullable) {
        return new SmallIntType(isNullable);
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

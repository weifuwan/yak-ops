package org.apache.flink.cdc.common.types;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

import java.util.Collections;
import java.util.List;

/** Data type of a boolean with a (possibly) three-valued logic of {@code TRUE, FALSE, UNKNOWN}. */
@PublicEvolving
public class BooleanType extends DataType {

    private static final long serialVersionUID = 1L;

    private static final String FORMAT = "BOOLEAN";

    public BooleanType(boolean isNullable) {
        super(isNullable, DataTypeRoot.BOOLEAN);
    }

    public BooleanType() {
        this(true);
    }

    @Override
    public DataType copy(boolean isNullable) {
        return new BooleanType(isNullable);
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

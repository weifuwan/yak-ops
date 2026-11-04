package org.apache.flink.cdc.common.types;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

import java.util.Collections;
import java.util.List;

/** Data type of a 4-byte signed integer with values from -2,147,483,648 to 2,147,483,647. */
@PublicEvolving
public class IntType extends DataType {

    private static final long serialVersionUID = 1L;

    private static final String FORMAT = "INT";

    public IntType(boolean isNullable) {
        super(isNullable, DataTypeRoot.INTEGER);
    }

    public IntType() {
        this(true);
    }

    @Override
    public DataType copy(boolean isNullable) {
        return new IntType(isNullable);
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

package org.apache.flink.cdc.common.types;

import org.apache.flink.cdc.common.annotation.PublicEvolving;
import org.apache.flink.cdc.common.utils.Preconditions;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Data type of an array of elements with same subtype. Compared to the SQL standard, the maximum
 * cardinality of an array cannot be specified but is fixed at {@link Integer#MAX_VALUE}. Also, any
 * valid type is supported as a subtype.
 */
@PublicEvolving
public final class ArrayType extends DataType {

    private static final long serialVersionUID = 1L;

    public static final String FORMAT = "ARRAY<%s>";

    private final DataType elementType;

    public ArrayType(boolean isNullable, DataType elementType) {
        super(isNullable, DataTypeRoot.ARRAY);
        this.elementType =
                Preconditions.checkNotNull(elementType, "Element type must not be null.");
    }

    public ArrayType(DataType elementType) {
        this(true, elementType);
    }

    public DataType getElementType() {
        return elementType;
    }

    @Override
    public DataType copy(boolean isNullable) {
        return new ArrayType(isNullable, elementType.copy());
    }

    @Override
    public String asSummaryString() {
        return withNullability(FORMAT, elementType.asSummaryString());
    }

    @Override
    public String asSerializableString() {
        return withNullability(FORMAT, elementType.asSerializableString());
    }

    @Override
    public List<DataType> getChildren() {
        return Collections.singletonList(elementType);
    }

    @Override
    public <R> R accept(DataTypeVisitor<R> visitor) {
        return visitor.visit(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        ArrayType arrayType = (ArrayType) o;
        return elementType.equals(arrayType.elementType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), elementType);
    }
}

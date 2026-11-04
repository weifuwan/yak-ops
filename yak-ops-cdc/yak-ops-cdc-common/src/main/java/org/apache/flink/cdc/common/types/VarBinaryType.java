package org.apache.flink.cdc.common.types;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Data type of a variable-length binary string (=a sequence of bytes). */
@PublicEvolving
public final class VarBinaryType extends DataType {

    private static final long serialVersionUID = 1L;

    public static final int MIN_LENGTH = 1;

    public static final int DEFAULT_LENGTH = 1;

    public static final int MAX_LENGTH = Integer.MAX_VALUE;

    private static final String FORMAT = "VARBINARY(%d)";

    private static final String MAX_FORMAT = "BYTES";

    private final int length;

    public VarBinaryType(boolean isNullable, int length) {
        super(isNullable, DataTypeRoot.VARBINARY);
        if (length < MIN_LENGTH) {
            throw new IllegalArgumentException(
                    String.format(
                            "Variable binary string length must be between %d and %d (both inclusive).",
                            MIN_LENGTH, MAX_LENGTH));
        }
        this.length = length;
    }

    public VarBinaryType(int length) {
        this(true, length);
    }

    public VarBinaryType() {
        this(DEFAULT_LENGTH);
    }

    public int getLength() {
        return length;
    }

    @Override
    public DataType copy(boolean isNullable) {
        return new VarBinaryType(isNullable, length);
    }

    @Override
    public String asSerializableString() {
        if (length == MAX_LENGTH) {
            return withNullability(MAX_FORMAT);
        }
        return withNullability(FORMAT, length);
    }

    @Override
    public List<DataType> getChildren() {
        return Collections.emptyList();
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
        VarBinaryType that = (VarBinaryType) o;
        return length == that.length;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), length);
    }

    public static VarBinaryType bytesType() {
        return new VarBinaryType(true, MAX_LENGTH);
    }
}

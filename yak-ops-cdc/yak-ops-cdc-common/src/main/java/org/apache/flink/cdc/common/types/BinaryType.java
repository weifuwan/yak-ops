package org.apache.flink.cdc.common.types;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Data type of a fixed-length binary string (=a sequence of bytes).
 *
 * <p>For expressing a zero-length binary string literal, this type does also support {@code n} to
 * be 0. However, this is not exposed through the API.
 */
@PublicEvolving
public final class BinaryType extends DataType {

    private static final long serialVersionUID = 1L;

    public static final int EMPTY_LITERAL_LENGTH = 0;

    public static final int MIN_LENGTH = 1;

    public static final int MAX_LENGTH = Integer.MAX_VALUE;

    public static final int DEFAULT_LENGTH = 1;

    private static final String FORMAT = "BINARY(%d)";

    private final int length;

    public BinaryType(boolean isNullable, int length) {
        super(isNullable, DataTypeRoot.BINARY);
        if (length < MIN_LENGTH) {
            throw new IllegalArgumentException(
                    String.format(
                            "Binary string length must be between %d and %d (both inclusive).",
                            MIN_LENGTH, MAX_LENGTH));
        }
        this.length = length;
    }

    public BinaryType(int length) {
        this(true, length);
    }

    public BinaryType() {
        this(DEFAULT_LENGTH);
    }

    /** Helper constructor for {@link #ofEmptyLiteral()} and {@link #copy(boolean)}. */
    private BinaryType(int length, boolean isNullable) {
        super(isNullable, DataTypeRoot.BINARY);
        this.length = length;
    }

    /**
     * The SQL standard defines that character string literals are allowed to be zero-length strings
     * (i.e., to contain no characters) even though it is not permitted to declare a type that is
     * zero. For consistent behavior, the same logic applies to binary strings.
     *
     * <p>This method enables this special kind of binary string.
     *
     * <p>Zero-length binary strings have no serializable string representation.
     */
    public static BinaryType ofEmptyLiteral() {
        return new BinaryType(EMPTY_LITERAL_LENGTH, false);
    }

    public int getLength() {
        return length;
    }

    @Override
    public DataType copy(boolean isNullable) {
        return new BinaryType(length, isNullable);
    }

    @Override
    public String asSerializableString() {
        if (length == EMPTY_LITERAL_LENGTH) {
            throw new IllegalArgumentException(
                    "Zero-length binary strings have no serializable string representation.");
        }
        return withNullability(FORMAT, length);
    }

    @Override
    public String asSummaryString() {
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
        BinaryType that = (BinaryType) o;
        return length == that.length;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), length);
    }
}

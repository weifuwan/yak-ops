package io.yak.ops.core.types;

/** Variable-width characters; max length represents unbounded STRING metadata. */
public final class VarCharType extends LogicalType {

    public static final int MAX_LENGTH = Integer.MAX_VALUE;

    private final int length;

    public VarCharType(int length) {
        this(true, length);
    }

    public VarCharType(boolean nullable, int length) {
        super(nullable, LogicalTypeRoot.VARCHAR);
        if (length < 1) {
            throw new IllegalArgumentException("VARCHAR length must be positive");
        }
        this.length = length;
    }

    public int length() {
        return length;
    }

    @Override
    public VarCharType copy(boolean nullable) {
        return new VarCharType(nullable, length);
    }

    @Override
    public String asSerializableString() {
        return withNullability(length == MAX_LENGTH ? "STRING" : "VARCHAR(" + length + ")");
    }

    @Override
    public Class<?> getDefaultConversion() {
        return String.class;
    }

    @Override
    protected Object parameters() {
        return length;
    }
}

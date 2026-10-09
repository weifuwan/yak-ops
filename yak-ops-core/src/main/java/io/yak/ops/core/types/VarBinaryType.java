package io.yak.ops.core.types;

/** Variable-width bytes; max length represents BINARY/BYTES without a known capacity. */
public final class VarBinaryType extends LogicalType {

    public static final int MAX_LENGTH = Integer.MAX_VALUE;

    private final int length;

    public VarBinaryType(int length) {
        this(true, length);
    }

    public VarBinaryType(boolean nullable, int length) {
        super(nullable, LogicalTypeRoot.VARBINARY);
        if (length < 1) {
            throw new IllegalArgumentException("VARBINARY length must be positive");
        }
        this.length = length;
    }

    public int length() {
        return length;
    }

    @Override
    public VarBinaryType copy(boolean nullable) {
        return new VarBinaryType(nullable, length);
    }

    @Override
    public String asSerializableString() {
        return withNullability(length == MAX_LENGTH ? "BYTES" : "VARBINARY(" + length + ")");
    }

    @Override
    public Class<?> getDefaultConversion() {
        return byte[].class;
    }

    @Override
    protected Object parameters() {
        return length;
    }
}

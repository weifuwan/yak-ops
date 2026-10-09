package io.yak.ops.core.types;

/** Fixed-width bytes with explicit column capacity. */
public final class BinaryType extends LogicalType {

    private final int length;

    public BinaryType(int length) {
        this(true, length);
    }

    public BinaryType(boolean nullable, int length) {
        super(nullable, LogicalTypeRoot.BINARY);
        if (length < 1) {
            throw new IllegalArgumentException("BINARY length must be positive");
        }
        this.length = length;
    }

    public int length() {
        return length;
    }

    @Override
    public BinaryType copy(boolean nullable) {
        return new BinaryType(nullable, length);
    }

    @Override
    public String asSerializableString() {
        return withNullability("BINARY(" + length + ")");
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

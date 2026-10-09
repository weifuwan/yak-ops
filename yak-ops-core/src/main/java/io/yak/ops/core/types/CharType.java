package io.yak.ops.core.types;

/** Fixed-width character type preserving the declared capacity. */
public final class CharType extends LogicalType {

    private final int length;

    public CharType(int length) {
        this(true, length);
    }

    public CharType(boolean nullable, int length) {
        super(nullable, LogicalTypeRoot.CHAR);
        if (length < 1) {
            throw new IllegalArgumentException("CHAR length must be positive");
        }
        this.length = length;
    }

    public int length() {
        return length;
    }

    @Override
    public CharType copy(boolean nullable) {
        return new CharType(nullable, length);
    }

    @Override
    public String asSerializableString() {
        return withNullability("CHAR(" + length + ")");
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

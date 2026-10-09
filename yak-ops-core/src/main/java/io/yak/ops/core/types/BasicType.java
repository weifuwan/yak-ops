package io.yak.ops.core.types;

/** Unparameterized primitive logical type; variable-width and temporal types use dedicated classes. */
public final class BasicType extends LogicalType {

    public BasicType(LogicalTypeRoot typeRoot) {
        this(true, typeRoot);
    }

    public BasicType(boolean nullable, LogicalTypeRoot typeRoot) {
        super(nullable, typeRoot);
        switch (typeRoot) {
            case BOOLEAN, TINYINT, SMALLINT, INTEGER, BIGINT, FLOAT, DOUBLE, DATE -> {}
            default -> throw new IllegalArgumentException("Parameterized logical type requires its own class");
        }
    }

    @Override
    public BasicType copy(boolean nullable) {
        return new BasicType(nullable, getTypeRoot());
    }

    @Override
    public String asSerializableString() {
        return withNullability(
                getTypeRoot() == LogicalTypeRoot.INTEGER ? "INT" : getTypeRoot().name());
    }

    @Override
    public Class<?> getDefaultConversion() {
        return switch (getTypeRoot()) {
            case BOOLEAN -> Boolean.class;
            case TINYINT -> Byte.class;
            case SMALLINT -> Short.class;
            case INTEGER -> Integer.class;
            case BIGINT -> Long.class;
            case FLOAT -> Float.class;
            case DOUBLE -> Double.class;
            case DATE -> java.time.LocalDate.class;
            default -> throw new IllegalStateException("Unsupported basic type root");
        };
    }
}

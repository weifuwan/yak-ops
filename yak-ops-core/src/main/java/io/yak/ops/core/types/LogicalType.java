package io.yak.ops.core.types;

/**
 * Type contract for table schemas independently of the database-specific native SQL name.
 *
 * <p>Parameterized types retain their parameters and never require a JDBC driver to be loaded.
 */
public sealed interface LogicalType permits BasicType, DecimalType {

    TypeKind kind();
}

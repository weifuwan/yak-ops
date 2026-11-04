package org.apache.flink.cdc.common.types;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

import java.util.Collections;
import java.util.List;

/**
 * Data type of an 8-byte signed integer with values from -9,223,372,036,854,775,808 to
 * 9,223,372,036,854,775,807.
 */
@PublicEvolving
public final class BigIntType extends DataType {

    private static final long serialVersionUID = 1L;

    private static final String FORMAT = "BIGINT";

    public BigIntType(boolean isNullable) {
        super(isNullable, DataTypeRoot.BIGINT);
    }

    public BigIntType() {
        this(true);
    }

    @Override
    public DataType copy(boolean isNullable) {
        return new BigIntType(isNullable);
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

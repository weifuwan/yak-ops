package org.apache.flink.cdc.common.types;

import org.apache.flink.annotation.PublicEvolving;

import java.util.Collections;
import java.util.List;

/**
 * Data type of semi-structured data.
 *
 * <p>The type supports storing any semi-structured data, including ARRAY, MAP, and scalar types.
 * VARIANT can only store MAP types with keys of type STRING. The data type of the fields are stored
 * in the data structure, which is close to the semantics of JSON. Compared to ROW and STRUCTURED
 * type, VARIANT type has the flexibility that supports highly nested and evolving schema.
 *
 * <p>The serializable string representation of this type is {@code VARIANT}.
 */
@PublicEvolving
public final class VariantType extends DataType {

    public VariantType(boolean isNullable) {
        super(isNullable, DataTypeRoot.VARIANT);
    }

    public VariantType() {
        this(true);
    }

    @Override
    public DataType copy(boolean isNullable) {
        return new VariantType(isNullable);
    }

    @Override
    public String asSerializableString() {
        return withNullability("VARIANT");
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

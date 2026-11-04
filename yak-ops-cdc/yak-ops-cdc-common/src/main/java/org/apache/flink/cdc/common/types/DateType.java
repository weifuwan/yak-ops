package org.apache.flink.cdc.common.types;

import org.apache.flink.cdc.common.annotation.PublicEvolving;

import java.util.Collections;
import java.util.List;

/**
 * Data type of a date consisting of {@code year-month-day} with values ranging from {@code
 * 0000-01-01} to {@code 9999-12-31}. Compared to the SQL standard, the range starts at year {@code
 * 0000}.
 *
 * <p>A conversion from and to {@code int} describes the number of days since epoch.
 */
@PublicEvolving
public final class DateType extends DataType {

    private static final long serialVersionUID = 1L;

    private static final String FORMAT = "DATE";

    public DateType(boolean isNullable) {
        super(isNullable, DataTypeRoot.DATE);
    }

    public DateType() {
        this(true);
    }

    @Override
    public DataType copy(boolean isNullable) {
        return new DateType(isNullable);
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

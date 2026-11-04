package org.apache.flink.cdc.runtime.parser.metadata;

import org.apache.flink.cdc.common.schema.Column;
import org.apache.flink.cdc.runtime.typeutils.CalciteDataTypeConverter;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.impl.AbstractTable;

import java.util.List;

/** TransformTable to generate the metadata of calcite. */
public class TransformTable extends AbstractTable {

    private String name;

    private List<Column> columns;

    public TransformTable(String name, List<Column> columns) {
        this.name = name;
        this.columns = columns;
    }

    public String getName() {
        return name;
    }

    @Override
    public RelDataType getRowType(RelDataTypeFactory relDataTypeFactory) {
        return CalciteDataTypeConverter.convertCalciteRelDataType(relDataTypeFactory, columns);
    }
}

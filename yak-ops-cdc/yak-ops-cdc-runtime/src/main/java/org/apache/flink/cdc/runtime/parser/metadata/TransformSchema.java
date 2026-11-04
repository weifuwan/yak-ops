package org.apache.flink.cdc.runtime.parser.metadata;

import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.impl.AbstractSchema;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** TransformSchema to generate the metadata of calcite. */
public class TransformSchema extends AbstractSchema {

    private String name;
    private List<TransformTable> tables;

    public TransformSchema(String name, List<TransformTable> tables) {
        this.name = name;
        this.tables = tables;
    }

    @Override
    public Map<String, Table> getTableMap() {
        return tables.stream().collect(Collectors.toMap(TransformTable::getName, t -> t));
    }
}

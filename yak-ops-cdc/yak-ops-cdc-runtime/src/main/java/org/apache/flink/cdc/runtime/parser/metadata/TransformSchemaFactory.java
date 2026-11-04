package org.apache.flink.cdc.runtime.parser.metadata;

import org.apache.flink.cdc.common.schema.Column;
import org.apache.flink.cdc.common.utils.StringUtils;

import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.SchemaFactory;
import org.apache.calcite.schema.SchemaPlus;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** TransformSchemaFactory to generate the metadata of calcite. */
public class TransformSchemaFactory implements SchemaFactory {

    public static final TransformSchemaFactory INSTANCE = new TransformSchemaFactory();

    private TransformSchemaFactory() {}

    @Override
    public Schema create(SchemaPlus schemaPlus, String schemaName, Map<String, Object> operand) {
        if (StringUtils.isNullOrWhitespaceOnly(schemaName)) {
            schemaName = "default_schema";
        }
        String tableName = String.valueOf(operand.get("tableName"));
        List<Column> columns = (List<Column>) operand.get("columns");
        return new TransformSchema(
                schemaName, Arrays.asList(new TransformTable(tableName, columns)));
    }
}

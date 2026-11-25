package io.yak.ops.modeling.ddl.doris;

import io.yak.ops.dao.entity.enums.DimensionDataTypeEnum;
import io.yak.ops.dao.entity.enums.DorisDataModelEnum;
import io.yak.ops.modeling.ddl.model.DdlColumnDefinition;
import io.yak.ops.modeling.ddl.model.DdlTableDefinition;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.doris.flink.catalog.DorisTypeMapper;
import org.apache.doris.flink.catalog.doris.DataModel;
import org.apache.doris.flink.catalog.doris.DorisSchemaFactory;
import org.apache.doris.flink.catalog.doris.FieldSchema;
import org.apache.doris.flink.catalog.doris.TableSchema;
import org.apache.flink.table.api.DataTypes;
import org.apache.flink.table.types.DataType;
import org.springframework.stereotype.Component;

/**
 * Isolates the direct Flink Table API and Doris Connector SDK integration.
 * No application service or DDL planner should depend on these SDK types.
 */
@Component
public class DorisConnectorAdapter {

    public int getMaxCharLength() {
        return DorisTypeMapper.MAX_CHAR_SIZE / 3;
    }

    public int getMaxVarcharLength() {
        return (DorisTypeMapper.MAX_VARCHAR_SIZE - 1) / 3;
    }

    public String toDorisType(DdlColumnDefinition column) {
        return DorisTypeMapper.toDorisType(toFlinkType(column));
    }

    public String generateCreateTableDdl(
            DdlTableDefinition table, DorisDataModelEnum tableModel, List<String> keyFields) {
        Map<String, FieldSchema> fieldSchemas = new LinkedHashMap<>();
        for (DdlColumnDefinition column : table.getColumns()) {
            fieldSchemas.put(
                    column.getName(),
                    new FieldSchema(
                            column.getName(),
                            toDorisType(column),
                            column.getDefaultValue(),
                            StringUtils.defaultIfBlank(column.getComment(), "")));
        }

        TableSchema schema = new TableSchema();
        schema.setDatabase(table.getDatabase());
        schema.setTable(table.getTableName());
        schema.setTableComment(StringUtils.defaultIfBlank(table.getComment(), ""));
        schema.setFields(fieldSchemas);
        schema.setModel(DataModel.valueOf(tableModel.name()));
        schema.setKeys(keyFields);
        schema.setDistributeKeys(Collections.singletonList(keyFields.get(0)));
        schema.setProperties(Collections.emptyMap());
        schema.setTableBuckets(null);

        String generatedDdl = DorisSchemaFactory.generateCreateTableDDL(schema);
        return generatedDdl.replaceFirst(
                "(?i)^CREATE TABLE IF NOT EXISTS\\s+", "CREATE TABLE ");
    }

    private DataType toFlinkType(DdlColumnDefinition column) {
        DimensionDataTypeEnum type = column.getDataType();
        switch (type) {
            case TINYINT: return DataTypes.TINYINT();
            case SMALLINT: return DataTypes.SMALLINT();
            case INT: return DataTypes.INT();
            case BIGINT: return DataTypes.BIGINT();
            case BOOLEAN: return DataTypes.BOOLEAN();
            case CHAR: return DataTypes.CHAR(column.getLength());
            case VARCHAR: return DataTypes.VARCHAR(column.getLength());
            case DECIMAL:
                return DataTypes.DECIMAL(
                        column.getPrecision(), column.getScale() == null ? 0 : column.getScale());
            case FLOAT: return DataTypes.FLOAT();
            case DOUBLE: return DataTypes.DOUBLE();
            case DATE: return DataTypes.DATE();
            case TIME: return DataTypes.TIME(normalizeDatetimePrecision(column));
            case DATETIME:
            case TIMESTAMP: return DataTypes.TIMESTAMP(normalizeDatetimePrecision(column));
            case STRING:
            case TEXT:
            case BINARY:
            case VARBINARY:
            case JSON:
            case ARRAY:
            case MAP:
            case STRUCT: return DataTypes.STRING();
            default:
                throw new IllegalArgumentException("当前 Doris 类型映射暂不支持: " + type);
        }
    }

    private int normalizeDatetimePrecision(DdlColumnDefinition column) {
        return column.getDatetimePrecision() == null ? 0 : column.getDatetimePrecision();
    }
}

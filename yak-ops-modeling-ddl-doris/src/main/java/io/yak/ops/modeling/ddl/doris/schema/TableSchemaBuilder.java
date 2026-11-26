package io.yak.ops.modeling.ddl.doris.schema;

import io.yak.ops.modeling.ddl.model.DdlDataType;
import io.yak.ops.modeling.ddl.doris.model.DorisTableModel;
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
public class TableSchemaBuilder {

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
            DdlTableDefinition table, DorisTableModel tableModel, List<String> keyFields) {
        return generateCreateTableDdl(table, tableModel, keyFields, 3);
    }

    public String generateCreateTableDdl(
            DdlTableDefinition table, DorisDataModelEnum tableModel,
            List<String> keyFields, int replicationNum) {
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
        schema.setProperties(Collections.singletonMap(
                "replication_num", String.valueOf(replicationNum)));
        schema.setTableBuckets(null);

        String generatedDdl = DorisSchemaFactory.generateCreateTableDDL(schema);
        generatedDdl = generatedDdl.replaceFirst(
                "(?i)^CREATE TABLE IF NOT EXISTS\\s+", "CREATE TABLE ");
        return applyColumnNullability(generatedDdl, table.getColumns());
    }

    /**
     * Doris Connector 25.1.0 FieldSchema has no nullable property. Keep the SDK responsible
     * for Doris type mapping and table-model syntax, then apply column nullability to its DDL.
     */
    private String applyColumnNullability(String ddl, List<DdlColumnDefinition> columns) {
        int openParenthesis = ddl.indexOf('(');
        if (openParenthesis < 0) {
            throw new IllegalStateException("Doris SDK generated DDL without a column definition");
        }
        int closeParenthesis = findColumnListEnd(ddl, openParenthesis);
        if (closeParenthesis < 0) {
            throw new IllegalStateException("Unable to locate the end of Doris column definitions");
        }

        Map<String, DdlColumnDefinition> columnsByName = new LinkedHashMap<>();
        for (DdlColumnDefinition column : columns) {
            columnsByName.put(column.getName().toLowerCase(java.util.Locale.ROOT), column);
        }

        StringBuilder result = new StringBuilder(ddl.length() + columns.size() * 10);
        result.append(ddl, 0, openParenthesis + 1);
        int segmentStart = openParenthesis + 1;
        int nestedParentheses = 0;
        boolean inString = false;
        boolean inIdentifier = false;

        for (int i = segmentStart; i < closeParenthesis; i++) {
            char current = ddl.charAt(i);
            if (inString) {
                if (current == '\\' && i + 1 < closeParenthesis) {
                    i++;
                } else if (current == '\'') {
                    inString = false;
                }
                continue;
            }
            if (inIdentifier) {
                if (current == '`') {
                    inIdentifier = false;
                }
                continue;
            }
            if (current == '\'') {
                inString = true;
            } else if (current == '`') {
                inIdentifier = true;
            } else if (current == '(') {
                nestedParentheses++;
            } else if (current == ')') {
                nestedParentheses--;
            } else if (current == ',' && nestedParentheses == 0) {
                appendColumnNullability(result, ddl.substring(segmentStart, i), columnsByName);
                result.append(',');
                segmentStart = i + 1;
            }
        }

        appendColumnNullability(
                result, ddl.substring(segmentStart, closeParenthesis), columnsByName);
        if (!columnsByName.isEmpty()) {
            throw new IllegalStateException(
                    "Doris SDK omitted model columns from generated DDL: " + columnsByName.keySet());
        }
        result.append(ddl, closeParenthesis, ddl.length());
        return result.toString();
    }

    private int findColumnListEnd(String ddl, int openParenthesis) {
        int nestedParentheses = 0;
        boolean inString = false;
        boolean inIdentifier = false;
        for (int i = openParenthesis + 1; i < ddl.length(); i++) {
            char current = ddl.charAt(i);
            if (inString) {
                if (current == '\\' && i + 1 < ddl.length()) {
                    i++;
                } else if (current == '\'') {
                    inString = false;
                }
                continue;
            }
            if (inIdentifier) {
                if (current == '`') {
                    inIdentifier = false;
                }
                continue;
            }
            if (current == '\'') {
                inString = true;
            } else if (current == '`') {
                inIdentifier = true;
            } else if (current == '(') {
                nestedParentheses++;
            } else if (current == ')') {
                if (nestedParentheses == 0) {
                    return i;
                }
                nestedParentheses--;
            }
        }
        return -1;
    }

    private void appendColumnNullability(
            StringBuilder target,
            String definition,
            Map<String, DdlColumnDefinition> columnsByName) {
        int identifierStart = 0;
        while (identifierStart < definition.length()
                && Character.isWhitespace(definition.charAt(identifierStart))) {
            identifierStart++;
        }
        if (identifierStart >= definition.length() || definition.charAt(identifierStart) != '`') {
            throw new IllegalStateException("Unable to parse Doris SDK column definition: " + definition);
        }

        int identifierEnd = definition.indexOf('`', identifierStart + 1);
        if (identifierEnd < 0) {
            throw new IllegalStateException("Unable to parse Doris SDK column identifier: " + definition);
        }
        String columnName = definition.substring(identifierStart + 1, identifierEnd);
        DdlColumnDefinition column =
                columnsByName.remove(columnName.toLowerCase(java.util.Locale.ROOT));
        if (column == null) {
            throw new IllegalStateException("Doris SDK generated an unexpected column: " + columnName);
        }

        int typeStart = identifierEnd + 1;
        while (typeStart < definition.length() && Character.isWhitespace(definition.charAt(typeStart))) {
            typeStart++;
        }
        int typeEnd = findTypeEnd(definition, typeStart);
        if (typeEnd <= typeStart) {
            throw new IllegalStateException("Unable to parse Doris SDK column type: " + definition);
        }

        target.append(definition, 0, typeEnd);
        target.append(Boolean.FALSE.equals(column.getNullable()) ? " NOT NULL" : " NULL");
        target.append(definition, typeEnd, definition.length());
    }

    private int findTypeEnd(String definition, int typeStart) {
        int nestedParentheses = 0;
        for (int i = typeStart; i < definition.length(); i++) {
            char current = definition.charAt(i);
            if (current == '(') {
                nestedParentheses++;
            } else if (current == ')') {
                nestedParentheses--;
            } else if (Character.isWhitespace(current) && nestedParentheses == 0) {
                return i;
            }
        }
        return definition.length();
    }

    private DataType toFlinkType(DdlColumnDefinition column) {
        DdlDataType type = column.getDataType();
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

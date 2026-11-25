package io.yak.ops.modeling.ddl.doris;

import io.yak.ops.dao.entity.enums.DimensionDataTypeEnum;
import io.yak.ops.dao.entity.enums.DorisDataModelEnum;
import io.yak.ops.modeling.ddl.model.DdlColumnDefinition;
import io.yak.ops.modeling.ddl.model.DdlTableDefinition;
import io.yak.ops.modeling.ddl.model.DorisColumnMetadata;
import io.yak.ops.modeling.ddl.model.DorisFieldTypeOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/** Generates Doris SQL while delegating connector-specific types and CREATE DDL to its adapter. */
@Component
public class DorisDdlGenerator {

    private static final Pattern TYPE_PARAMETERS = Pattern.compile("\\((\\d+)(?:,(\\d+))?\\)");

    private final DorisConnectorAdapter connectorAdapter;
    private final DorisDdlFormatter formatter;

    public DorisDdlGenerator(DorisConnectorAdapter connectorAdapter, DorisDdlFormatter formatter) {
        this.connectorAdapter = connectorAdapter;
        this.formatter = formatter;
    }

    public List<DorisFieldTypeOption> getFieldTypeOptions() {
        List<DorisFieldTypeOption> options = new ArrayList<>();
        options.add(option("TINYINT", "TINYINT", "NONE", null, null, null));
        options.add(option("SMALLINT", "SMALLINT", "NONE", null, null, null));
        options.add(option("INT", "INT", "NONE", null, null, null));
        options.add(option("BIGINT", "BIGINT", "NONE", null, null, null));
        options.add(option("BOOLEAN", "BOOLEAN", "NONE", null, null, null));
        options.add(option("CHAR", "CHAR", "LENGTH", connectorAdapter.getMaxCharLength(), null, null));
        options.add(option("VARCHAR", "VARCHAR", "LENGTH", connectorAdapter.getMaxVarcharLength(), null, null));
        options.add(option("STRING", "STRING", "NONE", null, null, null));
        options.add(option("DECIMAL", "DECIMAL", "DECIMAL", null, 38, 38));
        options.add(option("FLOAT", "FLOAT", "NONE", null, null, null));
        options.add(option("DOUBLE", "DOUBLE", "NONE", null, null, null));
        options.add(option("DATE", "DATE", "NONE", null, null, null));
        options.add(option("TIME", "TIME（映射为 STRING）", "DATETIME_PRECISION", null, null, null));
        options.add(option("DATETIME", "DATETIME", "DATETIME_PRECISION", null, null, null));
        options.add(option("TIMESTAMP", "TIMESTAMP", "DATETIME_PRECISION", null, null, null));
        options.add(option("BINARY", "BINARY（映射为 STRING）", "NONE", null, null, null));
        options.add(option("VARBINARY", "VARBINARY（映射为 STRING）", "NONE", null, null, null));
        options.add(option("TEXT", "TEXT（映射为 STRING）", "NONE", null, null, null));
        options.add(option("JSON", "JSON（映射为 STRING）", "NONE", null, null, null));
        options.add(option("ARRAY", "ARRAY（映射为 STRING）", "NONE", null, null, null));
        options.add(option("MAP", "MAP（映射为 STRING）", "NONE", null, null, null));
        options.add(option("STRUCT", "STRUCT（映射为 STRING）", "NONE", null, null, null));
        return Collections.unmodifiableList(options);
    }

    public List<String> resolveKeyFields(DdlTableDefinition table) {
        return resolveKeyFields(table, DorisDataModelEnum.UNIQUE);
    }

    public List<String> resolveKeyFields(DdlTableDefinition table, DorisDataModelEnum tableModel) {
        if (tableModel == DorisDataModelEnum.DUPLICATE) {
            return Collections.singletonList(table.getColumns().get(0).getName());
        }
        List<String> primaryFields = new ArrayList<>();
        for (DdlColumnDefinition column : table.getColumns()) {
            if (column.isPrimaryKey()) {
                primaryFields.add(column.getName());
            }
        }
        return primaryFields.isEmpty()
                ? Collections.singletonList(table.getColumns().get(0).getName())
                : primaryFields;
    }

    public String generateCreateTableDdl(DdlTableDefinition table, DorisDataModelEnum tableModel) {
        return generateCreateTableDdl(table, tableModel, 3);
    }

    public String generateCreateTableDdl(
            DdlTableDefinition table, DorisDataModelEnum tableModel, int replicationNum) {
        return formatter.format(connectorAdapter.generateCreateTableDdl(
                table, tableModel, resolveKeyFields(table, tableModel), replicationNum));
    }

    public String generateAddColumnDdl(String database, String tableName, DdlColumnDefinition column) {
        StringBuilder ddl = new StringBuilder("ALTER TABLE ")
                .append(quoteIdentifier(database)).append(".").append(quoteIdentifier(tableName))
                .append(" ADD COLUMN ").append(quoteIdentifier(column.getName()))
                .append(" ").append(connectorAdapter.toDorisType(column))
                .append(Boolean.FALSE.equals(column.getNullable()) ? " NOT NULL" : " NULL");

        String defaultValue = StringUtils.trimToNull(column.getDefaultValue());
        if (defaultValue != null) {
            ddl.append(" DEFAULT ").append(formatDefaultValue(defaultValue, column.getDataType()));
        }
        if (StringUtils.isNotBlank(column.getComment())) {
            ddl.append(" COMMENT '").append(escapeSqlLiteral(column.getComment())).append("'");
        }
        return formatter.format(ddl.toString());
    }

    public String generateDropColumnDdl(String database, String tableName, String columnName) {
        String ddl = "ALTER TABLE " + quoteIdentifier(database) + "." + quoteIdentifier(tableName)
                + " DROP COLUMN " + quoteIdentifier(columnName);
        return formatter.format(ddl);
    }

    public boolean matches(DorisColumnMetadata actual, DdlColumnDefinition expected) {
        String expectedType = connectorAdapter.toDorisType(expected);
        String expectedBase = normalizeTypeName(expectedType);
        if (!expectedBase.equals(normalizeTypeName(actual.getTypeName()))) {
            return false;
        }
        if (expected.getNullable() != null && actual.getNullable() != null
                && !expected.getNullable().equals(actual.getNullable())) {
            return false;
        }

        Matcher parameters = TYPE_PARAMETERS.matcher(expectedType);
        if (!parameters.find()) {
            return true;
        }
        if ("CHAR".equals(expectedBase) || "VARCHAR".equals(expectedBase) || "DECIMAL".equals(expectedBase)) {
            Integer expectedSize = Integer.valueOf(parameters.group(1));
            if (actual.getSize() == null || !expectedSize.equals(actual.getSize())) {
                return false;
            }
        }
        if ("DECIMAL".equals(expectedBase) && parameters.group(2) != null) {
            Integer expectedScale = Integer.valueOf(parameters.group(2));
            return actual.getScale() != null && expectedScale.equals(actual.getScale());
        }
        if ("DATETIME".equals(expectedBase)) {
            Integer expectedPrecision = Integer.valueOf(parameters.group(1));
            return actual.getScale() != null && expectedPrecision.equals(actual.getScale());
        }
        return true;
    }

    private String formatDefaultValue(String value, DimensionDataTypeEnum type) {
        switch (type) {
            case TINYINT:
            case SMALLINT:
            case INT:
            case BIGINT:
            case DECIMAL:
            case FLOAT:
            case DOUBLE:
                if (!value.matches("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?")) {
                    throw new IllegalArgumentException("数值字段默认值格式不合法: " + value);
                }
                return value;
            case BOOLEAN:
                if (!value.matches("(?i)TRUE|FALSE")) {
                    throw new IllegalArgumentException("BOOLEAN 字段默认值格式不合法: " + value);
                }
                return value.toUpperCase(Locale.ROOT);
            case DATE:
            case TIME:
            case DATETIME:
            case TIMESTAMP:
                if (value.matches("(?i)CURRENT_TIMESTAMP(?:\\(\\))?")) {
                    return value;
                }
                return "'" + escapeSqlLiteral(value) + "'";
            default:
                return "'" + escapeSqlLiteral(value) + "'";
        }
    }

    private String normalizeTypeName(String typeName) {
        if (typeName == null) {
            return "";
        }
        int parameterStart = typeName.indexOf('(');
        String baseType = (parameterStart >= 0 ? typeName.substring(0, parameterStart) : typeName)
                .trim().toUpperCase(Locale.ROOT);
        if ("DECIMALV3".equals(baseType)) return "DECIMAL";
        if ("DATETIMEV2".equals(baseType)) return "DATETIME";
        if ("TEXT".equals(baseType)) return "STRING";
        if ("DATEV2".equals(baseType)) return "DATE";
        return baseType;
    }

    private String quoteIdentifier(String value) {
        return "`" + value + "`";
    }

    private String escapeSqlLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private DorisFieldTypeOption option(
            String code, String label, String parameterType,
            Integer maxLength, Integer maxPrecision, Integer maxScale) {
        return new DorisFieldTypeOption(code, label, parameterType, maxLength, maxPrecision, maxScale);
    }
}

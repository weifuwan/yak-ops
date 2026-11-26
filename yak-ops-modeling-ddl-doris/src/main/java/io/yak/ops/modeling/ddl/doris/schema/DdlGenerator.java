package io.yak.ops.modeling.ddl.doris.schema;

import io.yak.ops.modeling.ddl.model.DdlDataType;
import io.yak.ops.modeling.ddl.doris.model.DorisTableModel;
import io.yak.ops.modeling.ddl.model.DdlColumnDefinition;
import io.yak.ops.modeling.ddl.model.DdlTableDefinition;
import io.yak.ops.modeling.ddl.model.DdlColumnMetadata;
import io.yak.ops.modeling.ddl.model.DdlFieldTypeOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/** Generates Doris SQL while using the Flink Doris connector schema builder. */
@Component
public class DdlGenerator {

    private static final Pattern TYPE_PARAMETERS = Pattern.compile("\\((\\d+)(?:,(\\d+))?\\)");

    private final TableSchemaBuilder tableSchemaBuilder;
    private final DdlFormatter formatter;

    public DdlGenerator(TableSchemaBuilder tableSchemaBuilder, DdlFormatter formatter) {
        this.tableSchemaBuilder = tableSchemaBuilder;
        this.formatter = formatter;
    }

    public List<DdlFieldTypeOption> getFieldTypeOptions() {
        List<DdlFieldTypeOption> options = new ArrayList<>();
        options.add(option("TINYINT", "TINYINT", "NONE", null, null, null));
        options.add(option("SMALLINT", "SMALLINT", "NONE", null, null, null));
        options.add(option("INT", "INT", "NONE", null, null, null));
        options.add(option("BIGINT", "BIGINT", "NONE", null, null, null));
        options.add(option("BOOLEAN", "BOOLEAN", "NONE", null, null, null));
        options.add(option("CHAR", "CHAR", "LENGTH", tableSchemaBuilder.getMaxCharLength(), null, null));
        options.add(option("VARCHAR", "VARCHAR", "LENGTH", tableSchemaBuilder.getMaxVarcharLength(), null, null));
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
        return resolveKeyFields(table, DorisTableModel.UNIQUE);
    }

    public List<String> resolveKeyFields(DdlTableDefinition table, DorisTableModel tableModel) {
        if (tableModel == DorisTableModel.DUPLICATE) {
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

    public String generateCreateTableDdl(DdlTableDefinition table, DorisTableModel tableModel) {
        return generateCreateTableDdl(table, tableModel, 3);
    }

    public String generateCreateTableDdl(
            DdlTableDefinition table, DorisTableModel tableModel, int replicationNum) {
        return formatter.format(tableSchemaBuilder.generateCreateTableDdl(
                table, tableModel, resolveKeyFields(table, tableModel), replicationNum));
    }

    public String generateAddColumnDdl(String database, String tableName, DdlColumnDefinition column) {
        StringBuilder ddl = new StringBuilder("ALTER TABLE ")
                .append(quoteIdentifier(database)).append(".").append(quoteIdentifier(tableName))
                .append(" ADD COLUMN ").append(quoteIdentifier(column.getName()))
                .append(" ").append(tableSchemaBuilder.toDorisType(column))
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

    /** Returns the Doris physical type for schema diagnostics and verification. */
    public String toDorisType(DdlColumnDefinition column) {
        return tableSchemaBuilder.toDorisType(column);
    }

    public boolean matches(DdlColumnMetadata actual, DdlColumnDefinition expected) {
        if (!matchesType(actual, expected)) {
            return false;
        }
        return expected.getNullable() == null || actual.getNullable() == null
                || expected.getNullable().equals(actual.getNullable());
    }

    /** Compares the Doris physical type and its parameters without enforcing nullability. */
    public boolean matchesType(DdlColumnMetadata actual, DdlColumnDefinition expected) {
        String expectedType = toDorisType(expected);
        String expectedBase = normalizeTypeName(expectedType);
        if (!expectedBase.equals(normalizeTypeName(actual.getTypeName()))) {
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
            Integer actualPrecision = actual.getScale() == null
                    ? actual.getSize()
                    : actual.getScale();
            if (actualPrecision == null) {
                actualPrecision = 0;
            }
            return expectedPrecision.equals(actualPrecision);
        }
        return true;
    }

    private String formatDefaultValue(String value, DdlDataType type) {
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

    private DdlFieldTypeOption option(
            String code, String label, String parameterType,
            Integer maxLength, Integer maxPrecision, Integer maxScale) {
        return new DdlFieldTypeOption(code, label, parameterType, maxLength, maxPrecision, maxScale);
    }
}

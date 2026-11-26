package io.yak.ops.modeling.ddl.doris.validation;

import io.yak.ops.modeling.ddl.model.DdlDataType;
import io.yak.ops.modeling.ddl.doris.model.DorisTableModel;
import io.yak.ops.modeling.ddl.model.DdlColumnDefinition;
import io.yak.ops.modeling.ddl.model.DdlTableDefinition;
import io.yak.ops.modeling.ddl.model.DdlRequest;
import io.yak.ops.modeling.ddl.doris.model.DorisOptions;
import io.yak.ops.modeling.ddl.doris.schema.TableSchemaBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Validates the physical model before a Doris DDL plan is generated. */
@Component
public class ModelValidator {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]*$");

    private final TableSchemaBuilder tableSchemaBuilder;

    public ModelValidator(TableSchemaBuilder tableSchemaBuilder) {
        this.tableSchemaBuilder = tableSchemaBuilder;
    }

    public void validate(DdlRequest request) {
        DorisOptions options = request == null ? null : request.getOptions(DorisOptions.class);
        if (request == null) {
            throw new IllegalArgumentException("Doris DDL 请求不能为空");
        }
        if (options == null || options.getTableModel() == null) {
            throw new IllegalArgumentException("请选择 Doris 表类型");
        }
        if (request.getPublishMode() == null) {
            throw new IllegalArgumentException("请选择发布模式");
        }
        validateTable(request.getTable(), options.getTableModel());
    }

    private void validateTable(DdlTableDefinition table, DorisTableModel tableModel) {
        if (table == null) {
            throw new IllegalArgumentException("Doris 物理表定义不能为空");
        }
        validateIdentifier(table.getDatabase(), "目标 Doris Database 名称不合法");
        validateIdentifier(table.getTableName(), "模型英文表名不符合 Doris 标识符规则");

        List<DdlColumnDefinition> columns = table.getColumns();
        if (columns == null || columns.isEmpty()) {
            throw new IllegalArgumentException("物理建表至少需要一个字段");
        }

        Set<String> fieldNames = new HashSet<>();
        int primaryKeyCount = 0;
        for (DdlColumnDefinition column : columns) {
            if (column == null) {
                throw new IllegalArgumentException("字段定义不能为空");
            }
            validateIdentifier(column.getName(), "字段英文名称不合法: " + column.getName());
            if (!fieldNames.add(column.getName().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("字段英文名称重复: " + column.getName());
            }
            validateType(column);
            validateDefaultValue(column.getDefaultValue(), column.getName());
            if (column.isPrimaryKey()) {
                primaryKeyCount++;
            }
        }

        if (tableModel == DorisTableModel.UNIQUE && primaryKeyCount == 0) {
            throw new IllegalArgumentException("UNIQUE KEY 主键表至少需要一个主键字段，请先在字段管理中设置主键。");
        }
        if (tableModel == DorisTableModel.UNIQUE) {
            for (DdlColumnDefinition column : columns) {
                if (column.isPrimaryKey() && !Boolean.FALSE.equals(column.getNullable())) {
                    throw new IllegalArgumentException(
                            "UNIQUE KEY 主键字段必须设置为非空，请检查字段: " + column.getName());
                }
            }
        }
    }

    private void validateType(DdlColumnDefinition column) {
        DdlDataType type = column.getDataType();
        if (type == null) {
            throw new IllegalArgumentException("字段类型不能为空: " + column.getName());
        }
        switch (type) {
            case CHAR:
                validateLength(column, 1, tableSchemaBuilder.getMaxCharLength());
                return;
            case VARCHAR:
                validateLength(column, 1, tableSchemaBuilder.getMaxVarcharLength());
                return;
            case DECIMAL:
                Integer precision = column.getPrecision();
                Integer scale = column.getScale() == null ? 0 : column.getScale();
                if (precision == null || precision < 1 || precision > 38) {
                    throw new IllegalArgumentException(
                            "DECIMAL 精度必须在 1 到 38 之间: " + column.getName());
                }
                if (scale < 0 || scale > precision) {
                    throw new IllegalArgumentException(
                            "DECIMAL 小数位数必须在 0 到精度之间: " + column.getName());
                }
                return;
            case TIME:
            case DATETIME:
            case TIMESTAMP:
                Integer datetimePrecision = column.getDatetimePrecision();
                if (datetimePrecision != null && (datetimePrecision < 0 || datetimePrecision > 6)) {
                    throw new IllegalArgumentException(
                            "Doris 时间精度必须在 0 到 6 之间: " + column.getName());
                }
                return;
            case TINYINT:
            case SMALLINT:
            case INT:
            case BIGINT:
            case BOOLEAN:
            case STRING:
            case TEXT:
            case FLOAT:
            case DATE:
            case BINARY:
            case VARBINARY:
            case JSON:
            case ARRAY:
            case DOUBLE:
            case MAP:
            case STRUCT:
                return;
            default:
                throw new IllegalArgumentException("当前 Doris 类型映射暂不支持: " + type);
        }
    }

    private void validateLength(DdlColumnDefinition column, int min, int max) {
        Integer length = column.getLength();
        if (length == null || length < min || length > max) {
            throw new IllegalArgumentException(
                    column.getDataType() + " 长度必须在 " + min + " 到 " + max + " 之间: "
                            + column.getName());
        }
    }

    private void validateIdentifier(String value, String message) {
        if (value == null || !IDENTIFIER_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(message);
        }
    }

    private void validateDefaultValue(String value, String fieldName) {
        if (value == null) {
            return;
        }
        if (value.length() > 255 || value.indexOf('\'') >= 0 || value.indexOf('\\') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(
                    "默认值包含当前 Doris DDL Builder 不支持的字符: " + fieldName);
        }
    }
}

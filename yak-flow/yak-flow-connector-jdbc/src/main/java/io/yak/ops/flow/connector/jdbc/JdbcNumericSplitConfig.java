package io.yak.ops.flow.connector.jdbc;

import io.yak.ops.flow.api.row.YakColumn;
import io.yak.ops.flow.api.row.YakDataType;
import io.yak.ops.flow.api.row.YakTableSchema;
import io.yak.ops.flow.api.row.YakTypeKind;
import java.util.Objects;
import java.util.Optional;

/**
 * 定义 bounded JDBC Source 的显式数值范围分片参数；当前只支持单整数主键。
 *
 * @param column 分片字段名称，必须与 Source Schema 中的单整数主键名称一致
 * @param lowerBound 分片字段最小包含值
 * @param upperBound 分片字段最大包含值
 * @param splitCount 期望生成的最大分片数量；值域小于该数量时不会生成空分片
 * @author weifuwan
 * @since 2026-09-28
 */
public record JdbcNumericSplitConfig(String column, long lowerBound, long upperBound, int splitCount) {

    public JdbcNumericSplitConfig {
        Objects.requireNonNull(column, "column must not be null");
        if (column.isBlank()) {
            throw new IllegalArgumentException("column must not be blank");
        }
        if (lowerBound > upperBound) {
            throw new IllegalArgumentException("lowerBound must not be greater than upperBound");
        }
        if (splitCount <= 0) {
            throw new IllegalArgumentException("splitCount must be greater than 0");
        }
    }

    public static Optional<String> eligibleColumn(YakTableSchema schema) {
        Objects.requireNonNull(schema, "schema must not be null");
        if (schema.primaryKeys().size() != 1) return Optional.empty();
        String primaryKey = schema.primaryKeys().getFirst();
        return schema.columns().stream()
                .filter(column -> column.name().equals(primaryKey))
                .filter(column -> isIntegerType(column.dataType()))
                .map(YakColumn::name)
                .findFirst();
    }

    private static boolean isIntegerType(YakDataType dataType) {
        YakTypeKind kind = dataType.kind();
        return kind == YakTypeKind.TINYINT
                || kind == YakTypeKind.SMALLINT
                || kind == YakTypeKind.INTEGER
                || kind == YakTypeKind.BIGINT;
    }
}

package io.yak.ops.business.datasync.schema.target;

import io.yak.ops.flow.api.row.YakDataType;
import java.util.Objects;

/**
 * 一列 Logical Schema 到目标 JDBC 原生列类型的规划结果。
 *
 * @param name 字段名称
 * @param logicalType YakFlow 逻辑类型
 * @param nativeType 目标数据库原生类型；不支持时为空
 * @param nullable Logical Schema 是否允许 NULL
 * @param primaryKey 是否属于目标主键
 * @param primaryKeyPosition 复合主键顺序；非主键时为空
 * @param comment 产品字段备注
 * @param warning 安全放宽等非阻塞诊断
 * @param unsupportedReason 阻塞建表的原因；支持时为空
 * @author weifuwan
 * @since 2026-10-04
 */
public record TargetColumnPlan(
        String name,
        YakDataType logicalType,
        String nativeType,
        boolean nullable,
        boolean primaryKey,
        Integer primaryKeyPosition,
        String comment,
        String warning,
        String unsupportedReason) {

    public TargetColumnPlan {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(logicalType, "logicalType must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        nativeType = normalize(nativeType);
        comment = normalize(comment);
        warning = normalize(warning);
        unsupportedReason = normalize(unsupportedReason);
        if (primaryKey && (primaryKeyPosition == null || primaryKeyPosition < 1)) {
            throw new IllegalArgumentException("primaryKeyPosition is required for primary key columns");
        }
        if (!primaryKey) {
            primaryKeyPosition = null;
        }
    }

    public boolean supported() {
        return unsupportedReason == null;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

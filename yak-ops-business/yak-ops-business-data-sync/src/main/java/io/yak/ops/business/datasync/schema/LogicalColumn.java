package io.yak.ops.business.datasync.schema;

import io.yak.ops.core.types.LogicalType;
import io.yak.ops.core.types.TypeKind;
import java.util.Objects;

/**
 * Data Sync 产品层的逻辑字段定义。
 *
 * <p>字段类型复用 YakFlow 逻辑类型体系，不保存 MySQL / PostgreSQL / Oracle 原生类型名称。
 *
 * @param name 字段名称，保留来源标识符原始大小写
 * @param dataType YakFlow 逻辑类型
 * @param nullable 是否允许空值
 * @param length STRING / BINARY 的容量；不适用或未知时为 null
 * @param comment 字段业务备注；未提供时可为 null
 * @author weifuwan
 * @since 2026-10-04
 */
public record LogicalColumn(String name, LogicalType dataType, boolean nullable, Integer length, String comment) {

    public LogicalColumn {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(dataType, "dataType must not be null");
        Objects.requireNonNull(dataType.kind(), "dataType.kind must not be null");

        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (length != null && length < 0) {
            throw new IllegalArgumentException("length must not be negative");
        }
        if (length != null && !supportsLength(dataType.kind())) {
            throw new IllegalArgumentException("length is only supported by STRING or BINARY");
        }
    }

    private static boolean supportsLength(TypeKind kind) {
        return kind == TypeKind.STRING || kind == TypeKind.BINARY;
    }
}

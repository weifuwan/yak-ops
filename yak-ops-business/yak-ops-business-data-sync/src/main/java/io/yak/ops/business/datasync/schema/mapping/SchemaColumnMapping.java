package io.yak.ops.business.datasync.schema.mapping;

import java.util.Objects;

/**
 * Data Sync Schema Resolution 使用的单个来源字段到目标字段映射。
 *
 * @param source 来源字段名称
 * @param target 目标字段名称
 * @author weifuwan
 * @since 2026-10-05
 */
public record SchemaColumnMapping(String source, String target) {

    public SchemaColumnMapping {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(target, "target must not be null");
        if (source.isBlank()) throw new IllegalArgumentException("source must not be blank");
        if (target.isBlank()) throw new IllegalArgumentException("target must not be blank");
    }
}

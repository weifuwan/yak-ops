package io.yak.ops.business.datasync.schema.target;

import io.yak.ops.flow.api.row.YakTableSchema;
import java.util.List;

/**
 * 已存在目标表与 LogicalTable 的兼容性检查结果。
 *
 * @param compatible 是否满足当前无 Transform 直接写入
 * @param targetWriteSchema 按 Source 字段顺序排列的目标运行 Schema；不兼容时为空
 * @param issues 阻塞运行的问题
 * @author weifuwan
 * @since 2026-10-04
 */
public record TargetSchemaCompatibilityResult(
        boolean compatible, YakTableSchema targetWriteSchema, List<String> issues) {

    public TargetSchemaCompatibilityResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
        if (compatible && targetWriteSchema == null) {
            throw new IllegalArgumentException("compatible result requires targetWriteSchema");
        }
        if (!compatible && targetWriteSchema != null) {
            throw new IllegalArgumentException("incompatible result must not contain targetWriteSchema");
        }
    }
}

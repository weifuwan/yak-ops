package io.yak.ops.business.datasync.execution.planning.target;

import io.yak.ops.flow.api.row.YakTableSchema;

/**
 * Runtime Target Preflight 完成后的执行输入。
 *
 * @param sourceSchema 来源运行 Schema
 * @param targetWriteSchema 按来源字段顺序排列的目标写入 Schema
 * @param targetCreated 本次 Preflight 是否创建了目标表
 */
public record TargetTablePreflightResult(
        YakTableSchema sourceSchema, YakTableSchema targetWriteSchema, boolean targetCreated) {}

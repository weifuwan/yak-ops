package io.yak.ops.business.datasync.execution.trace;

import java.util.List;

/**
 * Runtime Trace 文件 Cursor 分页结果。
 *
 * @param records 当前页记录
 * @param nextCursor 下一页 Cursor
 * @param hasMore 是否存在后续匹配记录
 * @author weifuwan
 * @since 2026-10-03
 */
public record ExecutionTracePage(List<ExecutionTraceRecord> records, String nextCursor, boolean hasMore) {}

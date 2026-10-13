package io.yak.ops.common.bean.vo.task;

/** Bounded incremental contents of an authorized Task Instance log. */
public record TaskLogPageVO(String content, String nextCursor, boolean hasMore) {}

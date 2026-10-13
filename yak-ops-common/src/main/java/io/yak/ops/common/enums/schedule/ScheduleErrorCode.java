package io.yak.ops.common.enums.schedule;

import io.yak.ops.common.result.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Localized schedule configuration errors; never exposes engine diagnostics. */
@Getter
@RequiredArgsConstructor
public enum ScheduleErrorCode implements ErrorCode {
    INVALID_TIME_RULE(18001, "调度时间规则无效"),
    REVISION_CONFLICT(18002, "调度已变更，请刷新后重试"),
    MUST_BE_DISABLED(18003, "请先停止调度"),
    TARGET_ALREADY_SCHEDULED(18004, "该任务已配置调度");

    private final Integer code;
    private final String message;
}

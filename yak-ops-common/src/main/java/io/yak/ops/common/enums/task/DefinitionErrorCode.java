package io.yak.ops.common.enums.task;

import io.yak.ops.common.result.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Localized task editing and publication errors without internal diagnostics. */
@Getter
@RequiredArgsConstructor
public enum DefinitionErrorCode implements ErrorCode {
    REVISION_CONFLICT(19001, "任务已变更，请刷新后重新确认修改"),
    PUBLICATION_REQUEST_CONFLICT(19002, "发布请求标识已用于其他内容，请重新发起发布"),
    PUBLICATION_INVALID(19003, "发布校验失败，请检查任务配置和数据源"),
    SCHEDULE_INCOMPATIBLE(19004, "新版本不支持周期调度，请先停止调度再发布");

    private final Integer code;
    private final String message;
}

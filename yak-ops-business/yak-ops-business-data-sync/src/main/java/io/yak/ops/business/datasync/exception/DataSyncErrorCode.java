package io.yak.ops.business.datasync.exception;

import io.yak.ops.common.result.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Data Sync 业务错误码。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Getter
@RequiredArgsConstructor
public enum DataSyncErrorCode implements ErrorCode {
    TASK_NOT_FOUND(42001, "同步任务不存在"),
    INSTANCE_NOT_FOUND(42002, "同步实例不存在"),
    DUPLICATE_TASK_NAME(42003, "同步任务名称已存在"),
    INVALID_TASK(42004, "同步任务参数不合法"),
    CREATE_TASK_FAILED(42005, "创建同步任务失败"),
    UPDATE_TASK_FAILED(42006, "更新同步任务失败"),
    DELETE_TASK_FAILED(42007, "删除同步任务失败"),
    INVALID_QUERY(42008, "同步查询参数不合法"),
    FIELD_MAPPING_INCOMPATIBLE(42009, "来源与目标表字段不兼容"),
    ACTIVE_INSTANCE_EXISTS(42010, "同步任务已有运行中的实例"),
    INSTANCE_NOT_CANCELABLE(42011, "同步实例当前不可停止"),
    EXECUTION_FAILED(42012, "离线同步执行失败"),
    EXECUTION_LOST(42013, "离线同步执行上下文丢失"),
    INVALID_TASK_STATUS(42014, "同步任务当前状态不允许该操作"),
    SCHEDULE_NOT_FOUND(42015, "同步任务调度不存在"),
    INVALID_SCHEDULE(42016, "同步任务调度参数不合法"),
    SCHEDULE_PERSIST_FAILED(42017, "同步任务调度保存失败"),
    SCHEDULE_RUNTIME_FAILED(42018, "同步任务调度运行时同步失败"),
    ATTEMPT_PERSIST_FAILED(42019, "同步执行Attempt状态保存失败"),
    TARGET_TABLE_NOT_FOUND(42020, "目标表不存在"),
    TARGET_SCHEMA_INCOMPATIBLE(42021, "目标表结构不兼容"),
    TARGET_TABLE_CREATE_FAILED(42022, "自动创建目标表失败");

    private final Integer code;
    private final String message;
}

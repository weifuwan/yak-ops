package io.yak.ops.plugin.task.grpc;

public enum GrpcCheckCondition {
    /**
     * 0 status_code_default:OK
     * 1 status_code_custom
     */
    STATUS_CODE_DEFAULT,
    STATUS_CODE_CUSTOM,
    STATUS_CODE_DESCRIPTION_CONTAIN,
}

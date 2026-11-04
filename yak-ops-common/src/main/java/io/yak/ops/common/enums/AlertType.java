package io.yak.ops.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;

import lombok.Getter;

/**
 * describe the reason why alert generates
 */
@Getter
public enum AlertType {

    /**
     * 0 workflow instance failure, 1 workflow instance success, 2 workflow instance blocked, 3 workflow instance timeout, 4 fault tolerance warning,
     * 5 task failure, 6 task success, 7 task timeout
      */
    WORKFLOW_INSTANCE_FAILURE(0, "workflow instance failure"),
    WORKFLOW_INSTANCE_SUCCESS(1, "workflow instance success"),
    WORKFLOW_INSTANCE_BLOCKED(2, "workflow instance blocked"),
    WORKFLOW_INSTANCE_TIMEOUT(3, "workflow instance timeout"),
    FAULT_TOLERANCE_WARNING(4, "fault tolerance warning"),
    TASK_FAILURE(5, "task failure"),
    TASK_SUCCESS(6, "task success"),
    TASK_TIMEOUT(7, "task timeout"),
    ;

    AlertType(int code, String descp) {
        this.code = code;
        this.descp = descp;
    }

    @EnumValue
    private final int code;
    private final String descp;

}

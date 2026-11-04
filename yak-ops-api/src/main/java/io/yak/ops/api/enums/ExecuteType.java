package io.yak.ops.api.enums;

public enum ExecuteType {

    /**
     * operation type
     * 0 none
     * 1 repeat running
     * 2 resume pause
     * 3 resume failure
     * 4 stop
     * 5 pause
     * 6 execute task
     */
    NONE(0, "NONE"),

    // ******************************* Workflow ***************************
    REPEAT_RUNNING(1, "REPEAT_RUNNING"),
    RECOVER_SUSPENDED_PROCESS(2, "RECOVER_SUSPENDED_PROCESS"),
    START_FAILURE_TASK_PROCESS(3, "START_FAILURE_TASK_PROCESS"),
    STOP(4, "STOP"),
    PAUSE(5, "PAUSE"),
    // ******************************* Workflow ***************************

    // ******************************* Task *******************************
    EXECUTE_TASK(6, "EXECUTE_TASK"),
    // ******************************* Task *******************************
    ;

    private final int code;
    private final String desc;

    ExecuteType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static ExecuteType getEnum(int value) {
        for (ExecuteType e : ExecuteType.values()) {
            if (e.getCode() == value) {
                return e;
            }
        }
        return NONE;
    }
}

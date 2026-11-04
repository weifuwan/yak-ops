package io.yak.ops.plugin.task.api.log;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TaskInstanceLogHeader {

    public static final String DOLPHIN_EMOJI = "\uD83D\uDC2C";

    private static final String INITIALIZE_TASK_CONTEXT_HEADER = DOLPHIN_EMOJI + " Initialize Task Context";
    private static final String LOAD_TASK_INSTANCE_PLUGIN_HEADER = DOLPHIN_EMOJI + " Load Task Instance Plugin";
    private static final String EXECUTE_TASK_HEADER = DOLPHIN_EMOJI + " Execute Task Instance";
    private static final String FINALIZE_TASK_HEADER = DOLPHIN_EMOJI + " Finalize Task Instance";

    public static void printInitializeTaskContextHeader() {
        log.info(INITIALIZE_TASK_CONTEXT_HEADER);
    }

    public static void printLoadTaskInstancePluginHeader() {
        log.info(LOAD_TASK_INSTANCE_PLUGIN_HEADER);
    }

    public static void printExecuteTaskHeader() {
        log.info(EXECUTE_TASK_HEADER);
    }

    public static void printFinalizeTaskHeader() {
        log.info(FINALIZE_TASK_HEADER);
    }
}

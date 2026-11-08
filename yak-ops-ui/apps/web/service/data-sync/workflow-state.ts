export type WorkflowExecutionStatus =
  | "SUBMITTED_SUCCESS"
  | "RUNNING_EXECUTION"
  | "READY_PAUSE"
  | "PAUSE"
  | "READY_STOP"
  | "STOP"
  | "FAILURE"
  | "SUCCESS"
  | "SERIAL_WAIT"
  | "FAILOVER";

export const isWorkflowActive = (state: WorkflowExecutionStatus): boolean =>
  state !== "STOP" && state !== "FAILURE" && state !== "SUCCESS";

export const canStopWorkflow = (state: WorkflowExecutionStatus): boolean =>
  state === "SERIAL_WAIT" ||
  state === "RUNNING_EXECUTION" ||
  state === "READY_PAUSE" ||
  state === "READY_STOP";

export const workflowStatusText = (state: WorkflowExecutionStatus): string =>
  ({
    SUBMITTED_SUCCESS: "启动中",
    SERIAL_WAIT: "排队中",
    RUNNING_EXECUTION: "启动中",
    READY_PAUSE: "暂停中",
    PAUSE: "已暂停",
    READY_STOP: "停止中",
    STOP: "已停止",
    FAILURE: "运行失败",
    SUCCESS: "已完成",
    FAILOVER: "恢复中",
  })[state];

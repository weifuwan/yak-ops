import type {
  ExecutionType,
  GlobalParameterType,
  PanelKey,
} from "./types";
import type {
  WorkflowFailureStrategy,
  WorkflowMissedFirePolicy,
  WorkflowPriority,
  WorkflowWarningType,
} from "@/service/workflow/management";

export const CONTROL_CLASS =
  "h-8 w-full min-w-0 rounded-md border border-[#dfe3e8] bg-white px-2 text-xs text-[#252a32] outline-none transition-colors placeholder:text-[#a0a7b2] focus:border-[#8ba9ff] disabled:cursor-not-allowed disabled:bg-[#f7f8fa] disabled:text-[#9ca3af]";
export const TEXTAREA_CLASS =
  "w-full min-w-0 resize-y rounded-md border border-[#dfe3e8] bg-white px-2 py-2 text-xs text-[#252a32] outline-none transition-colors placeholder:text-[#a0a7b2] focus:border-[#8ba9ff] disabled:cursor-not-allowed disabled:bg-[#f7f8fa] disabled:text-[#9ca3af]";
export const PROPERTY_CONTROL_CLASS = CONTROL_CLASS.replace("h-8", "h-7");
export const PROPERTY_TEXTAREA_CLASS = TEXTAREA_CLASS.replace("py-2", "py-1.5") + " min-h-[48px]";
export const SCHEDULE_CONTROL_CLASS = CONTROL_CLASS.replace("h-8", "h-7");
export const MUTED_CLASS = "text-[11px] leading-5 text-[#8b93a1]";

export const GLOBAL_PARAMETER_TYPES: GlobalParameterType[] = [
  "VARCHAR",
  "INTEGER",
  "LONG",
  "FLOAT",
  "DOUBLE",
  "DATE",
  "TIME",
  "TIMESTAMP",
  "BOOLEAN",
  "LIST",
  "FILE",
];

export const EXECUTION_TYPES: Array<{ value: ExecutionType; label: string }> = [
  { value: "PARALLEL", label: "并行执行" },
  { value: "SERIAL_WAIT", label: "串行等待" },
  { value: "SERIAL_DISCARD", label: "串行丢弃" },
  { value: "SERIAL_PRIORITY", label: "串行优先" },
];

export const WORKFLOW_STATES: Record<string, string> = {
  SUBMITTED_SUCCESS: "启动中",
  RUNNING_EXECUTION: "运行中",
  READY_PAUSE: "暂停中",
  PAUSE: "已暂停",
  READY_STOP: "停止中",
  STOP: "已停止",
  FAILURE: "运行失败",
  SUCCESS: "已完成",
  SERIAL_WAIT: "排队中",
  FAILOVER: "恢复中",
};

export const TASK_STATES: Record<string, string> = {
  SUBMITTED_SUCCESS: "已提交",
  RUNNING_EXECUTION: "运行中",
  DELAY_EXECUTION: "等待执行",
  READY_PAUSE: "暂停中",
  PAUSE: "已暂停",
  READY_STOP: "停止中",
  STOP: "已停止",
  FAILURE: "运行失败",
  SUCCESS: "已完成",
  NEED_FAULT_TOLERANCE: "容错中",
  KILL: "已终止",
  WAITING_THREAD: "等待线程",
};

export const WARNING_TYPES: Array<{ value: WorkflowWarningType; label: string }> = [
  { value: "NONE", label: "不通知" },
  { value: "FAILURE", label: "仅失败时通知" },
  { value: "SUCCESS", label: "仅成功时通知" },
  { value: "ALL", label: "成功或失败都通知" },
];

export const FAILURE_STRATEGIES: Array<{ value: WorkflowFailureStrategy; label: string }> = [
  { value: "END", label: "结束工作流" },
  { value: "CONTINUE", label: "继续执行" },
];

export const PRIORITIES: Array<{ value: WorkflowPriority; label: string }> = [
  { value: "HIGHEST", label: "最高" },
  { value: "HIGH", label: "高" },
  { value: "MEDIUM", label: "中" },
  { value: "LOW", label: "低" },
  { value: "LOWEST", label: "最低" },
];

export const MISSED_FIRE_POLICIES: Array<{ value: WorkflowMissedFirePolicy; label: string }> = [
  { value: "SKIP_MISSED", label: "跳过错过的调度" },
  { value: "FIRE_ONCE_NOW", label: "立即补触发一次" },
  { value: "FIRE_ALL_MISSED", label: "补触发所有错过的调度" },
];

export const PANEL_ITEMS: Array<{
  key: PanelKey;
  label: string;
}> = [
  { key: "properties", label: "属性" },
  { key: "schedule", label: "调度配置" },
  { key: "history", label: "运行历史" },
  { key: "versions", label: "版本" },
  { key: "ops", label: "运维" },
];


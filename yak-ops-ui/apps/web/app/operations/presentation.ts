import type { DataSyncInstanceStatus, DataSyncScope } from "@/service/data-sync";

export const WORKFLOW_STATE_ITEMS = {
  ALL: "全部状态",
  SUBMITTED_SUCCESS: "已提交",
  RUNNING_EXECUTION: "运行中",
  SERIAL_WAIT: "等待中",
  SUCCESS: "成功",
  FAILURE: "失败",
  STOP: "已停止",
  PAUSE: "已暂停",
} as const;

export const INSTANCE_STATUS_ITEMS: Record<string, string> = {
  ALL: "全部状态",
  SUBMITTED_SUCCESS: "已提交",
  DISPATCH: "调度中",
  RUNNING_EXECUTION: "运行中",
  DELAY_EXECUTION: "延迟执行",
  NEED_FAULT_TOLERANCE: "故障恢复",
  SUCCESS: "成功",
  FORCED_SUCCESS: "强制成功",
  FAILURE: "失败",
  PAUSE: "已暂停",
  KILL: "已停止",
};

export const ACTIVE_INSTANCE_STATES = new Set<DataSyncInstanceStatus>([
  "SUBMITTED_SUCCESS",
  "DISPATCH",
  "RUNNING_EXECUTION",
  "DELAY_EXECUTION",
  "NEED_FAULT_TOLERANCE",
]);

export const ACTIVE_WORKFLOW_STATES = new Set([
  "SUBMITTED_SUCCESS",
  "RUNNING_EXECUTION",
  "SERIAL_WAIT",
  "READY_PAUSE",
  "READY_STOP",
  "PAUSE",
]);

export function workflowStateText(value?: string | null) {
  return WORKFLOW_STATE_ITEMS[value as keyof typeof WORKFLOW_STATE_ITEMS] ?? value ?? "-";
}

export function instanceStatusText(value?: string | null) {
  return INSTANCE_STATUS_ITEMS[value ?? ""] ?? value ?? "-";
}

export function scopeText(scope?: DataSyncScope) {
  if (scope === "DATABASE") return "整库同步";
  if (scope === "MULTI_TABLE") return "多表同步";
  if (scope === "SINGLE_TABLE") return "单表同步";
  return "-";
}

export function triggerText(value?: string | null) {
  if (!value) return "-";
  if (value.includes("SCHEDULER") || value.includes("SCHEDULE")) return "周期调度";
  if (value.includes("MANUAL") || value.includes("START")) return "手动运行";
  return value;
}

export function durationText(start?: string | null, end?: string | null) {
  if (!start) return "-";
  const startAt = Date.parse(start);
  const endAt = end ? Date.parse(end) : Date.now();
  if (!Number.isFinite(startAt) || !Number.isFinite(endAt)) return "-";
  const seconds = Math.max(0, Math.floor((endAt - startAt) / 1000));
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ${seconds % 60}s`;
  const hours = Math.floor(minutes / 60);
  return `${hours}h ${minutes % 60}m`;
}

import type { DataSyncExecutionDay, DataSyncExecutionStats } from "@/service/data-sync/overview";

export type ExecutionTab = "ALL" | "OFFLINE" | "REALTIME";
export type ExecutionPeriod = "yesterday" | "7d" | "30d";
export interface ExecutionRange {
  startDate: string;
  endDate: string;
}

export const EXECUTION_METRICS = [
  { key: "executionCount", label: "执行次数" },
  { key: "succeededCount", label: "成功次数" },
  { key: "failedCount", label: "失败次数" },
  { key: "stoppedCount", label: "停止次数" },
  { key: "runningCount", label: "运行中" },
  { key: "queuedCount", label: "排队中" },
  { key: "pausedCount", label: "已暂停" },
  { key: "successRate", label: "成功率" },
] as const;

export type ExecutionMetricKey = (typeof EXECUTION_METRICS)[number]["key"];
export type ExecutionCounts = Omit<DataSyncExecutionDay, "date" | "syncType">;
const COUNT_KEYS = [
  "executionCount",
  "succeededCount",
  "failedCount",
  "stoppedCount",
  "runningCount",
  "queuedCount",
  "pausedCount",
] as const;

function dateString(date: Date) {
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, "0"),
    String(date.getDate()).padStart(2, "0"),
  ].join("-");
}

export function rangeForPeriod(period: ExecutionPeriod, today = new Date()): ExecutionRange {
  const end = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 1);
  const length = period === "yesterday" ? 1 : period === "7d" ? 7 : 30;
  const start = new Date(end.getFullYear(), end.getMonth(), end.getDate() - length + 1);
  return { startDate: dateString(start), endDate: dateString(end) };
}

export function activePeriod(range: ExecutionRange, today = new Date()): ExecutionPeriod | undefined {
  for (const period of ["yesterday", "7d", "30d"] as const) {
    const current = rangeForPeriod(period, today);
    if (current.startDate === range.startDate && current.endDate === range.endDate) return period;
  }
  return undefined;
}

export function validRange(range: ExecutionRange, today = new Date()): boolean {
  const start = new Date(range.startDate + "T12:00:00");
  const end = new Date(range.endDate + "T12:00:00");
  const lastDay = rangeForPeriod("yesterday", today).endDate;
  const days = (Date.parse(range.endDate + "T00:00:00Z") -
    Date.parse(range.startDate + "T00:00:00Z")) / 86_400_000;
  return !Number.isNaN(days) &&
    dateString(start) === range.startDate &&
    dateString(end) === range.endDate &&
    days >= 0 && days < 90 && range.endDate <= lastDay;
}

export function emptyCounts(): ExecutionCounts {
  return {
    executionCount: 0,
    succeededCount: 0,
    failedCount: 0,
    stoppedCount: 0,
    runningCount: 0,
    queuedCount: 0,
    pausedCount: 0,
  };
}

export function sumExecutionCounts(rows: DataSyncExecutionDay[], tab: ExecutionTab): ExecutionCounts {
  const result = emptyCounts();
  for (const row of rows) {
    if (tab !== "ALL" && row.syncType !== tab) continue;
    for (const key of COUNT_KEYS) {
      result[key] += row[key];
    }
  }
  return result;
}

export function executionMetricValue(counts: ExecutionCounts, key: ExecutionMetricKey) {
  return key === "successRate"
    ? counts.executionCount > 0
      ? Math.round((counts.succeededCount / counts.executionCount) * 10_000) / 100
      : 0
    : counts[key];
}

export function executionTrend(stats: DataSyncExecutionStats, tab: ExecutionTab) {
  const byDate = new Map<string, DataSyncExecutionDay[]>();
  for (const row of stats.days) {
    const existing = byDate.get(row.date) ?? [];
    existing.push(row);
    byDate.set(row.date, existing);
  }
  const points: Array<{ date: string; counts: ExecutionCounts }> = [];
  const day = new Date(stats.startDate + "T12:00:00");
  while (dateString(day) <= stats.endDate) {
    const date = dateString(day);
    points.push({ date, counts: sumExecutionCounts(byDate.get(date) ?? [], tab) });
    day.setDate(day.getDate() + 1);
  }
  return points;
}

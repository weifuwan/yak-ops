import type { StatusCountRecord } from "@/service/operations/analysis";

export function countStatus(records: StatusCountRecord[], state: string): number {
  return records.find((item) => item.state === state)?.count ?? 0;
}

export function workflowSuccessRate(records: StatusCountRecord[]): number | undefined {
  const success = countStatus(records, "SUCCESS");
  const failure = countStatus(records, "FAILURE");
  const completed = success + failure;

  if (completed === 0) return undefined;
  return (success / completed) * 100;
}

export function formatPercent(value?: number): string {
  if (value === undefined || !Number.isFinite(value)) return "—";
  return `${value.toFixed(1)}%`;
}

export function percentOf(count: number, total: number): number {
  if (total <= 0) return 0;
  return (count / total) * 100;
}

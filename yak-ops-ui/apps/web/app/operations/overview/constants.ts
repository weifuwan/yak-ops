const DEFAULT_STATUS_COLOR = "#94a3b8";

export const OVERVIEW_STATUS_COLORS: Record<string, string> = {
  SUCCESS: "#22c55e",
  FORCED_SUCCESS: "#16a34a",
  FAILURE: "#ef4444",
  RUNNING_EXECUTION: "#3b82f6",
  DISPATCH: "#6366f1",
  SUBMITTED_SUCCESS: "#64748b",
  SERIAL_WAIT: "#f59e0b",
  DELAY_EXECUTION: "#f59e0b",
  NEED_FAULT_TOLERANCE: "#f97316",
  PAUSE: "#eab308",
  READY_PAUSE: "#eab308",
  STOP: "#94a3b8",
  READY_STOP: "#94a3b8",
  KILL: "#94a3b8",
};

export function overviewStatusColor(state: string): string {
  return OVERVIEW_STATUS_COLORS[state] ?? DEFAULT_STATUS_COLOR;
}

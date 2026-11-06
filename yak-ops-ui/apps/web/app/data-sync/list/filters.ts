import type { DataSyncTaskStatus } from "@/service/data-sync";

export interface TaskListFilters {
  pageNo: number;
  keyword: string;
  status: DataSyncTaskStatus | "ALL";
}

export function parseTaskListFilters(search: URLSearchParams): TaskListFilters {
  const rawPage = Number(search.get("page"));
  const status = search.get("status");
  return {
    pageNo: Number.isSafeInteger(rawPage) && rawPage > 0 ? rawPage : 1,
    keyword: search.get("keyword")?.trim() || "",
    status: status === "ONLINE" || status === "OFFLINE" ? status : "ALL",
  };
}

export function updateTaskListFilters(
  current: URLSearchParams,
  patch: Partial<TaskListFilters>,
): URLSearchParams {
  const next = new URLSearchParams(current);
  const state = { ...parseTaskListFilters(current), ...patch };
  if (state.pageNo > 1) next.set("page", String(state.pageNo));
  else next.delete("page");
  if (state.keyword.trim()) next.set("keyword", state.keyword.trim());
  else next.delete("keyword");
  next.delete("productType");
  if (state.status !== "ALL") next.set("status", state.status);
  else next.delete("status");
  return next;
}

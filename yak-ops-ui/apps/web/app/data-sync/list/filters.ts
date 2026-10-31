import type { DataSyncProductType, DataSyncTaskStatus } from "@/service/data-sync";

export interface TaskListFilters {
  pageNo: number;
  keyword: string;
  productType: DataSyncProductType | "ALL";
  status: DataSyncTaskStatus | "ALL";
}

const PRODUCTS = new Set(["SINGLE_TABLE_BATCH", "DATABASE_REPLICATION"]);

export function parseTaskListFilters(search: URLSearchParams): TaskListFilters {
  const rawPage = Number(search.get("page"));
  const value = search.get("productType");
  const status = search.get("status");
  return {
    pageNo: Number.isSafeInteger(rawPage) && rawPage > 0 ? rawPage : 1,
    keyword: search.get("keyword")?.trim() || "",
    productType: value && PRODUCTS.has(value) ? (value as DataSyncProductType) : "ALL",
    status: status === "PUBLISHED" || status === "UNPUBLISHED" ? status : "ALL",
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
  if (state.productType !== "ALL") next.set("productType", state.productType);
  else next.delete("productType");
  if (state.status !== "ALL") next.set("status", state.status);
  else next.delete("status");
  return next;
}

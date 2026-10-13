import HttpUtils from "@/service/http/HttpUtils";

import type { DataSyncType } from "./types";

export type DataSyncOperationsRange = "TODAY" | "LAST_7_DAYS" | "LAST_30_DAYS";

export interface DataSyncOperationsSummary {
  executionCount: number;
  succeededCount: number;
  failedCount: number;
  canceledCount: number;
  runningCount: number;
}

export interface DataSyncOperationsTrendPoint {
  bucket: string;
  executionCount: number;
  succeededCount: number;
  failedCount: number;
  canceledCount: number;
}

export interface DataSyncOperationsFailure {
  instanceId: string;
  definitionId: string;
  taskName: string | null;
  failureCode: string | null;
  createTime: string;
}

export interface DataSyncOperationsResult {
  summary: DataSyncOperationsSummary;
  trend: DataSyncOperationsTrendPoint[];
  recentFailures: DataSyncOperationsFailure[];
}

export const getDataSyncOperations = (
  syncType: DataSyncType,
  range: DataSyncOperationsRange,
): Promise<DataSyncOperationsResult> =>
  HttpUtils.getData<DataSyncOperationsResult>(
    `/api/v1/data-sync/operations/dashboard?syncType=${syncType}&range=${range}`,
  );

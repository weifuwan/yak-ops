import HttpUtils from "@/service/http/HttpUtils";
import { YAK_OPS_API_PREFIX } from "@/config/api";

export interface DataIntegrationOverviewScope {
  scope: "DATABASE" | "MULTI_TABLE" | "SINGLE_TABLE";
  offline: number;
  realtime: number;
  total: number;
}

export interface DataIntegrationRecentFailure {
  instanceId: string;
  taskId: string;
  taskName: string;
  failedAt: string | null;
}

export interface DataIntegrationOverview {
  dataSourceCount: number;
  taskCount: number;
  onlineCount: number;
  offlineCount: number;
  queuedCount: number;
  runningCount: number;
  failedCount: number;
  succeededCount: number;
  stoppedCount: number;
  pausedCount: number;
  unreadableCount: number;
  taskTypes: DataIntegrationOverviewScope[];
  recentFailures: DataIntegrationRecentFailure[];
}

export type DataSyncOverviewCategory = "OFFLINE" | "REALTIME" | "UNKNOWN";

export interface DataSyncExecutionDay {
  date: string;
  syncType: DataSyncOverviewCategory;
  executionCount: number;
  succeededCount: number;
  failedCount: number;
  stoppedCount: number;
  runningCount: number;
  queuedCount: number;
  pausedCount: number;
}

export interface DataSyncExecutionStats {
  startDate: string;
  endDate: string;
  days: DataSyncExecutionDay[];
}

export const getDataIntegrationOverview = (
  projectCode: string,
  signal?: AbortSignal,
): Promise<DataIntegrationOverview> =>
  HttpUtils.getData<DataIntegrationOverview>(
    YAK_OPS_API_PREFIX +
      "/projects/" +
      encodeURIComponent(projectCode) +
      "/data-sync/overview",
    { signal },
  );

export const getDataSyncExecutionStats = (
  projectCode: string,
  startDate: string,
  endDate: string,
  signal?: AbortSignal,
): Promise<DataSyncExecutionStats> =>
  HttpUtils.getData<DataSyncExecutionStats>(
    YAK_OPS_API_PREFIX +
      "/projects/" +
      encodeURIComponent(projectCode) +
      "/data-sync/overview/executions?" +
      new URLSearchParams({ startDate, endDate }).toString(),
    { signal },
  );

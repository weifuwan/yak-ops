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

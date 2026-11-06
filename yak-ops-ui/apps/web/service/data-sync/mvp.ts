import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";

const API = YAK_OPS_API_PREFIX + "/api/v1/data-sync/mvp";

export interface SyncProject {
  code: string;
  name: string;
}

export interface SyncMvpTable {
  database: string;
  table: string;
}

export interface SyncMvpTask {
  id: string;
  taskCode: string;
  name: string;
  version: number;
  projectCode: string;
  taskType: "DATA_SYNC";
  parameters: {
    source: { datasourceId: string; tables: SyncMvpTable[] };
    sink: { datasourceId: string; tables: SyncMvpTable[] };
  };
}

export interface SyncMvpInstance {
  id: string | null;
  workflowInstanceId: number;
  workflowCode: string;
  taskName: string;
  status: string;
  startTime: string | null;
  endTime?: string | null;
  host?: string | null;
}

export interface CreateSyncMvpTask {
  projectCode: number;
  name: string;
  sourceDataSourceId: number;
  sourceDatabase: string;
  sourceTable: string;
  targetDataSourceId: number;
  targetDatabase: string;
  targetTable: string;
}

const projectQuery = (projectCode: string) => new URLSearchParams({ projectCode }).toString();

export const listSyncMvpProjects = () => HttpUtils.getData<SyncProject[]>(API + "/projects");

export const listSyncMvpTasks = (projectCode: string) =>
  HttpUtils.getData<SyncMvpTask[]>(API + "/tasks?" + projectQuery(projectCode));

export const createSyncMvpTask = (task: CreateSyncMvpTask) =>
  HttpUtils.postData<{ id: string; taskCode: string }>(API + "/tasks", task);

export const startSyncMvpTask = (projectCode: string, workflowCode: string) =>
  HttpUtils.postData<{ workflowInstanceId: string; taskCode: string }>(
    API + "/tasks/" + encodeURIComponent(workflowCode) + "/start?" + projectQuery(projectCode),
  );

export const listSyncMvpInstances = (projectCode: string) =>
  HttpUtils.getData<SyncMvpInstance[]>(API + "/instances?" + projectQuery(projectCode));

export const stopSyncMvpInstance = (projectCode: string, instanceId: string) =>
  HttpUtils.postData<boolean>(
    API + "/instances/" + encodeURIComponent(instanceId) + "/stop?" + projectQuery(projectCode),
  );

export const getSyncMvpLogs = (projectCode: string, instanceId: string, offset = 0) =>
  HttpUtils.getData<{ content: string; offset: number }>(
    API + "/instances/" + encodeURIComponent(instanceId) + "/logs?" +
      new URLSearchParams({ projectCode, offset: String(offset) }).toString(),
  );

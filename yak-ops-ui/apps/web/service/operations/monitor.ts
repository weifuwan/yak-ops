import { YAK_OPS_API_PREFIX } from "@/config/api";
import HttpUtils from "@/service/http/HttpUtils";

export type MonitorNodeType = "MASTER" | "WORKER" | "ALERT_SERVER";
export type ServerStatus = "NORMAL" | "BUSY";

export interface ServerHeartbeat {
  processId?: number;
  startupTime?: number;
  reportTime?: number;
  jvmCpuUsage?: number;
  cpuUsage?: number;
  jvmMemoryUsage?: number;
  jvmHeapUsed?: number;
  jvmNonHeapUsed?: number;
  jvmHeapMax?: number;
  jvmNonHeapMax?: number;
  memoryUsage?: number;
  diskUsage?: number;
  serverStatus?: ServerStatus | string;
  host?: string;
  port?: number;
  workerHostWeight?: number;
  threadPoolUsage?: number;
  workerGroup?: string;
}

export interface MonitorServer {
  id: number;
  host: string;
  port: number;
  serverDirectory?: string | null;
  heartBeatInfo?: string | null;
  createTime?: string | null;
  lastHeartbeatTime?: string | null;
}

export interface DatabaseMetrics {
  dbType?: string | null;
  state?: "YES" | "NO" | string | null;
  maxConnections: number;
  maxUsedConnections: number;
  threadsConnections: number;
  threadsRunningConnections: number;
  date?: string | null;
}

export interface MonitorServerView extends MonitorServer {
  heartbeat: ServerHeartbeat;
}

const MONITOR_API = YAK_OPS_API_PREFIX + "/monitor";

export async function listMonitorServers(
  nodeType: MonitorNodeType,
  signal?: AbortSignal,
): Promise<MonitorServer[]> {
  return HttpUtils.getData<MonitorServer[]>(
    MONITOR_API + "/" + encodeURIComponent(nodeType),
    { signal },
  );
}

export async function listDatabaseMetrics(signal?: AbortSignal): Promise<DatabaseMetrics[]> {
  return HttpUtils.getData<DatabaseMetrics[]>(MONITOR_API + "/databases", { signal });
}

export function parseServerHeartbeat(server: MonitorServer): MonitorServerView {
  if (!server.heartBeatInfo) return { ...server, heartbeat: {} };

  try {
    const parsed = JSON.parse(server.heartBeatInfo) as ServerHeartbeat;
    return { ...server, heartbeat: parsed && typeof parsed === "object" ? parsed : {} };
  } catch {
    return { ...server, heartbeat: {} };
  }
}

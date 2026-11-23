import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Activity,
  AlertTriangle,
  Cpu,
  HardDrive,
  MemoryStick,
  RefreshCw,
  Server,
  type LucideIcon,
} from "lucide-react";
import {
  Badge,
  Button,
  Card,
  CardContent,
  Table,
  type TableColumns,
} from "@yak-ops/yak-ui";

import {
  listDatabaseMetrics,
  listMonitorServers,
  parseServerHeartbeat,
  type DatabaseMetrics,
  type MonitorServerView,
} from "@/service/operations/monitor";
import { OperationsPageShell } from "../components/operations-page-shell";

const REFRESH_INTERVAL = 30_000;

function percent(value?: number): string {
  if (value === undefined || !Number.isFinite(value)) return "—";
  return `${(value * 100).toFixed(1)}%`;
}

function average(values: Array<number | undefined>): number | undefined {
  const valid = values.filter((value): value is number =>
    value !== undefined && Number.isFinite(value),
  );
  if (!valid.length) return undefined;
  return valid.reduce((sum, value) => sum + value, 0) / valid.length;
}

function formatTime(value?: string | null): string {
  if (!value) return "—";
  const timestamp = Date.parse(value);
  if (!Number.isFinite(timestamp)) return value;

  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).format(timestamp);
}

function serverAddress(server: MonitorServerView): string {
  return `${server.host || server.heartbeat.host || "—"}:${server.port || server.heartbeat.port || "—"}`;
}

function statusBadge(status?: string) {
  if (status === "NORMAL") return <Badge tone="success">正常</Badge>;
  if (status === "BUSY") return <Badge tone="warning">繁忙</Badge>;
  return <Badge tone="neutral">{status || "未知"}</Badge>;
}

function MetricCard({
  label,
  value,
  icon: Icon,
}: {
  label: string;
  value: string | number;
  icon: LucideIcon;
}) {
  return (
    <Card>
      <CardContent className="p-5">
        <div className="flex items-center justify-between gap-3">
          <div className="truncate text-xs text-[var(--yak-components-muted-text)]">{label}</div>
          <Icon size={18} className="shrink-0 text-[var(--yak-components-muted-text)]" />
        </div>
        <div className="mt-3 text-3xl font-semibold tabular-nums">{value}</div>
      </CardContent>
    </Card>
  );
}

function UsageItem({
  label,
  value,
  icon: Icon,
}: {
  label: string;
  value?: number;
  icon: LucideIcon;
}) {
  const normalized = value === undefined ? 0 : Math.max(0, Math.min(1, value));
  const barColor =
    normalized >= 0.8 ? "#ef4444" : normalized >= 0.6 ? "#f59e0b" : "var(--yak-color-primary)";

  return (
    <div className="rounded-lg border border-[var(--yak-components-panel-border)] p-4">
      <div className="flex items-center justify-between gap-3 text-xs">
        <div className="flex min-w-0 items-center gap-2">
          <Icon size={15} className="shrink-0 text-[var(--yak-components-muted-text)]" />
          <span className="truncate">{label}</span>
        </div>
        <span className="shrink-0 font-medium tabular-nums">{percent(value)}</span>
      </div>
      <div className="mt-3 h-1.5 overflow-hidden rounded-full bg-[var(--yak-color-hover)]">
        <div
          className="h-full rounded-full transition-[width] duration-300"
          style={{ width: `${normalized * 100}%`, backgroundColor: barColor }}
        />
      </div>
    </div>
  );
}

function ResourceOverview({ servers }: { servers: MonitorServerView[] }) {
  const values = useMemo(
    () => ({
      cpu: average(servers.map((item) => item.heartbeat.cpuUsage)),
      memory: average(servers.map((item) => item.heartbeat.memoryUsage)),
      disk: average(servers.map((item) => item.heartbeat.diskUsage)),
      jvmCpu: average(servers.map((item) => item.heartbeat.jvmCpuUsage)),
      jvmMemory: average(servers.map((item) => item.heartbeat.jvmMemoryUsage)),
    }),
    [servers],
  );

  return (
    <Card>
      <CardContent className="p-4">
        <div className="mb-3 text-sm font-medium">资源使用概览</div>
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-5">
          <UsageItem label="系统 CPU" value={values.cpu} icon={Cpu} />
          <UsageItem label="系统内存" value={values.memory} icon={MemoryStick} />
          <UsageItem label="磁盘使用" value={values.disk} icon={HardDrive} />
          <UsageItem label="JVM CPU" value={values.jvmCpu} icon={Activity} />
          <UsageItem label="JVM 内存" value={values.jvmMemory} icon={MemoryStick} />
        </div>
      </CardContent>
    </Card>
  );
}

const commonServerColumns: TableColumns<MonitorServerView> = [
  {
    key: "address",
    title: "节点地址",
    minWidth: 190,
    render: (_value, record) => (
      <span className="font-medium text-[var(--yak-components-panel-text)]">
        {serverAddress(record)}
      </span>
    ),
  },
  {
    key: "status",
    title: "状态",
    width: 90,
    render: (_value, record) => statusBadge(record.heartbeat.serverStatus),
  },
  {
    key: "cpu",
    title: "CPU",
    width: 95,
    align: "right",
    render: (_value, record) => (
      <span className="tabular-nums">{percent(record.heartbeat.cpuUsage)}</span>
    ),
  },
  {
    key: "memory",
    title: "内存",
    width: 95,
    align: "right",
    render: (_value, record) => (
      <span className="tabular-nums">{percent(record.heartbeat.memoryUsage)}</span>
    ),
  },
  {
    key: "disk",
    title: "磁盘",
    width: 95,
    align: "right",
    render: (_value, record) => (
      <span className="tabular-nums">{percent(record.heartbeat.diskUsage)}</span>
    ),
  },
  {
    key: "jvmCpu",
    title: "JVM CPU",
    width: 105,
    align: "right",
    render: (_value, record) => (
      <span className="tabular-nums">{percent(record.heartbeat.jvmCpuUsage)}</span>
    ),
  },
  {
    key: "jvmMemory",
    title: "JVM 内存",
    width: 110,
    align: "right",
    render: (_value, record) => (
      <span className="tabular-nums">{percent(record.heartbeat.jvmMemoryUsage)}</span>
    ),
  },
  {
    key: "processId",
    title: "PID",
    width: 90,
    align: "right",
    render: (_value, record) => record.id || record.heartbeat.processId || "—",
  },
  {
    key: "heartbeat",
    title: "最后心跳",
    width: 180,
    render: (_value, record) => formatTime(record.lastHeartbeatTime),
  },
];

function MonitorTableHeader({
  title,
  count,
  unit,
}: {
  title: string;
  count: number;
  unit: string;
}) {
  return (
    <div className="flex h-11 items-center justify-between gap-4 border-b border-[var(--yak-components-panel-border)] px-4">
      <div className="text-sm font-medium">{title}</div>
      <div className="shrink-0 text-xs tabular-nums text-[var(--yak-components-muted-text)]">
        {count} {unit}
      </div>
    </div>
  );
}

function ServerTableCard({
  title,
  records,
  loading,
  columns = commonServerColumns,
}: {
  title: string;
  records: MonitorServerView[];
  loading: boolean;
  columns?: TableColumns<MonitorServerView>;
}) {
  return (
    <Card>
      <CardContent className="p-0">
        <MonitorTableHeader title={title} count={records.length} unit="个节点" />
        <Table<MonitorServerView>
          columns={columns}
          dataSource={records}
          rowKey={(record) => `${record.host}:${record.port}:${record.id}`}
          loading={loading}
          bordered
          size="small"
          scroll={{ x: 1080 }}
          pagination={false}
          emptyText="暂无在线节点"
        />
      </CardContent>
    </Card>
  );
}

const workerColumns: TableColumns<MonitorServerView> = [
  ...commonServerColumns.slice(0, 1),
  {
    key: "workerGroup",
    title: "Worker 分组",
    width: 140,
    render: (_value, record) => record.heartbeat.workerGroup || "—",
  },
  ...commonServerColumns.slice(1, 2),
  {
    key: "threadPool",
    title: "线程池",
    width: 100,
    align: "right",
    render: (_value, record) => (
      <span className="tabular-nums">{percent(record.heartbeat.threadPoolUsage)}</span>
    ),
  },
  ...commonServerColumns.slice(2),
];

const databaseColumns: TableColumns<DatabaseMetrics> = [
  {
    key: "dbType",
    title: "数据库",
    minWidth: 140,
    render: (_value, record) => (
      <span className="font-medium">{record.dbType || "—"}</span>
    ),
  },
  {
    key: "state",
    title: "状态",
    width: 90,
    render: (_value, record) =>
      record.state === "YES" ? (
        <Badge tone="success">正常</Badge>
      ) : (
        <Badge tone="danger">异常</Badge>
      ),
  },
  {
    key: "current",
    title: "当前连接",
    width: 120,
    align: "right",
    render: (_value, record) => record.threadsConnections.toLocaleString("zh-CN"),
  },
  {
    key: "running",
    title: "活跃连接",
    width: 120,
    align: "right",
    render: (_value, record) => record.threadsRunningConnections.toLocaleString("zh-CN"),
  },
  {
    key: "maxUsed",
    title: "历史最大连接",
    width: 140,
    align: "right",
    render: (_value, record) => record.maxUsedConnections.toLocaleString("zh-CN"),
  },
  {
    key: "max",
    title: "最大连接数",
    width: 120,
    align: "right",
    render: (_value, record) => record.maxConnections.toLocaleString("zh-CN"),
  },
  {
    key: "usage",
    title: "连接使用率",
    width: 120,
    align: "right",
    render: (_value, record) =>
      record.maxConnections > 0
        ? `${((record.threadsConnections / record.maxConnections) * 100).toFixed(1)}%`
        : "—",
  },
  {
    key: "date",
    title: "采集时间",
    width: 180,
    render: (_value, record) => formatTime(record.date),
  },
];

export function SystemMonitorPage() {
  const mastersQuery = useQuery({
    queryKey: ["operations", "system-monitor", "master"],
    queryFn: ({ signal }) => listMonitorServers("MASTER", signal),
    refetchInterval: REFRESH_INTERVAL,
  });
  const workersQuery = useQuery({
    queryKey: ["operations", "system-monitor", "worker"],
    queryFn: ({ signal }) => listMonitorServers("WORKER", signal),
    refetchInterval: REFRESH_INTERVAL,
  });
  const alertsQuery = useQuery({
    queryKey: ["operations", "system-monitor", "alert-server"],
    queryFn: ({ signal }) => listMonitorServers("ALERT_SERVER", signal),
    refetchInterval: REFRESH_INTERVAL,
  });
  const databaseQuery = useQuery({
    queryKey: ["operations", "system-monitor", "database"],
    queryFn: ({ signal }) => listDatabaseMetrics(signal),
    refetchInterval: REFRESH_INTERVAL,
  });

  const masters = (mastersQuery.data ?? []).map(parseServerHeartbeat);
  const workers = (workersQuery.data ?? []).map(parseServerHeartbeat);
  const alertServers = (alertsQuery.data ?? []).map(parseServerHeartbeat);
  const allServers = [...masters, ...workers, ...alertServers];

  const busyCount = allServers.filter((item) => item.heartbeat.serverStatus === "BUSY").length;
  const avgCpu = average(allServers.map((item) => item.heartbeat.cpuUsage));
  const avgMemory = average(allServers.map((item) => item.heartbeat.memoryUsage));

  const isRefreshing =
    mastersQuery.isFetching ||
    workersQuery.isFetching ||
    alertsQuery.isFetching ||
    databaseQuery.isFetching;

  const hasError =
    mastersQuery.isError ||
    workersQuery.isError ||
    alertsQuery.isError ||
    databaseQuery.isError;

  const refresh = async () => {
    await Promise.all([
      mastersQuery.refetch(),
      workersQuery.refetch(),
      alertsQuery.refetch(),
      databaseQuery.refetch(),
    ]);
  };

  return (
    <OperationsPageShell
      title="系统监控"
      extra={
        <Button size="sm" variant="secondary" onClick={() => void refresh()}>
          <RefreshCw
            size={14}
            aria-hidden="true"
            className={isRefreshing ? "animate-spin" : undefined}
          />
          刷新
        </Button>
      }
    >
      <div className="min-h-0 flex-1 space-y-3 overflow-y-auto">
        {hasError ? (
          <div className="rounded-md border border-[var(--yak-components-panel-border)] px-4 py-3 text-sm text-[var(--yak-components-muted-text)]">
            部分系统监控数据加载失败，已成功返回的节点和指标仍会正常展示。
          </div>
        ) : null}

        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-6">
          <MetricCard label="Master 节点" value={masters.length} icon={Server} />
          <MetricCard label="Worker 节点" value={workers.length} icon={Server} />
          <MetricCard label="Alert Server" value={alertServers.length} icon={AlertTriangle} />
          <MetricCard label="繁忙节点" value={busyCount} icon={AlertTriangle} />
          <MetricCard label="平均 CPU" value={percent(avgCpu)} icon={Cpu} />
          <MetricCard label="平均内存" value={percent(avgMemory)} icon={MemoryStick} />
        </div>

        <ResourceOverview servers={allServers} />

        <ServerTableCard
          title="Master 节点"
          records={masters}
          loading={mastersQuery.isPending}
        />

        <ServerTableCard
          title="Worker 节点"
          records={workers}
          loading={workersQuery.isPending}
          columns={workerColumns}
        />

        <ServerTableCard
          title="Alert Server"
          records={alertServers}
          loading={alertsQuery.isPending}
        />

        <Card>
          <CardContent className="p-0">
            <MonitorTableHeader
              title="数据库指标"
              count={(databaseQuery.data ?? []).length}
              unit="个数据源"
            />
            <Table<DatabaseMetrics>
              columns={databaseColumns}
              dataSource={databaseQuery.data ?? []}
              rowKey={(record) => `${record.dbType || "database"}-${record.date || "current"}`}
              loading={databaseQuery.isPending}
              bordered
              size="small"
              scroll={{ x: 1050 }}
              pagination={false}
              emptyText="暂无数据库监控数据"
            />
          </CardContent>
        </Card>
      </div>
    </OperationsPageShell>
  );
}

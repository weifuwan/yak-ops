import {
  Badge,
  Button,
  Input,
  Table,
  toast,
  type BadgeProps,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { useCallback, useEffect, useState } from "react";

import {
  cancelDataSyncInstance,
  listDataSyncOperationTasks,
  runDataSyncTask,
  type DataSyncInstanceRecord,
  type DataSyncTaskOperationRecord,
  type DataSyncType,
} from "@/service/data-sync";

const PAGE_SIZE = 20;
const POLL_INTERVAL_MILLIS = 2000;

const isActive = (instance?: DataSyncInstanceRecord) =>
  instance?.status === "PENDING" ||
  instance?.status === "RUNNING" ||
  instance?.status === "RETRY_WAITING";

const triggerText = (triggerType?: string) => {
  if (triggerType === "MANUAL") return "手动";
  if (triggerType === "SCHEDULE") return "调度";
  if (triggerType === "AUTO_RECOVERY") return "自动恢复";
  if (triggerType === "RETRY") return "重试";
  return triggerType || "-";
};

const instanceStatusMeta = (
  record: DataSyncTaskOperationRecord,
): { label: string; tone: BadgeProps["tone"] } => {
  const latest = record.latestInstance;
  if (!latest) {
    if (record.syncType === "REALTIME" && record.desiredState === "RUNNING") {
      return { label: "待处理", tone: "warning" };
    }
    return { label: "未运行", tone: "neutral" };
  }
  if (!isActive(latest) && record.syncType === "REALTIME" && record.desiredState === "RUNNING") {
    return { label: "待处理", tone: "warning" };
  }
  switch (latest.status) {
    case "PENDING":
      return { label: "等待", tone: "neutral" };
    case "RUNNING":
      return { label: "运行中", tone: "info" };
    case "RETRY_WAITING":
      return { label: "等待重试", tone: "warning" };
    case "SUCCEEDED":
      return { label: "成功", tone: "success" };
    case "FAILED":
      return { label: "失败", tone: "danger" };
    case "CANCELED":
      return { label: record.syncType === "REALTIME" ? "已停止" : "已取消", tone: "neutral" };
    case "LOST":
      return { label: "已丢失", tone: "warning" };
    default:
      return { label: latest.status || "-", tone: "neutral" };
  }
};

const lastRunText = (record: DataSyncTaskOperationRecord) => {
  const latest = record.latestInstance;
  if (!latest) return { time: "-", trigger: "-" };
  return {
    time: latest.startTime || latest.createTime || "-",
    trigger: triggerText(latest.triggerType),
  };
};

const nextRunText = (record: DataSyncTaskOperationRecord) => {
  if (record.syncType === "REALTIME") {
    if (record.desiredState !== "RUNNING") return "-";
    return isActive(record.latestInstance) ? "持续运行" : "待处理";
  }
  if (!record.schedule?.enabled) return "-";
  return record.schedule.nextFireTime || "待计算";
};

interface DataSyncTaskOperationsProps {
  syncType: DataSyncType;
  onOpenInstances: (taskId: string) => void;
  onOpenInstanceDetail: (instanceId: string) => void;
}

export function DataSyncTaskOperations({
  syncType,
  onOpenInstances,
  onOpenInstanceDetail,
}: DataSyncTaskOperationsProps) {
  const realtime = syncType === "REALTIME";
  const [records, setRecords] = useState<DataSyncTaskOperationRecord[]>([]);
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [actionKey, setActionKey] = useState<string>();

  const loadTasks = useCallback(async () => {
    setLoading(true);
    try {
      const result = await listDataSyncOperationTasks({
        pageNo,
        pageSize: PAGE_SIZE,
        keyword: keyword.trim() || undefined,
        syncType,
        status: "PUBLISHED",
      });
      setRecords(result?.bizData || []);
      setTotal(result?.pagination?.total || 0);
    } finally {
      setLoading(false);
    }
  }, [keyword, pageNo, syncType]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadTasks(), keyword.trim() ? 250 : 0);
    return () => window.clearTimeout(timer);
  }, [keyword, loadTasks]);

  useEffect(() => {
    if (!records.some((record) => isActive(record.latestInstance))) return;
    const timer = window.setInterval(() => void loadTasks(), POLL_INTERVAL_MILLIS);
    return () => window.clearInterval(timer);
  }, [loadTasks, records]);

  const runTask = async (record: DataSyncTaskOperationRecord) => {
    if (actionKey) return;
    setActionKey(`${record.id}:run`);
    try {
      const instance = await runDataSyncTask(record.id);
      toast.success(realtime ? "实时同步任务已启动" : "同步任务已启动");
      await loadTasks();
      onOpenInstanceDetail(instance.id);
    } finally {
      setActionKey(undefined);
    }
  };

  const stopTask = async (record: DataSyncTaskOperationRecord) => {
    const activeInstance = record.latestInstance;
    if (!isActive(activeInstance) || !activeInstance || actionKey) return;
    setActionKey(`${record.id}:stop`);
    try {
      await cancelDataSyncInstance(activeInstance.id);
      toast.success(realtime ? "实时同步实例已停止" : "同步实例已停止");
      await loadTasks();
    } finally {
      setActionKey(undefined);
    }
  };

  const columns: TableColumns<DataSyncTaskOperationRecord> = [
    {
      key: "name",
      title: "任务名称",
      minWidth: 190,
      render: (_value, record) => (
        <div className="min-w-0">
          <div className="truncate text-[13px] font-medium text-[#252832]">{record.name}</div>
          <div className="mt-0.5 text-xs text-[#98a2b3]">v{record.definitionVersion}</div>
        </div>
      ),
    },
    {
      key: "automation",
      title: "自动化",
      minWidth: 180,
      render: (_value, record) =>
        realtime ? (
          <div>
            <Badge tone={record.desiredState === "RUNNING" ? "success" : "neutral"}>
              {record.desiredState === "RUNNING" ? "期望运行" : "期望停止"}
            </Badge>
            <div className="mt-1 text-xs text-[#98a2b3]">
              {record.latestInstance?.triggerType === "AUTO_RECOVERY"
                ? "最近由自动恢复拉起"
                : "Desired State"}
            </div>
          </div>
        ) : record.schedule ? (
          <div>
            <Badge tone={record.schedule.enabled ? "success" : "neutral"}>
              {record.schedule.enabled ? "调度开启" : "调度关闭"}
            </Badge>
            <div
              className="mt-1 truncate text-xs text-[#98a2b3]"
              title={record.schedule.cronExpression}
            >
              {record.schedule.cronExpression}
            </div>
          </div>
        ) : (
          <Badge tone="neutral">仅手动</Badge>
        ),
    },
    {
      key: "runtimeStatus",
      title: "运行状态",
      minWidth: 170,
      render: (_value, record) => {
        const meta = instanceStatusMeta(record);
        const latest = record.latestInstance;
        const detail =
          latest?.errorMessage ||
          (record.syncType === "REALTIME" && record.desiredState === "RUNNING" && !isActive(latest)
            ? `实际状态：${latest?.status || "无活动 Execution"}`
            : undefined);
        return (
          <div>
            <Badge tone={meta.tone}>{meta.label}</Badge>
            {detail ? (
              <div className="mt-1 max-w-[220px] truncate text-xs text-[#98a2b3]" title={detail}>
                {detail}
              </div>
            ) : null}
          </div>
        );
      },
    },
    {
      key: "lastRun",
      title: "上次运行",
      minWidth: 170,
      render: (_value, record) => {
        const lastRun = lastRunText(record);
        return (
          <div>
            <div className="text-xs text-[#475467]">{lastRun.time}</div>
            <div className="mt-1 text-xs text-[#98a2b3]">{lastRun.trigger}</div>
          </div>
        );
      },
    },
    {
      key: "nextRun",
      title: "下次运行",
      minWidth: 160,
      render: (_value, record) => (
        <div>
          <div className="text-xs text-[#475467]">{nextRunText(record)}</div>
          {!realtime && record.schedule?.enabled ? (
            <div className="mt-1 text-xs text-[#98a2b3]">{record.schedule.timeZone}</div>
          ) : null}
        </div>
      ),
    },
    {
      key: "attempt",
      title: "Attempt",
      minWidth: 150,
      render: (_value, record) => {
        const latest = record.latestInstance;
        if (!latest) return "-";
        const current = latest.currentAttempt || 1;
        const max = latest.maxAttempts || record.retryPolicy?.maxAttempts || 1;
        return (
          <div>
            <div className="text-xs font-medium text-[#475467]">
              {current} / {max}
            </div>
            {latest.status === "RETRY_WAITING" ? (
              <div className="mt-1 text-xs text-[#b54708]">
                {latest.nextRetryTime ? `${latest.nextRetryTime} 重试` : "等待重试"}
              </div>
            ) : max > 1 ? (
              <div className="mt-1 text-xs text-[#98a2b3]">
                Backoff {latest.backoffSeconds ?? record.retryPolicy?.backoffSeconds ?? 0}s
              </div>
            ) : null}
          </div>
        );
      },
    },
    {
      key: "actions",
      title: "操作",
      width: 180,
      align: "center",
      render: (_value, record) => {
        const active = isActive(record.latestInstance);
        const actionLoading = active
          ? actionKey === `${record.id}:stop`
          : actionKey === `${record.id}:run`;
        const startLabel =
          realtime && record.desiredState === "RUNNING" && !active
            ? "重新启动"
            : realtime
              ? "启动"
              : "运行";

        return (
          <div className="flex items-center justify-center gap-1">
            <Button
              variant="ghost"
              size="small"
              loading={actionLoading}
              disabled={Boolean(actionKey) && !actionLoading}
              className={
                active
                  ? "px-1 text-xs font-normal text-[#d92d20]"
                  : "px-1 text-xs font-normal text-[var(--yak-color-primary)]"
              }
              onClick={() => {
                if (active) void stopTask(record);
                else void runTask(record);
              }}
            >
              {active ? "停止" : startLabel}
            </Button>
            <span className="h-3 w-px bg-[#e4e7ec]" />
            <Button
              variant="ghost"
              size="small"
              disabled={Boolean(actionKey)}
              className="px-1 text-xs font-normal text-[#667085] hover:text-[var(--yak-color-primary)]"
              onClick={() => onOpenInstances(record.id)}
            >
              实例
            </Button>
          </div>
        );
      },
    },
  ];

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="flex shrink-0 flex-wrap items-center gap-2">
        <div className="w-[300px]">
          <Input
            size="small"
            variant="outlined"
            value={keyword}
            placeholder="搜索任务名称"
            onChange={(event) => {
              setKeyword(event.target.value);
              setPageNo(1);
            }}
          />
        </div>
      </div>

      <div className="mt-4 min-h-0 flex-1">
        <Table<DataSyncTaskOperationRecord>
          className="min-h-full"
          columns={columns}
          dataSource={records}
          rowKey="id"
          loading={loading}
          bordered
          size="medium"
          scroll={{ x: 1270 }}
          emptyText={realtime ? "暂无已上线实时同步任务" : "暂无已上线离线同步任务"}
          pagination={
            total > 0
              ? {
                  current: pageNo,
                  pageSize: PAGE_SIZE,
                  total,
                  disabled: loading,
                  onChange: (page) => setPageNo(page),
                }
              : false
          }
        />
      </div>
    </div>
  );
}

export default DataSyncTaskOperations;

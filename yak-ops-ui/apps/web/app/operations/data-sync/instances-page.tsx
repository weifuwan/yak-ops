import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import {
  Button,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Table,
  toast,
  type TableColumns,
} from "@yak-ops/yak-ui";

import { useWorkspace } from "@/hooks/use-workspace";
import {
  cancelDataSyncInstance,
  listDataSyncInstances,
  type DataSyncInstanceStatus,
  type DataSyncInstanceSummary,
  type DataSyncType,
} from "@/service/data-sync";
import { OperationsPageShell } from "../components/operations-page-shell";
import {
  ACTIVE_INSTANCE_STATES,
  INSTANCE_STATUS_ITEMS,
  durationText,
  instanceStatusText,
  triggerText,
} from "../presentation";

const PAGE_SIZE = 20;

const INSTANCE_STATUS_STYLES: Record<
  DataSyncInstanceStatus,
  { dot: string; text: string; pulse?: boolean }
> = {
  SUBMITTED_SUCCESS: {
    dot: "bg-[var(--yak-color-primary)]",
    text: "text-[var(--yak-color-primary)]",
  },
  DISPATCH: {
    dot: "bg-[var(--yak-color-primary)]",
    text: "text-[var(--yak-color-primary)]",
  },
  RUNNING_EXECUTION: {
    dot: "bg-[var(--yak-color-primary)]",
    text: "text-[var(--yak-color-primary)]",
    pulse: true,
  },
  DELAY_EXECUTION: {
    dot: "bg-amber-500",
    text: "text-amber-600",
  },
  NEED_FAULT_TOLERANCE: {
    dot: "bg-amber-500",
    text: "text-amber-600",
  },
  SUCCESS: {
    dot: "bg-[var(--yak-color-success)]",
    text: "text-[var(--yak-color-success)]",
  },
  FORCED_SUCCESS: {
    dot: "bg-[var(--yak-color-success)]",
    text: "text-[var(--yak-color-success)]",
  },
  FAILURE: {
    dot: "bg-[var(--yak-color-danger)]",
    text: "text-[var(--yak-color-danger)]",
  },
  PAUSE: {
    dot: "bg-[var(--yak-components-muted-text)]",
    text: "text-[var(--yak-components-muted-text)]",
  },
  KILL: {
    dot: "bg-[var(--yak-components-muted-text)]",
    text: "text-[var(--yak-components-muted-text)]",
  },
};

function InstanceStatus({ status }: { status: DataSyncInstanceStatus }) {
  const style = INSTANCE_STATUS_STYLES[status];

  return (
    <span className="inline-flex items-center gap-2 whitespace-nowrap">
      <span
        aria-hidden="true"
        className={`size-2 shrink-0 rounded-full ${style.dot} ${style.pulse ? "animate-pulse" : ""}`}
      />
      <span className={`text-xs ${style.text}`}>{instanceStatusText(status)}</span>
    </span>
  );
}

function OperationsDataSyncInstancesPage({ syncType }: { syncType: DataSyncType }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { currentWorkspace } = useWorkspace();
  const scope = currentWorkspace?.id ?? "";
  const [status, setStatus] = useState<DataSyncInstanceStatus | "ALL">("ALL");
  const [pageNo, setPageNo] = useState(1);

  const refreshSeconds = syncType === "REALTIME" ? 3 : 5;
  const query = useQuery({
    queryKey: ["operations", "data-sync-instances", scope, syncType, pageNo, status],
    queryFn: () =>
      listDataSyncInstances({
        pageNo,
        pageSize: PAGE_SIZE,
        syncType,
        status: status === "ALL" ? undefined : status,
      }),
    enabled: Boolean(scope),
    refetchInterval: refreshSeconds * 1000,
  });

  const stopMutation = useMutation({
    mutationFn: (workflowInstanceId: number) => cancelDataSyncInstance(workflowInstanceId),
    onSuccess: async () => {
      toast.success("停止指令已提交");
      await queryClient.invalidateQueries({ queryKey: ["operations", "data-sync-instances"] });
    },
    onError: () => toast.error("停止实例失败"),
  });

  const columns: TableColumns<DataSyncInstanceSummary> = [
    {
      key: "task",
      title: "任务 / 实例",
      width: "30%",
      render: (_v, r) => (
        <div className="flex min-w-0 flex-col gap-1">
          <div className="truncate text-sm font-medium text-[var(--yak-components-panel-text)]">
            {r.taskName || `同步任务 #${r.taskId}`}
          </div>
          <div className="truncate text-xs leading-4 text-[var(--yak-components-muted-text)]">
            <span>实例 #{r.id}</span>
            <span className="mx-1.5">·</span>
            <span>任务 #{r.taskId}</span>
          </div>
        </div>
      ),
    },
    {
      key: "trigger",
      title: "触发方式",
      width: "10%",
      render: (_v, r) => (
        <span className="whitespace-nowrap text-xs">{triggerText(r.triggerType)}</span>
      ),
    },
    {
      key: "status",
      title: "执行状态",
      width: "11%",
      render: (_v, r) => <InstanceStatus status={r.status} />,
    },
    {
      key: "startTime",
      title: "开始时间",
      width: "15%",
      render: (_v, r) => (
        <span className="whitespace-nowrap text-xs tabular-nums">
          {r.startTime || r.createTime || "-"}
        </span>
      ),
    },
    {
      key: "finishTime",
      title: "结束时间",
      width: "15%",
      render: (_v, r) => (
        <span className="whitespace-nowrap text-xs tabular-nums">
          {r.finishTime || "-"}
        </span>
      ),
    },
    {
      key: "duration",
      title: "运行时长",
      width: "9%",
      render: (_v, r) => (
        <span className="whitespace-nowrap text-xs font-medium tabular-nums">
          {durationText(r.startTime || r.createTime, r.finishTime)}
        </span>
      ),
    },
    {
      key: "actions",
      title: "操作",
      width: "10%",
      fixed: "right",
      align: "center",
      render: (_v, r) => {
        const active = ACTIVE_INSTANCE_STATES.has(r.status);
        return (
          <div className="flex items-center justify-center gap-1 whitespace-nowrap">
            <Button
              size="sm"
              variant="ghost"
              className="px-2 text-xs font-medium !text-[var(--yak-color-primary)] hover:!text-[var(--yak-color-primary)]"
              onClick={() => navigate(`/data-sync/${r.taskId}/detail?executionId=${r.id}`)}
            >
              详情
            </Button>
            {active && r.workflowInstanceId ? (
              <Button
                size="sm"
                variant="ghost"
                className="px-2 text-xs !text-[var(--yak-color-danger)] hover:!text-[var(--yak-color-danger)]"
                isPending={stopMutation.isPending}
                onClick={() => stopMutation.mutate(r.workflowInstanceId!)}
              >
                停止
              </Button>
            ) : null}
          </div>
        );
      },
    },
  ];

  return (
    <OperationsPageShell
      title={syncType === "REALTIME" ? "实时实例" : "离线实例"}
      extra={<Button size="sm" variant="secondary" onClick={() => void query.refetch()}>刷新</Button>}
    >
      <div className="mb-3 flex min-h-9 flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-3">
          <div className="w-[160px]">
            <Select
              size="small"
              items={INSTANCE_STATUS_ITEMS}
              value={status}
              onValueChange={(value) => {
                setStatus(String(value ?? "ALL") as DataSyncInstanceStatus | "ALL");
                setPageNo(1);
              }}
            >
              <SelectTrigger variant="outlined"><SelectValue /></SelectTrigger>
              <SelectContent>
                {Object.entries(INSTANCE_STATUS_ITEMS).map(([value, label]) => (
                  <SelectItem key={value} value={value}>
                    <SelectItemText>{label}</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <span className="text-xs text-[var(--yak-components-muted-text)]">
            共 <span className="font-medium text-[var(--yak-components-panel-text)]">{query.data?.total ?? 0}</span> 条实例
          </span>
        </div>

        <div className="inline-flex items-center gap-2 text-xs text-[var(--yak-components-muted-text)]" aria-live="polite">
          <span
            aria-hidden="true"
            className={`size-1.5 rounded-full ${query.isError ? "bg-[var(--yak-color-danger)]" : "bg-[var(--yak-color-primary)]"} ${syncType === "REALTIME" && !query.isError ? "animate-pulse" : ""}`}
          />
          <span>
            {query.isError
              ? "更新失败"
              : query.isFetching && !query.isPending
                ? "正在同步"
                : `自动刷新 · 每 ${refreshSeconds} 秒`}
          </span>
        </div>
      </div>

      <div className="min-h-0 flex-1">
        <Table<DataSyncInstanceSummary>
          className="min-h-full [&_th]:!text-[var(--yak-components-muted-text)]"
          columns={columns}
          dataSource={query.data?.totalList ?? []}
          rowKey="id"
          bordered
          size="medium"
          loading={query.isPending}
          scroll={{ x: 1080 }}
          emptyText={query.isError ? "实例列表加载失败" : syncType === "REALTIME" ? "暂无实时实例" : "暂无离线实例"}
          pagination={
            (query.data?.total ?? 0) > 0
              ? { current: pageNo, pageSize: PAGE_SIZE, total: query.data?.total ?? 0, onChange: setPageNo }
              : false
          }
        />
      </div>
    </OperationsPageShell>
  );
}

export function OfflineInstancesOperationsPage() {
  return <OperationsDataSyncInstancesPage syncType="OFFLINE" />;
}

export function RealtimeInstancesOperationsPage() {
  return <OperationsDataSyncInstancesPage syncType="REALTIME" />;
}

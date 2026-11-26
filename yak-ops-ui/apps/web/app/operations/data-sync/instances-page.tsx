import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import {
  Button,
  Modal,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Table,
  Switch,
  toast,
  type TableColumns,
} from "@yak-ops/yak-ui";

import { useWorkspace } from "@/hooks/use-workspace";
import { ExecutionStatusIndicator } from "@/app/components/execution-status-indicator";
import {
  listDataSyncInstances,
  operateDataSyncInstance,
  type DataSyncInstanceAction,
  type DataSyncInstanceStatus,
  type DataSyncInstanceSummary,
  type DataSyncType,
} from "@/service/data-sync";
import { OperationsPageShell } from "../components/operations-page-shell";
import {
  INSTANCE_STATUS_ITEMS,
  durationText,
  instanceStatusText,
  triggerText,
} from "../presentation";

const PAGE_SIZE = 20;

const ACTION_LABELS: Record<DataSyncInstanceAction, string> = {
  STOP: "停止",
  RESUME: "恢复",
  RECOVER_FAILURE: "恢复失败任务",
  RERUN: "重新运行",
};

function actionDescription(syncType: DataSyncType, action: DataSyncInstanceAction): string {
  if (syncType === "REALTIME") {
    const descriptions: Record<DataSyncInstanceAction, string> = {
      STOP: "先请求 Flink 生成 Savepoint，再停止当前实时任务。成功后会保存恢复点。",
      RESUME: "使用当前实例最近的有效 Checkpoint 或 Savepoint 创建新实例，从原进度继续执行。",
      RECOVER_FAILURE: "使用当前实例最近的有效 Checkpoint 或 Savepoint 创建新实例，恢复失败的实时同步。",
      RERUN: "创建全新的实时同步实例，从头开始执行，不加载旧 Checkpoint 或 Savepoint。",
    };
    return descriptions[action];
  }

  const descriptions: Record<DataSyncInstanceAction, string> = {
    STOP: "停止当前离线工作流实例。",
    RESUME: "恢复当前已暂停或已停止的工作流实例，继续执行未完成的节点。",
    RECOVER_FAILURE: "恢复当前工作流实例中的失败任务，已成功的任务不会重新执行。",
    RERUN: "重新运行当前工作流实例，按照工作流引擎的重跑规则执行。",
  };
  return descriptions[action];
}

function OperationsDataSyncInstancesPage({ syncType }: { syncType: DataSyncType }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { currentWorkspace } = useWorkspace();
  const scope = currentWorkspace?.id ?? "";
  const [status, setStatus] = useState<DataSyncInstanceStatus | "ALL">("ALL");
  const [pageNo, setPageNo] = useState(1);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [confirmAction, setConfirmAction] = useState<{
    instance: DataSyncInstanceSummary;
    action: DataSyncInstanceAction;
  }>();

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
    refetchInterval: autoRefresh ? refreshSeconds * 1000 : false,
  });

  const actionMutation = useMutation({
    mutationFn: ({ instanceId, action }: { instanceId: string; action: DataSyncInstanceAction }) =>
      operateDataSyncInstance(instanceId, action),
    onSuccess: async (result, variables) => {
      setConfirmAction(undefined);
      if (variables.action === "STOP" && syncType === "REALTIME") {
        toast.success("实时任务已停止，恢复点已保存");
      } else if (variables.action === "STOP") {
        toast.success("停止请求已提交");
      } else if (result.newWorkflowInstanceId) {
        toast.success(
          `${ACTION_LABELS[variables.action]}请求已提交，新工作流实例 #${result.newWorkflowInstanceId}`,
        );
      } else {
        toast.success(`${ACTION_LABELS[variables.action]}请求已提交`);
      }
      await queryClient.invalidateQueries({ queryKey: ["operations", "data-sync-instances"] });
    },
    onError: async () => {
      toast.error("实例操作失败，请刷新状态后重试");
      await queryClient.invalidateQueries({ queryKey: ["operations", "data-sync-instances"] });
    },
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
      render: (_v, r) => <ExecutionStatusIndicator status={r.status} label={instanceStatusText(r.status)} />,
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
      width: "20%",
      fixed: "right",
      align: "center",
      render: (_v, r) => (
        <div className="flex items-center justify-center gap-1 whitespace-nowrap">
          <Button
            size="sm"
            variant="ghost"
            className="px-2 text-xs font-medium !text-[var(--yak-color-primary)] hover:!text-[var(--yak-color-primary)]"
            onClick={() => navigate(`/data-sync/${r.taskId}/detail?executionId=${r.id}`)}
          >
            详情
          </Button>
          {(r.availableActions ?? []).map((action) => (
            <Button
              key={action}
              size="sm"
              variant="ghost"
              className={[
                "px-2 text-xs",
                action === "STOP"
                  ? "!text-[var(--yak-color-danger)] hover:!text-[var(--yak-color-danger)]"
                  : "!text-[var(--yak-color-primary)] hover:!text-[var(--yak-color-primary)]",
              ].join(" ")}
              isDisabled={actionMutation.isPending}
              onClick={() => setConfirmAction({ instance: r, action })}
            >
              {ACTION_LABELS[action]}
            </Button>
          ))}
          {r.recoveryUnavailableReason ? (
            <span
              className="max-w-[112px] truncate text-[11px] text-[var(--yak-components-muted-text)]"
              title={r.recoveryUnavailableReason}
            >
              {r.recoveryUnavailableReason}
            </span>
          ) : null}
        </div>
      ),
    },
  ];

  return (
    <>
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
        </div>

        <label className="inline-flex cursor-pointer items-center gap-2 text-xs text-[var(--yak-components-muted-text)]">
          <span>自动刷新</span>
          <Switch
            size="small"
            checked={autoRefresh}
            onCheckedChange={setAutoRefresh}
            aria-label="自动刷新"
          />
          {autoRefresh ? <span>每 {refreshSeconds} 秒</span> : null}
        </label>
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
          scroll={{ x: 1240 }}
          emptyText={query.isError ? "实例列表加载失败" : syncType === "REALTIME" ? "暂无实时实例" : "暂无离线实例"}
          pagination={
            (query.data?.total ?? 0) > 0
              ? { current: pageNo, pageSize: PAGE_SIZE, total: query.data?.total ?? 0, onChange: setPageNo }
              : false
          }
        />
      </div>
    </OperationsPageShell>
      <Modal
        open={Boolean(confirmAction)}
        centered
        width={480}
        title={confirmAction ? ACTION_LABELS[confirmAction.action] + "数据同步实例" : "实例操作"}
        onClose={() => {
          if (!actionMutation.isPending) setConfirmAction(undefined);
        }}
        footer={
          <>
            <Button
              size="sm"
              variant="secondary"
              isDisabled={actionMutation.isPending}
              onClick={() => setConfirmAction(undefined)}
            >
              取消
            </Button>
            <Button
              size="sm"
              variant="primary"
              isPending={actionMutation.isPending}
              isDisabled={!confirmAction}
              onClick={() => {
                if (!confirmAction) return;
                actionMutation.mutate({
                  instanceId: confirmAction.instance.id,
                  action: confirmAction.action,
                });
              }}
            >
              确认{confirmAction ? ACTION_LABELS[confirmAction.action] : "操作"}
            </Button>
          </>
        }
      >
        {confirmAction ? (
          <div className="space-y-2 text-sm">
            <p>{actionDescription(syncType, confirmAction.action)}</p>
            <div className="rounded-md bg-[#f7f8fa] px-3 py-2 text-xs text-[var(--yak-components-muted-text)]">
              <div>任务：{confirmAction.instance.taskName || `同步任务 #${confirmAction.instance.taskId}`}</div>
              <div className="mt-1">实例：#{confirmAction.instance.id}</div>
            </div>
          </div>
        ) : null}
      </Modal>
    </>
  );
}

export function OfflineInstancesOperationsPage() {
  return <OperationsDataSyncInstancesPage syncType="OFFLINE" />;
}

export function RealtimeInstancesOperationsPage() {
  return <OperationsDataSyncInstancesPage syncType="REALTIME" />;
}

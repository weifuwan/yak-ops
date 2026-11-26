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

const ACTION_DESCRIPTIONS: Record<DataSyncInstanceAction, string> = {
  STOP: "将请求 Flink 生成 Savepoint 后停止任务。停止完成后，可以使用该恢复点继续执行。",
  RESUME: "将使用当前实例保存的恢复点创建一个新实例，原实例和执行记录会保留。",
  RECOVER_FAILURE: "将从当前实例保存的恢复点创建一个新实例，继续处理失败任务。",
  RERUN: "将创建一个全新的执行实例，从头开始运行，不会加载当前实例的旧恢复点。",
};

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
      if (variables.action === "STOP") {
        toast.success("停止请求已提交，正在生成恢复点");
      } else if (result.newWorkflowInstanceId) {
        toast.success(
          `${ACTION_LABELS[variables.action]}请求已提交，新工作流实例 #${result.newWorkflowInstanceId}`,
        );
      } else {
        toast.success(`${ACTION_LABELS[variables.action]}请求已提交`);
      }
      await queryClient.invalidateQueries({ queryKey: ["operations", "data-sync-instances"] });
    },
    onError: () => toast.error("实例操作失败，请刷新状态后重试"),
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
              无恢复点
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
            <p>{ACTION_DESCRIPTIONS[confirmAction.action]}</p>
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

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

function OperationsDataSyncInstancesPage({ syncType }: { syncType: DataSyncType }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { currentWorkspace } = useWorkspace();
  const scope = currentWorkspace?.id ?? "";
  const [status, setStatus] = useState<DataSyncInstanceStatus | "ALL">("ALL");
  const [pageNo, setPageNo] = useState(1);

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
    refetchInterval: syncType === "REALTIME" ? 3000 : 5000,
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
      minWidth: 230,
      render: (_v, r) => (
        <div>
          <div className="font-medium">{r.taskName || `同步任务 #${r.taskId}`}</div>
          <div className="mt-0.5 text-xs text-[var(--yak-components-muted-text)]">实例 #{r.id} · 任务 #{r.taskId}</div>
        </div>
      ),
    },
    { key: "trigger", title: "触发方式", width: 110, render: (_v, r) => triggerText(r.triggerType) },
    { key: "status", title: "执行状态", width: 110, render: (_v, r) => instanceStatusText(r.status) },
    ...(syncType === "REALTIME"
      ? [{
          key: "runtime",
          title: "运行引擎",
          width: 120,
          render: () => "FLINK",
        } as const]
      : []),
    { key: "startTime", title: "开始时间", width: 180, render: (_v, r) => r.startTime || r.createTime || "-" },
    { key: "finishTime", title: "结束时间", width: 180, render: (_v, r) => r.finishTime || "-" },
    { key: "duration", title: "运行时长", width: 120, render: (_v, r) => durationText(r.startTime || r.createTime, r.finishTime) },
    {
      key: "actions",
      title: "操作",
      width: 150,
      fixed: "right",
      align: "center",
      render: (_v, r) => {
        const active = ACTIVE_INSTANCE_STATES.has(r.status);
        return (
          <div className="flex items-center justify-center gap-1">
            <Button
              size="sm"
              variant="ghost"
              onClick={() => navigate(`/data-sync/${r.taskId}/detail?executionId=${r.id}`)}
            >
              详情
            </Button>
            {active && r.workflowInstanceId ? (
              <Button
                size="sm"
                variant="ghost"
                className="text-[var(--yak-color-danger)]"
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
      <div className="mb-4 flex items-center gap-2">
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
      <div className="min-h-0 flex-1">
        <Table<DataSyncInstanceSummary>
          className="min-h-full"
          columns={columns}
          dataSource={query.data?.totalList ?? []}
          rowKey="id"
          bordered
          size="medium"
          loading={query.isPending || query.isFetching}
          scroll={{ x: syncType === "REALTIME" ? 1160 : 1040 }}
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

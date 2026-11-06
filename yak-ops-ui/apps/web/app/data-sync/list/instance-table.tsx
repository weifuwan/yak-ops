import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { Button, Table, type TableColumns } from "@yak-ops/yak-ui";
import { listDataSyncInstances, type DataSyncInstanceRecord } from "@/service/data-sync";

export function DataSyncInstanceTable({ scope }: { scope: string }) {
  const navigate = useNavigate();
  const query = useQuery({
    queryKey: ["data-sync-instances", scope],
    queryFn: () => listDataSyncInstances({ pageNo: 1, pageSize: 50 }),
    enabled: Boolean(scope),
    refetchInterval: 3000,
  });
  const columns: TableColumns<DataSyncInstanceRecord> = [
    { key: "id", title: "实例 ID", width: 130 },
    { key: "taskName", title: "任务名称", minWidth: 230 },
    {
      key: "status",
      title: "运行状态",
      width: 135,
      render: (_, row) =>
        ({
          SUBMITTED: "已提交",
          LAUNCHING: "启动中",
          RUNNING: "运行中",
          CANCELLING: "停止中",
          FINISHED: "已完成",
          FAILED: "失败",
          CANCELED: "已停止",
          RECONCILING: "恢复中",
        })[row.status] ?? row.status,
    },
    { key: "startTime", title: "启动时间", width: 190, render: (_, row) => row.startTime ?? "—" },
    {
      key: "detail",
      title: "操作",
      width: 110,
      render: (_, row) => (
        <Button
          variant="ghost"
          size="small"
          onClick={() => navigate("/data-sync/" + row.taskId + "/detail?executionId=" + row.id)}
        >
          查看日志
        </Button>
      ),
    },
  ];
  return (
    <div className="min-h-0 min-w-0 flex-1">
      {query.isError ? (
        <div className="mb-3 flex items-center gap-3 text-sm text-red-600">
          执行实例加载失败
          <Button size="small" onClick={() => void query.refetch()}>
            重试
          </Button>
        </div>
      ) : null}
      <Table<DataSyncInstanceRecord>
        rowKey="id"
        columns={columns}
        dataSource={query.data?.bizData ?? []}
        loading={query.isPending}
      />
    </div>
  );
}

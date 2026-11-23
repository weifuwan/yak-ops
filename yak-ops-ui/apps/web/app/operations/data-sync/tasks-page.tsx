import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import {
  Button,
  Input,
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
import { listDataSources } from "@/service/datasource";
import {
  listDataSyncTasks,
  listLatestDataSyncTaskInstances,
  runDataSyncTask,
  type DataSyncTaskListRecord,
  type DataSyncTaskRecord,
  type DataSyncType,
} from "@/service/data-sync";
import { stopWorkflowInstance } from "@/service/workflow/execution";
import type { TaskInstanceRecord } from "@/service/task-execution/types";
import { OperationsPageShell } from "../components/operations-page-shell";
import { ACTIVE_INSTANCE_STATES, instanceStatusText, scopeText } from "../presentation";

const PAGE_SIZE = 20;
const STATUS_ITEMS = { ALL: "全部发布状态", ONLINE: "已发布", OFFLINE: "未发布" };

function isValidTask(record: DataSyncTaskListRecord): record is DataSyncTaskRecord {
  return !("configurationError" in record);
}

function OperationsDataSyncTasksPage({ syncType }: { syncType: DataSyncType }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { currentWorkspace } = useWorkspace();
  const scope = currentWorkspace?.id ?? "";
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState("ALL");
  const [pageNo, setPageNo] = useState(1);

  const query = useQuery({
    queryKey: ["operations", "data-sync-tasks", scope, syncType, pageNo, keyword, status],
    queryFn: ({ signal }) =>
      listDataSyncTasks(
        {
          pageNo,
          pageSize: PAGE_SIZE,
          keyword: keyword || undefined,
          status: status === "ALL" ? undefined : (status as "ONLINE" | "OFFLINE"),
          syncType,
        },
        signal,
      ),
    enabled: Boolean(scope),
  });

  const dataSourcesQuery = useQuery({
    queryKey: ["operations", "data-sources", scope],
    queryFn: ({ signal }) => listDataSources({ pageNo: 1, pageSize: 200 }, signal),
    enabled: Boolean(scope),
    staleTime: 30_000,
  });
  const dataSourceNames = useMemo(
    () =>
      new Map(
        (dataSourcesQuery.data?.totalList ?? []).flatMap((item) =>
          item.id ? [[item.id, item.name || `数据源 #${item.id}`] as const] : [],
        ),
      ),
    [dataSourcesQuery.data],
  );

  const validTasks = useMemo(
    () => (query.data?.totalList ?? []).filter(isValidTask),
    [query.data],
  );
  const latestQuery = useQuery({
    queryKey: ["operations", "data-sync-task-latest", scope, syncType, validTasks.map((item) => item.id).join(",")],
    queryFn: ({ signal }) =>
      listLatestDataSyncTaskInstances(validTasks.map((item) => item.id), false, signal),
    enabled: Boolean(scope && validTasks.length),
    refetchInterval: syncType === "REALTIME" ? 3000 : 5000,
  });
  const latestByTask = useMemo(
    () => new Map<string, TaskInstanceRecord>((latestQuery.data ?? []).map((item) => [item.definitionId, item])),
    [latestQuery.data],
  );

  const actionMutation = useMutation({
    mutationFn: async ({ task, stop }: { task: DataSyncTaskRecord; stop: boolean }) => {
      const latest = latestByTask.get(task.id);
      if (stop) {
        if (!latest?.workflowInstanceId) throw new Error("当前实例缺少工作流实例编号");
        await stopWorkflowInstance(latest.workflowInstanceId);
        return;
      }
      await runDataSyncTask(task.workflowCode);
    },
    onSuccess: async (_data, variables) => {
      toast.success(variables.stop ? "停止指令已提交" : syncType === "REALTIME" ? "启动指令已提交" : "运行指令已提交");
      await queryClient.invalidateQueries({ queryKey: ["operations", "data-sync-task-latest"] });
    },
    onError: () => toast.error("任务操作失败"),
  });

  const columns: TableColumns<DataSyncTaskListRecord> = [
    {
      key: "name",
      title: "任务名称",
      minWidth: 220,
      render: (_v, r) => (
        <div className="min-w-0">
          <div className="truncate text-[13px] font-medium">{r.name}</div>
          <div className="mt-0.5 text-xs text-[var(--yak-components-muted-text)]">任务 #{r.id} · V{r.definitionVersion}</div>
        </div>
      ),
    },
    {
      key: "scope",
      title: "同步范围",
      width: 110,
      render: (_v, r) => isValidTask(r) ? scopeText(r.scope) : "-",
    },
    {
      key: "route",
      title: "同步链路",
      minWidth: 300,
      render: (_v, r) =>
        isValidTask(r) ? (
          <div className="text-xs">
            <div>{r.sourceDatabase || "默认库"}.{r.sourceTable || "*"}</div>
            <div className="mt-0.5 text-[var(--yak-components-muted-text)]">
              {dataSourceNames.get(r.sourceDataSourceId) || `数据源 #${r.sourceDataSourceId}`} →{" "}
              {dataSourceNames.get(r.targetDataSourceId) || `数据源 #${r.targetDataSourceId}`} ·{" "}
              {r.targetDatabase || "默认库"}.{r.targetTable || "*"}
            </div>
          </div>
        ) : (
          <span className="text-[var(--yak-color-danger)]">配置无法解析</span>
        ),
    },
    { key: "status", title: "发布状态", width: 100, render: (_v, r) => r.status === "ONLINE" ? "已发布" : "未发布" },
    {
      key: "runtime",
      title: "最近运行",
      minWidth: 170,
      render: (_v, r) => {
        if (!isValidTask(r)) return "-";
        const latest = latestByTask.get(r.id);
        if (!latest) return <span className="text-[var(--yak-components-muted-text)]">暂无执行</span>;
        return (
          <div className="text-xs">
            <div>{instanceStatusText(latest.status)}</div>
            <div className="mt-0.5 text-[var(--yak-components-muted-text)]">{latest.startTime || latest.createTime || "-"}</div>
          </div>
        );
      },
    },
    {
      key: "updated",
      title: "更新信息",
      width: 170,
      render: (_v, r) => (
        <div className="text-xs">
          <div>{r.updateBy || "-"}</div>
          <div className="mt-0.5 text-[var(--yak-components-muted-text)]">{r.updateTime || "-"}</div>
        </div>
      ),
    },
    {
      key: "actions",
      title: "操作",
      width: 190,
      fixed: "right",
      align: "center",
      render: (_v, r) => {
        if (!isValidTask(r)) {
          return <Button size="sm" variant="ghost" onClick={() => navigate(`/data-sync/${r.id}/edit`)}>查看</Button>;
        }
        const latest = latestByTask.get(r.id);
        const active = Boolean(latest && ACTIVE_INSTANCE_STATES.has(latest.status));
        return (
          <div className="flex items-center justify-center gap-1">
            <Button size="sm" variant="ghost" onClick={() => navigate(`/data-sync/${r.id}/detail`)}>详情</Button>
            <Button
              size="sm"
              variant="ghost"
              className={active ? "text-[var(--yak-color-danger)]" : "text-[var(--yak-color-primary)]"}
              isDisabled={!active && r.status !== "ONLINE"}
              isPending={actionMutation.isPending}
              onClick={() => actionMutation.mutate({ task: r, stop: active })}
            >
              {active ? "停止" : syncType === "REALTIME" ? "启动" : "运行"}
            </Button>
          </div>
        );
      },
    },
  ];

  return (
    <OperationsPageShell
      title={syncType === "REALTIME" ? "实时任务" : "离线任务"}
      extra={<Button size="sm" variant="secondary" onClick={() => void query.refetch()}>刷新</Button>}
    >
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <Input
          size="small"
          variant="outlined"
          className="w-[280px]"
          placeholder="任务名称 / 任务 ID"
          value={keywordInput}
          onChange={(event) => setKeywordInput(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              setKeyword(keywordInput.trim());
              setPageNo(1);
            }
          }}
        />
        <div className="w-[150px]">
          <Select
            size="small"
            items={STATUS_ITEMS}
            value={status}
            onValueChange={(value) => {
              setStatus(String(value ?? "ALL"));
              setPageNo(1);
            }}
          >
            <SelectTrigger variant="outlined"><SelectValue /></SelectTrigger>
            <SelectContent>
              {Object.entries(STATUS_ITEMS).map(([value, label]) => (
                <SelectItem key={value} value={value}>
                  <SelectItemText>{label}</SelectItemText>
                  <SelectItemIndicator />
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <Button size="sm" variant="secondary" onClick={() => { setKeyword(keywordInput.trim()); setPageNo(1); }}>搜索</Button>
      </div>
      <div className="min-h-0 flex-1">
        <Table<DataSyncTaskListRecord>
          className="min-h-full"
          columns={columns}
          dataSource={query.data?.totalList ?? []}
          rowKey="id"
          bordered
          size="medium"
          loading={query.isPending || query.isFetching || latestQuery.isPending || dataSourcesQuery.isPending}
          scroll={{ x: 1280 }}
          emptyText={query.isError ? "任务列表加载失败" : syncType === "REALTIME" ? "暂无实时同步任务" : "暂无离线同步任务"}
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

export function OfflineTasksOperationsPage() {
  return <OperationsDataSyncTasksPage syncType="OFFLINE" />;
}

export function RealtimeTasksOperationsPage() {
  return <OperationsDataSyncTasksPage syncType="REALTIME" />;
}

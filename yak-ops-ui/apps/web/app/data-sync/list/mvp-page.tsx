import { useEffect, useMemo, useState, type ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Modal, PageHeader, toast } from "@yak-ops/yak-ui";
import { useAuth } from "@/hooks/use-auth";
import {
  createSyncMvpTask,
  getSyncMvpLogs,
  listSyncMvpInstances,
  listSyncMvpProjects,
  listSyncMvpTasks,
  startSyncMvpTask,
  stopSyncMvpInstance,
  type CreateSyncMvpTask,
  type SyncMvpInstance,
  type SyncMvpTask,
} from "@/service/data-sync/mvp";
import {
  listDataSources,
  listDataSourceDatabases,
  listDataSourceTables,
  type DataSourceRecord,
} from "@/service/datasource";

const SELECT_CLASS =
  "h-9 w-full min-w-0 rounded border border-[var(--yak-components-border)] bg-white px-3 text-[13px] outline-none";
const INPUT_CLASS = SELECT_CLASS;

function statusText(status: string): string {
  const translations: Record<string, string> = {
    SUBMITTED: "待启动",
    SUBMITTED_SUCCESS: "已提交",
    DISPATCH: "派发中",
    RUNNING_EXECUTION: "运行中",
    SUCCESS: "已完成",
    FAILURE: "失败",
    KILL: "已停止",
    STOP: "已停止",
    READY_STOP: "正在停止",
    PAUSE: "已暂停",
    NEED_FAULT_TOLERANCE: "等待恢复",
  };
  return translations[status] ?? status;
}

const runningStates = new Set(["SUBMITTED", "SUBMITTED_SUCCESS", "DISPATCH", "RUNNING_EXECUTION", "READY_STOP"]);
const canStopStates = new Set(["RUNNING_EXECUTION", "DISPATCH"]);

function Field({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <label className="flex min-w-0 flex-col gap-1.5 text-xs text-[var(--yak-components-muted-text)]">
      {label}
      {children}
    </label>
  );
}

function CreateTaskDialog({
  projectCode,
  sources,
  onClose,
  onSaved,
}: {
  projectCode: string;
  sources: DataSourceRecord[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const mysqlSources = useMemo(() => sources.filter((source) => source.dbType === "MYSQL"), [sources]);
  const [name, setName] = useState("");
  const [sourceId, setSourceId] = useState("");
  const [targetId, setTargetId] = useState("");
  const [sourceDatabase, setSourceDatabase] = useState("");
  const [sourceTable, setSourceTable] = useState("");
  const [targetDatabase, setTargetDatabase] = useState("");
  const [targetTable, setTargetTable] = useState("");
  const sourceDatabases = useQuery({
    queryKey: ["sync-mvp-source-databases", sourceId],
    queryFn: () => listDataSourceDatabases(sourceId),
    enabled: Boolean(sourceId),
  });
  const targetDatabases = useQuery({
    queryKey: ["sync-mvp-target-databases", targetId],
    queryFn: () => listDataSourceDatabases(targetId),
    enabled: Boolean(targetId),
  });
  const sourceTables = useQuery({
    queryKey: ["sync-mvp-source-tables", sourceId, sourceDatabase],
    queryFn: () => listDataSourceTables(sourceId, { database: sourceDatabase }),
    enabled: Boolean(sourceId && sourceDatabase),
  });
  const create = useMutation({
    mutationFn: createSyncMvpTask,
    onSuccess: () => {
      toast.success("同步任务已创建");
      onSaved();
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : "创建任务失败"),
  });
  const submit = () => {
    if (!name.trim() || !sourceId || !targetId || !sourceDatabase ||
        !sourceTable || !targetDatabase || !targetTable) {
      toast.error("请填写完整的任务名称、数据源和表信息");
      return;
    }
    const request: CreateSyncMvpTask = {
      projectCode: Number(projectCode),
      name: name.trim(),
      sourceDataSourceId: Number(sourceId),
      sourceDatabase,
      sourceTable,
      targetDataSourceId: Number(targetId),
      targetDatabase,
      targetTable,
    };
    create.mutate(request);
  };
  return (
    <Modal
      title="新建实时数据同步"
      open
      centered
      width={560}
      onClose={() => { if (!create.isPending) onClose(); }}
      footer={
        <div className="flex justify-end gap-2">
          <Button size="small" disabled={create.isPending} onClick={onClose}>取消</Button>
          <Button size="small" loading={create.isPending} onClick={submit}>创建任务</Button>
        </div>
      }
    >
      <div className="flex flex-col gap-4 py-2">
        <div className="rounded bg-[var(--yak-color-hover)] p-3 text-xs text-[var(--yak-components-muted-text)]">
          当前仅支持 MySQL CDC → MySQL JDBC，首次运行自动进行全量快照，然后持续同步增量变更。
          不会创建定时任务。
        </div>
        <Field label="任务名称">
          <input className={INPUT_CLASS} value={name} onChange={(event) => setName(event.target.value)}
            placeholder="例如：mysql_orders_realtime" />
        </Field>
        <div className="grid grid-cols-2 gap-4">
          <Field label="来源数据源">
            <select className={SELECT_CLASS} value={sourceId} onChange={(event) => {
              setSourceId(event.target.value);
              setSourceDatabase("");
              setSourceTable("");
            }}>
              <option value="">请选择</option>
              {mysqlSources.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
            </select>
          </Field>
          <Field label="目标数据源">
            <select className={SELECT_CLASS} value={targetId} onChange={(event) => {
              setTargetId(event.target.value);
              setTargetDatabase("");
            }}>
              <option value="">请选择</option>
              {mysqlSources.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
            </select>
          </Field>
          <Field label="来源数据库">
            <select className={SELECT_CLASS} value={sourceDatabase} disabled={!sourceId}
              onChange={(event) => { setSourceDatabase(event.target.value); setSourceTable(""); }}>
              <option value="">请选择</option>
              {(sourceDatabases.data ?? []).map((db) => <option key={db} value={db}>{db}</option>)}
            </select>
          </Field>
          <Field label="目标数据库">
            <select className={SELECT_CLASS} value={targetDatabase} disabled={!targetId}
              onChange={(event) => setTargetDatabase(event.target.value)}>
              <option value="">请选择</option>
              {(targetDatabases.data ?? []).map((db) => <option key={db} value={db}>{db}</option>)}
            </select>
          </Field>
          <Field label="来源表">
            <select className={SELECT_CLASS} value={sourceTable} disabled={!sourceDatabase}
              onChange={(event) => {
                setSourceTable(event.target.value);
                setTargetTable(event.target.value);
              }}>
              <option value="">请选择</option>
              {(sourceTables.data ?? []).map((item) =>
                <option key={item.name} value={item.name}>{item.name}</option>
              )}
            </select>
          </Field>
          <Field label="目标表名">
            <input className={INPUT_CLASS} value={targetTable}
              onChange={(event) => setTargetTable(event.target.value)}
              placeholder="目标表可由 CDC 自动建表" />
          </Field>
        </div>
        {sourceDatabases.isError || targetDatabases.isError || sourceTables.isError ? (
          <div className="text-xs text-[var(--yak-components-danger-text)]">
            获取数据库元数据失败，请检查数据源连接状态。
          </div>
        ) : null}
      </div>
    </Modal>
  );
}

export function DataSyncMvpPage() {
  const { currentUser } = useAuth();
  const queryClient = useQueryClient();
  const [projectCode, setProjectCode] = useState("");
  const [tab, setTab] = useState<"tasks" | "instances">("tasks");
  const [createOpen, setCreateOpen] = useState(false);
  const [logInstance, setLogInstance] = useState<SyncMvpInstance | null>(null);
  const [logText, setLogText] = useState("");
  const [logOffset, setLogOffset] = useState(0);
  useEffect(() => {
    setLogText("");
    setLogOffset(0);
  }, [logInstance?.id]);

  const projects = useQuery({
    queryKey: ["sync-mvp-projects", currentUser?.id],
    queryFn: listSyncMvpProjects,
    enabled: Boolean(currentUser?.id),
  });
  useEffect(() => {
    if (!projectCode && projects.data?.length) setProjectCode(projects.data[0].code);
  }, [projectCode, projects.data]);
  const tasks = useQuery({
    queryKey: ["sync-mvp-tasks", currentUser?.id, projectCode],
    queryFn: () => listSyncMvpTasks(projectCode),
    enabled: Boolean(currentUser?.id && projectCode),
  });
  const instances = useQuery({
    queryKey: ["sync-mvp-instances", currentUser?.id, projectCode],
    queryFn: () => listSyncMvpInstances(projectCode),
    enabled: Boolean(currentUser?.id && projectCode),
    refetchInterval: 3000,
  });
  const sources = useQuery({
    queryKey: ["sync-mvp-sources", currentUser?.id],
    queryFn: () => listDataSources({ pageNo: 1, pageSize: 200 }),
    enabled: Boolean(currentUser?.id),
  });
  const sourceNames = useMemo(
    () => new Map((sources.data?.bizData ?? []).map((source) => [source.id, source.name])),
    [sources.data],
  );
  const activeWorkflows = useMemo(
    () => new Set((instances.data ?? []).filter((row) => runningStates.has(row.status))
      .map((row) => row.workflowCode)),
    [instances.data],
  );
  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["sync-mvp-tasks", currentUser?.id, projectCode] }),
      queryClient.invalidateQueries({ queryKey: ["sync-mvp-instances", currentUser?.id, projectCode] }),
    ]);
  };
  const start = useMutation({
    mutationFn: (workflowCode: string) => startSyncMvpTask(projectCode, workflowCode),
    onSuccess: async () => {
      toast.success("已提交启动请求");
      await refresh();
      setTab("instances");
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : "启动失败"),
  });
  const stop = useMutation({
    mutationFn: (id: string) => stopSyncMvpInstance(projectCode, id),
    onSuccess: async () => {
      toast.success("已发送停止请求");
      await refresh();
    },
    onError: (error) => toast.error(error instanceof Error ? error.message : "停止失败"),
  });
  const logs = useQuery({
    queryKey: ["sync-mvp-logs", currentUser?.id, projectCode, logInstance?.id, logOffset],
    queryFn: () => getSyncMvpLogs(projectCode, logInstance!.id!, logOffset),
    enabled: Boolean(projectCode && logInstance?.id),
    refetchInterval: 3000,
  });
  useEffect(() => {
    if (!logs.data) return;
    if (logs.data.content) {
      setLogText((current) => (current + logs.data!.content).slice(-100_000));
    }
    setLogOffset(logs.data.offset);
  }, [logs.data]);

  const route = (task: SyncMvpTask) => {
    const source = task.parameters.source;
    const target = task.parameters.sink;
    const input = source.tables[0];
    const output = target.tables[0];
    return [
      sourceNames.get(source.datasourceId) ?? source.datasourceId,
      input ? input.database + "." + input.table : "",
      sourceNames.get(target.datasourceId) ?? target.datasourceId,
      output ? output.database + "." + output.table : "",
    ];
  };

  return (
    <div className="flex min-h-full flex-col bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
      <PageHeader title="数据同步" bordered className="bg-white px-6 max-md:px-4" />
      <div className="flex-1 p-6 max-md:p-4">
        <div className="flex min-h-[480px] flex-col bg-white p-5">
          <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-6">
              {([["tasks", "任务定义"], ["instances", "执行实例"]] as const).map(([key, label]) => (
                <button key={key} type="button" onClick={() => setTab(key)}
                  className={"border-b-2 pb-2 text-sm " +
                    (tab === key
                      ? "border-[var(--yak-components-primary)] font-medium"
                      : "border-transparent text-[var(--yak-components-muted-text)]")}>
                  {label}
                </button>
              ))}
            </div>
            <div className="flex items-center gap-3">
              <select className={SELECT_CLASS + " max-w-[200px]"} value={projectCode}
                onChange={(event) => setProjectCode(event.target.value)}>
                <option value="">选择项目</option>
                {(projects.data ?? []).map((project) => (
                  <option key={project.code} value={project.code}>{project.name}</option>
                ))}
              </select>
              <Button size="small" disabled={!projectCode || tab !== "tasks"}
                onClick={() => setCreateOpen(true)}>新建同步任务</Button>
            </div>
          </div>

          {tab === "tasks" ? (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[760px] text-left text-[13px]">
                <thead className="border-y border-[var(--yak-components-border)] bg-[var(--yak-color-hover)]">
                  <tr>
                    <th className="px-4 py-3 font-medium">任务名称</th>
                    <th className="px-4 py-3 font-medium">来源</th>
                    <th className="px-4 py-3 font-medium">目标</th>
                    <th className="px-4 py-3 font-medium">同步策略</th>
                    <th className="px-4 py-3 font-medium">运行</th>
                    <th className="px-4 py-3 font-medium text-right">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {(tasks.data ?? []).map((task) => {
                    const [sourceName, sourcePath, targetName, targetPath] = route(task);
                    const busy = activeWorkflows.has(task.id);
                    return (
                      <tr key={task.id} className="border-b border-[var(--yak-components-border)]">
                        <td className="px-4 py-3">
                          <div className="font-medium">{task.name}</div>
                          <div className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
                            V{task.version} · DATA_SYNC
                          </div>
                        </td>
                        <td className="px-4 py-3">{sourceName}<div className="text-xs text-[var(--yak-components-muted-text)]">{sourcePath}</div></td>
                        <td className="px-4 py-3">{targetName}<div className="text-xs text-[var(--yak-components-muted-text)]">{targetPath}</div></td>
                        <td className="px-4 py-3">全量 + 持续增量</td>
                        <td className="px-4 py-3">{busy ? "运行中" : "未运行"}</td>
                        <td className="px-4 py-3 text-right">
                          <Button size="small" disabled={busy || start.isPending}
                            loading={start.isPending && start.variables === task.id}
                            onClick={() => start.mutate(task.id)}>启动</Button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
              {tasks.isLoading ? <p className="p-6 text-center text-sm">正在加载任务...</p> : null}
              {tasks.isError ? <p className="p-6 text-center text-sm text-red-600">任务加载失败，请检查服务状态。</p> : null}
              {tasks.data?.length === 0 ? <p className="p-10 text-center text-sm text-[var(--yak-components-muted-text)]">
                暂无同步任务。选择 MySQL 数据源创建第一个实时同步任务。
              </p> : null}
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[760px] text-left text-[13px]">
                <thead className="border-y border-[var(--yak-components-border)] bg-[var(--yak-color-hover)]">
                  <tr>
                    <th className="px-4 py-3 font-medium">实例 ID</th>
                    <th className="px-4 py-3 font-medium">任务名称</th>
                    <th className="px-4 py-3 font-medium">状态</th>
                    <th className="px-4 py-3 font-medium">开始时间</th>
                    <th className="px-4 py-3 font-medium">Worker</th>
                    <th className="px-4 py-3 font-medium text-right">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {(instances.data ?? []).map((instance) => (
                    <tr key={instance.id ?? "workflow-" + instance.workflowInstanceId}
                      className="border-b border-[var(--yak-components-border)]">
                      <td className="px-4 py-3">{instance.id ?? "准备中"}</td>
                      <td className="px-4 py-3">{instance.taskName}</td>
                      <td className="px-4 py-3">{statusText(instance.status)}</td>
                      <td className="px-4 py-3">{instance.startTime ?? "—"}</td>
                      <td className="px-4 py-3">{instance.host ?? "—"}</td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex justify-end gap-2">
                          <Button size="small" variant="ghost" disabled={!instance.id}
                            onClick={() => setLogInstance(instance)}>日志</Button>
                          {instance.id && canStopStates.has(instance.status) ? (
                            <Button size="small" variant="danger" disabled={stop.isPending}
                              loading={stop.isPending && stop.variables === instance.id}
                              onClick={() => stop.mutate(instance.id!)}>停止</Button>
                          ) : null}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {instances.isLoading ? <p className="p-6 text-center text-sm">正在加载实例...</p> : null}
              {instances.isError ? <p className="p-6 text-center text-sm text-red-600">实例加载失败。</p> : null}
              {instances.data?.length === 0 ? (
                <p className="p-10 text-center text-sm text-[var(--yak-components-muted-text)]">
                  暂无执行记录，请先在任务定义中手动启动。
                </p>
              ) : null}
            </div>
          )}
        </div>
      </div>

      {createOpen ? (
        <CreateTaskDialog projectCode={projectCode} sources={sources.data?.bizData ?? []}
          onClose={() => setCreateOpen(false)}
          onSaved={() => { setCreateOpen(false); void refresh(); }} />
      ) : null}
      {logInstance ? (
        <Modal open centered width={900} title={"运行日志 · " + logInstance.taskName}
          onClose={() => setLogInstance(null)}
          footer={<Button size="small" onClick={() => setLogInstance(null)}>关闭</Button>}>
          <div className="max-h-[60vh] overflow-auto rounded bg-[#101828] p-4 text-xs leading-5 text-gray-100">
            <pre className="whitespace-pre-wrap break-all">
              {logs.isError ? "日志暂不可用，请检查 Worker 是否在线。" :
                logText || "暂无日志，任务启动后会自动刷新。"}
            </pre>
          </div>
          <p className="mt-2 text-xs text-[var(--yak-components-muted-text)]">
            每 3 秒获取新增日志，每次最多 200 行；窗口保留最近 100 KB。
          </p>
        </Modal>
      ) : null}
    </div>
  );
}

export default DataSyncMvpPage;

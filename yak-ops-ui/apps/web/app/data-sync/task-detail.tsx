import {
  Badge,
  Button,
  PageHeader,
  SectionCard,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Spinner,
  Tabs,
  TabsList,
  TabsPanel,
  TabsTab,
  toast,
} from "@yak-ops/yak-ui";
import { ArrowRight, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useState, type ReactNode } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";

import { getDataSource, type DataSourceRecord } from "@/service/datasource";
import {
  getDataSyncInstance,
  getDataSyncSchedule,
  getDataSyncTask,
  listDataSyncAttempts,
  listDataSyncInstances,
  type DataSyncAttemptRecord,
  type DataSyncInstanceRecord,
  type DataSyncScheduleRecord,
  type DataSyncTaskRecord,
  type DataSyncType,
} from "@/service/data-sync";

import { DataSyncExecutionDiagnostics } from "./execution-diagnostics";
import {
  DataSyncExecutionConfigContent,
  DataSyncExecutionDetailContent,
  dataSyncInstanceStatusMeta,
  dataSyncTriggerText,
  isActiveDataSyncInstance,
} from "./execution-detail";
import { DataSyncExecutionLogPanel } from "./execution-log";
import { DataSyncTaskStatusBadge } from "./task-lifecycle";

const EXECUTION_PAGE_SIZE = 20;
const POLL_INTERVAL_MILLIS = 2000;

type ExecutionStatusFilter = "ALL" | "SUCCEEDED" | "FAILED" | "RUNNING";

const EXECUTION_STATUS_ITEMS = {
  ALL: "全部状态",
  SUCCEEDED: "成功",
  FAILED: "失败",
  RUNNING: "运行中",
};

const pathText = (database?: string, schema?: string, table?: string) =>
  [database, schema, table].filter(Boolean).join(".") || "-";

function InfoItem({
  label,
  children,
  inline = false,
}: {
  label: string;
  children: ReactNode;
  inline?: boolean;
}) {
  if (inline) {
    return (
      <div className="flex min-w-0 items-start gap-2 text-[13px] leading-5">
        <div className="shrink-0 text-[#98a2b3]">{label}：</div>
        <div className="min-w-0 flex-1 break-words text-[#344054]">{children}</div>
      </div>
    );
  }

  return (
    <div className="min-w-0">
      <div className="text-xs text-[#98a2b3]">{label}</div>
      <div className="mt-1 min-w-0 break-words text-[13px] text-[#344054]">{children}</div>
    </div>
  );
}

interface DataSyncTaskDetailPageProps {
  syncType: DataSyncType;
  basePath: string;
  title: string;
  localScroll?: boolean;
}

export function DataSyncTaskDetailPage({
  syncType,
  basePath,
  title,
  localScroll = false,
}: DataSyncTaskDetailPageProps) {
  const realtime = syncType === "REALTIME";
  const { taskId } = useParams<{ taskId: string }>();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const executionId = searchParams.get("executionId") || undefined;
  const tab = searchParams.get("tab");
  const activeTab = tab === "config" || tab === "log" ? tab : "status";

  const [task, setTask] = useState<DataSyncTaskRecord>();
  const [source, setSource] = useState<DataSourceRecord>();
  const [target, setTarget] = useState<DataSourceRecord>();
  const [schedule, setSchedule] = useState<DataSyncScheduleRecord>();
  const [taskLoading, setTaskLoading] = useState(true);

  const [executions, setExecutions] = useState<DataSyncInstanceRecord[]>([]);
  const [executionPage, setExecutionPage] = useState(1);
  const [executionTotal, setExecutionTotal] = useState(0);
  const [executionListLoading, setExecutionListLoading] = useState(true);
  const [executionStatus, setExecutionStatus] = useState<ExecutionStatusFilter>("ALL");

  const [selectedExecution, setSelectedExecution] = useState<DataSyncInstanceRecord>();
  const [attempts, setAttempts] = useState<DataSyncAttemptRecord[]>([]);
  const [executionLoading, setExecutionLoading] = useState(false);

  const loadTask = useCallback(async () => {
    if (!taskId) return;
    setTaskLoading(true);
    try {
      const value = await getDataSyncTask(taskId);
      if (value.syncType !== syncType) {
        toast.error("任务类型与当前页面不匹配");
        navigate(basePath, { replace: true });
        return;
      }

      const [sourceValue, targetValue, scheduleValue] = await Promise.all([
        getDataSource(value.sourceDataSourceId).catch(() => undefined),
        getDataSource(value.targetDataSourceId).catch(() => undefined),
        realtime ? Promise.resolve(undefined) : getDataSyncSchedule(value.id),
      ]);

      setTask(value);
      setSource(sourceValue);
      setTarget(targetValue);
      setSchedule(scheduleValue);
    } finally {
      setTaskLoading(false);
    }
  }, [basePath, navigate, realtime, syncType, taskId]);

  const loadExecutions = useCallback(async () => {
    if (!taskId) return;
    setExecutionListLoading(true);
    try {
      const result = await listDataSyncInstances({
        pageNo: executionPage,
        pageSize: EXECUTION_PAGE_SIZE,
        taskId,
        syncType,
        status: executionStatus === "ALL" ? undefined : executionStatus,
      });
      setExecutions(result?.bizData || []);
      setExecutionTotal(result?.pagination?.total || 0);
    } finally {
      setExecutionListLoading(false);
    }
  }, [executionPage, executionStatus, syncType, taskId]);

  const loadSelectedExecution = useCallback(async () => {
    if (!executionId || !taskId) {
      setSelectedExecution(undefined);
      setAttempts([]);
      return;
    }

    setExecutionLoading(true);
    try {
      const [value, attemptItems] = await Promise.all([
        getDataSyncInstance(executionId),
        listDataSyncAttempts(executionId),
      ]);
      if (value.taskId !== taskId || value.syncType !== syncType) {
        toast.error("执行记录与当前任务不匹配");
        navigate(`${basePath}/${taskId}/detail`, { replace: true });
        return;
      }
      setSelectedExecution(value);
      setAttempts(attemptItems || []);
    } finally {
      setExecutionLoading(false);
    }
  }, [basePath, executionId, navigate, syncType, taskId]);

  useEffect(() => {
    void loadTask();
  }, [loadTask]);

  useEffect(() => {
    void loadExecutions();
  }, [loadExecutions]);

  useEffect(() => {
    if (executionId || executions.length === 0) return;
    const next = new URLSearchParams(searchParams);
    next.set("executionId", executions[0].id);
    setSearchParams(next, { replace: true });
  }, [executionId, executions, searchParams, setSearchParams]);

  useEffect(() => {
    void loadSelectedExecution();
  }, [loadSelectedExecution]);

  useEffect(() => {
    if (!executions.some(isActiveDataSyncInstance)) return;
    const timer = window.setInterval(() => void loadExecutions(), POLL_INTERVAL_MILLIS);
    return () => window.clearInterval(timer);
  }, [executions, loadExecutions]);

  useEffect(() => {
    if (!isActiveDataSyncInstance(selectedExecution)) return;
    const timer = window.setInterval(() => void loadSelectedExecution(), POLL_INTERVAL_MILLIS);
    return () => window.clearInterval(timer);
  }, [loadSelectedExecution, selectedExecution]);

  const selectExecution = (id: string) => {
    const next = new URLSearchParams(searchParams);
    next.set("executionId", id);
    setSearchParams(next);
  };

  const changeExecutionPage = (nextPage: number) => {
    const next = new URLSearchParams(searchParams);
    next.delete("executionId");
    setSearchParams(next, { replace: true });
    setExecutionPage(nextPage);
  };

  const changeExecutionStatus = (value: string) => {
    const next = new URLSearchParams(searchParams);
    next.delete("executionId");
    setSearchParams(next, { replace: true });
    setExecutionStatus((value || "ALL") as ExecutionStatusFilter);
    setExecutionPage(1);
  };

  const changeDetailTab = (value: string) => {
    const next = new URLSearchParams(searchParams);
    if (value === "config" || value === "log") next.set("tab", value);
    else next.delete("tab");
    setSearchParams(next);
  };

  if (taskLoading || !task) {
    return (
      <div className="flex min-h-full items-center justify-center bg-[#f6f6f6]">
        <Spinner size="xlarge" label="加载任务详情" />
      </div>
    );
  }

  const totalPages = Math.max(1, Math.ceil(executionTotal / EXECUTION_PAGE_SIZE));
  const scheduleText = schedule
    ? `${schedule.cronExpression} · ${schedule.timeZone} · ${schedule.enabled ? "已开启" : "已关闭"}`
    : "仅手动";
  const desiredStateText =
    task.desiredState === "RUNNING"
      ? "期望运行"
      : task.desiredState === "STOPPED"
        ? "期望停止"
        : "-";

  if (localScroll) {
    return (
      <div className="flex h-full min-h-0 overflow-hidden bg-[#f6f6f6] text-[#242731]">
        <aside className="flex w-[320px] shrink-0 flex-col border-r border-[#e6e8eb] bg-white max-xl:w-[280px]">
          <div className="shrink-0 border-b border-[#eef0f3]">
            <div className="flex items-center justify-between px-4 py-3">
              <div className="text-sm font-semibold text-[#242731]">执行记录</div>
              <Button
                size="small"
                variant="ghost"
                className="h-7 w-7 px-0"
                aria-label="刷新执行记录"
                title="刷新"
                disabled={executionListLoading}
                onClick={() => void loadExecutions()}
              >
                <RefreshCw
                  size={14}
                  className={
                    executionListLoading ? "animate-spin motion-reduce:animate-none" : undefined
                  }
                />
              </Button>
            </div>

            <div className="px-4 pb-3">
              <Select
                size="small"
                items={EXECUTION_STATUS_ITEMS}
                value={executionStatus}
                onValueChange={(value) => changeExecutionStatus(String(value || "ALL"))}
              >
                <SelectTrigger variant="outlined" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">
                    <SelectItemText>全部状态</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                  <SelectItem value="SUCCEEDED">
                    <SelectItemText>成功</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                  <SelectItem value="FAILED">
                    <SelectItemText>失败</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                  <SelectItem value="RUNNING">
                    <SelectItemText>运行中</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          <div className="min-h-0 flex-1 overflow-y-auto p-2">
            {executionListLoading && executions.length === 0 ? (
              <div className="flex min-h-40 items-center justify-center">
                <Spinner label="加载执行记录" />
              </div>
            ) : executions.length === 0 ? (
              <div className="flex min-h-40 items-center justify-center px-4 text-center text-sm text-[#98a2b3]">
                暂无执行记录
              </div>
            ) : (
              <div className="space-y-1">
                {executions.map((execution) => {
                  const meta = dataSyncInstanceStatusMeta(execution.status, realtime);
                  const selected = execution.id === executionId;
                  return (
                    <button
                      key={execution.id}
                      type="button"
                      className={`w-full cursor-pointer rounded-md px-3 py-2.5 text-left transition-colors ${
                        selected ? "bg-[#f5f7ff]" : "bg-white hover:bg-[#f6f6f6]"
                      }`}
                      onClick={() => selectExecution(execution.id)}
                    >
                      <div className="flex items-center justify-between gap-3">
                        <Badge tone={meta.tone}>{meta.label}</Badge>
                        <span
                          className="min-w-0 truncate text-xs text-[#98a2b3]"
                          title={execution.startTime || execution.createTime}
                        >
                          {execution.startTime || execution.createTime || "-"}
                        </span>
                      </div>
                      {realtime ? (
                        <div className="mt-1 truncate text-[11px] text-[#98a2b3]">
                          {dataSyncTriggerText(execution.triggerType)}
                        </div>
                      ) : null}
                    </button>
                  );
                })}
              </div>
            )}
          </div>

          {executionTotal > EXECUTION_PAGE_SIZE ? (
            <div className="flex shrink-0 items-center justify-between border-t border-[#eef0f3] px-3 py-2">
              <Button
                size="small"
                variant="ghost"
                disabled={executionPage <= 1}
                className="px-1 text-xs font-normal"
                onClick={() => changeExecutionPage(executionPage - 1)}
              >
                上一页
              </Button>
              <span className="text-xs text-[#98a2b3]">
                {executionPage} / {totalPages}
              </span>
              <Button
                size="small"
                variant="ghost"
                disabled={executionPage >= totalPages}
                className="px-1 text-xs font-normal"
                onClick={() => changeExecutionPage(executionPage + 1)}
              >
                下一页
              </Button>
            </div>
          ) : null}
        </aside>

        <div className="flex min-w-0 flex-1 flex-col">
          <PageHeader
            title={task.name}
            description={`${title} · v${task.definitionVersion}`}
            bordered
            className="shrink-0 bg-white px-6 max-md:px-4"
            extra={
              <Button size="small" onClick={() => navigate(basePath)}>
                返回任务列表
              </Button>
            }
          />

          <div className="min-h-0 flex-1 overflow-y-auto">
            <div className="mx-6 mb-6 mt-5 space-y-4 max-md:mx-4">
              <SectionCard title="基本信息">
                <div className="grid grid-cols-2 gap-x-6 gap-y-4 max-md:grid-cols-1">
                  <InfoItem label="任务状态" inline>
                    <DataSyncTaskStatusBadge status={task.status} />
                  </InfoItem>
                  <InfoItem label={realtime ? "运行意图" : "调度"} inline>
                    {realtime ? desiredStateText : scheduleText}
                  </InfoItem>
                </div>

                <div className="mt-5 border-t border-[#eef0f3] pt-4">
                  <div className="text-xs text-[#98a2b3]">同步链路</div>
                  <div className="mt-3 flex items-center gap-4 max-md:flex-col max-md:items-stretch">
                    <div className="min-w-0 flex-1 rounded-lg bg-[#fafafa] px-4 py-3">
                      <div className="text-xs text-[#98a2b3]">来源</div>
                      <div className="mt-1 text-sm font-medium text-[#344054]">
                        {source?.name || "数据源加载失败"}
                      </div>
                      <div className="mt-1 break-all text-xs text-[#667085]">
                        {pathText(task.sourceDatabase, task.sourceSchema, task.sourceTable)}
                      </div>
                    </div>
                    <ArrowRight size={18} className="shrink-0 text-[#98a2b3] max-md:rotate-90" />
                    <div className="min-w-0 flex-1 rounded-lg bg-[#fafafa] px-4 py-3">
                      <div className="text-xs text-[#98a2b3]">目标</div>
                      <div className="mt-1 text-sm font-medium text-[#344054]">
                        {target?.name || "数据源加载失败"}
                      </div>
                      <div className="mt-1 break-all text-xs text-[#667085]">
                        {pathText(task.targetDatabase, task.targetSchema, task.targetTable)}
                      </div>
                    </div>
                  </div>
                </div>

                <div className="mt-4 flex flex-wrap gap-x-6 gap-y-2 border-t border-[#eef0f3] pt-3 text-xs text-[#98a2b3]">
                  <span>更新时间：{task.updateTime || task.createTime || "-"}</span>
                  {task.remark ? <span className="break-words">备注：{task.remark}</span> : null}
                </div>
              </SectionCard>

              <section className="min-w-0">
                <Tabs value={activeTab} onValueChange={changeDetailTab}>
                  <TabsList>
                    <TabsTab value="status">执行情况</TabsTab>
                    <TabsTab value="config">配置快照</TabsTab>
                    <TabsTab value="log">{realtime ? "执行日志" : "执行诊断"}</TabsTab>
                  </TabsList>

                  <TabsPanel value="status" className="pt-3">
                    {executionLoading && !selectedExecution ? (
                      <div className="flex min-h-56 items-center justify-center rounded-lg border border-[#e6e8eb] bg-white">
                        <Spinner size="large" label="加载执行详情" />
                      </div>
                    ) : selectedExecution ? (
                      <DataSyncExecutionDetailContent
                        record={selectedExecution}
                        attempts={attempts}
                        realtime={realtime}
                        sectionCard
                      />
                    ) : (
                      <div className="flex min-h-56 items-center justify-center rounded-lg border border-[#e6e8eb] bg-white text-sm text-[#98a2b3]">
                        选择左侧执行记录查看详情
                      </div>
                    )}
                  </TabsPanel>

                  <TabsPanel value="config" className="pt-3">
                    {executionLoading && !selectedExecution ? (
                      <div className="flex min-h-56 items-center justify-center rounded-lg border border-[#e6e8eb] bg-white">
                        <Spinner size="large" label="加载配置快照" />
                      </div>
                    ) : selectedExecution ? (
                      <DataSyncExecutionConfigContent
                        record={selectedExecution}
                        realtime={realtime}
                        sectionCard
                      />
                    ) : (
                      <div className="flex min-h-56 items-center justify-center rounded-lg border border-[#e6e8eb] bg-white text-sm text-[#98a2b3]">
                        选择左侧执行记录查看配置快照
                      </div>
                    )}
                  </TabsPanel>

                  <TabsPanel value="log" className="pt-3">
                    {realtime ? (
                      <DataSyncExecutionLogPanel
                        record={selectedExecution}
                        active={activeTab === "log"}
                        sectionCard
                      />
                    ) : (
                      <DataSyncExecutionDiagnostics
                        record={selectedExecution}
                        attempts={attempts}
                        active={activeTab === "log"}
                      />
                    )}
                  </TabsPanel>
                </Tabs>
              </section>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div
      className={
        localScroll
          ? "flex h-full min-h-0 flex-col overflow-hidden bg-[#f6f6f6] text-[#242731]"
          : "min-h-full bg-[#f6f6f6] text-[#242731]"
      }
    >
      <PageHeader
        title={task.name}
        description={`${title} · v${task.definitionVersion}`}
        bordered
        className={localScroll ? "shrink-0 bg-white px-6 max-md:px-4" : "bg-white px-6 max-md:px-4"}
        extra={
          <Button size="small" onClick={() => navigate(basePath)}>
            返回任务列表
          </Button>
        }
      />

      <div
        className={localScroll ? "min-h-0 flex-1 overflow-y-auto" : "px-6 pb-8 pt-5 max-md:px-4"}
      >
        <div
          className={
            localScroll
              ? "mx-6 mb-6 mt-5 space-y-4 rounded-lg border border-[#e6e8eb] bg-white p-4 max-md:mx-4"
              : "space-y-4"
          }
        >
          <section className="rounded-lg border border-[#e6e8eb] bg-white">
            <div className="border-b border-[#eef0f3] bg-[#fafafa] px-4 py-2.5 text-sm font-semibold text-[#344054]">
              基本信息
            </div>
            <div className="grid grid-cols-4 gap-x-6 gap-y-5 p-5 max-xl:grid-cols-3 max-lg:grid-cols-2 max-md:grid-cols-1">
              <InfoItem label="任务状态">
                <DataSyncTaskStatusBadge status={task.status} />
              </InfoItem>
              <InfoItem label="任务版本">v{task.definitionVersion}</InfoItem>
              <InfoItem label="同步类型">{realtime ? "实时同步" : "离线同步"}</InfoItem>
              {realtime ? <InfoItem label="运行意图">{desiredStateText}</InfoItem> : null}

              <InfoItem label="来源数据源">
                <div>{source?.name || "数据源加载失败"}</div>
                <div className="mt-0.5 text-xs text-[#667085]">
                  {pathText(task.sourceDatabase, task.sourceSchema, task.sourceTable)}
                </div>
              </InfoItem>
              <div className="hidden items-center justify-center xl:flex">
                <ArrowRight size={18} className="text-[#98a2b3]" />
              </div>
              <InfoItem label="目标数据源">
                <div>{target?.name || "数据源加载失败"}</div>
                <div className="mt-0.5 text-xs text-[#667085]">
                  {pathText(task.targetDatabase, task.targetSchema, task.targetTable)}
                </div>
              </InfoItem>
              {!realtime ? <InfoItem label="调度">{scheduleText}</InfoItem> : null}

              <InfoItem label="更新时间">{task.updateTime || task.createTime || "-"}</InfoItem>
              {task.remark ? <InfoItem label="备注">{task.remark}</InfoItem> : null}
            </div>
          </section>

          <div className="flex min-h-[560px] gap-4 max-lg:flex-col">
            <section className="flex w-[300px] shrink-0 flex-col overflow-hidden rounded-lg border border-[#e6e8eb] bg-white max-lg:w-full">
              <div className="shrink-0 border-b border-[#eef0f3]">
                <div className="flex items-center justify-between px-4 py-3">
                  <div className="text-sm font-semibold text-[#344054]">执行记录</div>
                  <Button
                    size="small"
                    variant="ghost"
                    className="h-7 w-7 px-0"
                    aria-label="刷新执行记录"
                    title="刷新"
                    disabled={executionListLoading}
                    onClick={() => void loadExecutions()}
                  >
                    <RefreshCw
                      size={14}
                      className={
                        executionListLoading ? "animate-spin motion-reduce:animate-none" : undefined
                      }
                    />
                  </Button>
                </div>

                <div className="px-4 pb-3">
                  <Select
                    size="small"
                    items={EXECUTION_STATUS_ITEMS}
                    value={executionStatus}
                    onValueChange={(value) => changeExecutionStatus(String(value || "ALL"))}
                  >
                    <SelectTrigger variant="outlined" className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="ALL">
                        <SelectItemText>全部状态</SelectItemText>
                        <SelectItemIndicator />
                      </SelectItem>
                      <SelectItem value="SUCCEEDED">
                        <SelectItemText>成功</SelectItemText>
                        <SelectItemIndicator />
                      </SelectItem>
                      <SelectItem value="FAILED">
                        <SelectItemText>失败</SelectItemText>
                        <SelectItemIndicator />
                      </SelectItem>
                      <SelectItem value="RUNNING">
                        <SelectItemText>运行中</SelectItemText>
                        <SelectItemIndicator />
                      </SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>

              <div className="min-h-0 flex-1 overflow-y-auto p-2">
                {executionListLoading && executions.length === 0 ? (
                  <div className="flex min-h-40 items-center justify-center">
                    <Spinner label="加载执行记录" />
                  </div>
                ) : executions.length === 0 ? (
                  <div className="flex min-h-40 items-center justify-center px-4 text-center text-sm text-[#98a2b3]">
                    暂无执行记录
                  </div>
                ) : (
                  <div className="space-y-1">
                    {executions.map((execution) => {
                      const meta = dataSyncInstanceStatusMeta(execution.status, realtime);
                      const selected = execution.id === executionId;
                      return (
                        <button
                          key={execution.id}
                          type="button"
                          className={`flex w-full cursor-pointer items-center justify-between gap-3 rounded-md px-3 py-2.5 text-left transition-colors ${
                            selected ? "bg-[#f5f7ff]" : "bg-white hover:bg-[#f6f6f6]"
                          }`}
                          onClick={() => selectExecution(execution.id)}
                        >
                          <Badge tone={meta.tone}>{meta.label}</Badge>
                          <span
                            className="min-w-0 truncate text-xs text-[#98a2b3]"
                            title={execution.startTime || execution.createTime}
                          >
                            {execution.startTime || execution.createTime || "-"}
                          </span>
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>

              {executionTotal > EXECUTION_PAGE_SIZE ? (
                <div className="flex items-center justify-between border-t border-[#eef0f3] px-3 py-2">
                  <Button
                    size="small"
                    variant="ghost"
                    disabled={executionPage <= 1}
                    className="px-1 text-xs font-normal"
                    onClick={() => changeExecutionPage(executionPage - 1)}
                  >
                    上一页
                  </Button>
                  <span className="text-xs text-[#98a2b3]">
                    {executionPage} / {totalPages}
                  </span>
                  <Button
                    size="small"
                    variant="ghost"
                    disabled={executionPage >= totalPages}
                    className="px-1 text-xs font-normal"
                    onClick={() => changeExecutionPage(executionPage + 1)}
                  >
                    下一页
                  </Button>
                </div>
              ) : null}
            </section>

            <section className="min-w-0 flex-1">
              <Tabs value={activeTab} onValueChange={changeDetailTab}>
                <TabsList>
                  <TabsTab value="status">执行情况</TabsTab>
                  <TabsTab value="config">配置快照</TabsTab>
                  <TabsTab value="log">执行日志</TabsTab>
                </TabsList>

                <TabsPanel value="status" className="pt-3">
                  {executionLoading && !selectedExecution ? (
                    <div className="flex min-h-56 items-center justify-center rounded-lg border border-[#e6e8eb] bg-white">
                      <Spinner size="large" label="加载执行详情" />
                    </div>
                  ) : selectedExecution ? (
                    <DataSyncExecutionDetailContent
                      record={selectedExecution}
                      attempts={attempts}
                      realtime={realtime}
                      sectionCard
                    />
                  ) : (
                    <div className="flex min-h-56 items-center justify-center rounded-lg border border-[#e6e8eb] bg-white text-sm text-[#98a2b3]">
                      选择左侧执行记录查看详情
                    </div>
                  )}
                </TabsPanel>

                <TabsPanel value="config" className="pt-3">
                  {executionLoading && !selectedExecution ? (
                    <div className="flex min-h-56 items-center justify-center rounded-lg border border-[#e6e8eb] bg-white">
                      <Spinner size="large" label="加载配置快照" />
                    </div>
                  ) : selectedExecution ? (
                    <DataSyncExecutionConfigContent
                      record={selectedExecution}
                      realtime={realtime}
                      sectionCard
                    />
                  ) : (
                    <div className="flex min-h-56 items-center justify-center rounded-lg border border-[#e6e8eb] bg-white text-sm text-[#98a2b3]">
                      选择左侧执行记录查看配置快照
                    </div>
                  )}
                </TabsPanel>

                <TabsPanel value="log" className="pt-3">
                  <DataSyncExecutionLogPanel
                    record={selectedExecution}
                    active={activeTab === "log"}
                    sectionCard
                  />
                </TabsPanel>
              </Tabs>
            </section>
          </div>
        </div>
      </div>
    </div>
  );
}

interface DataSyncLegacyInstanceDetailRedirectProps {
  syncType: DataSyncType;
  basePath: string;
}

export function DataSyncLegacyInstanceDetailRedirect({
  syncType,
  basePath,
}: DataSyncLegacyInstanceDetailRedirectProps) {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  useEffect(() => {
    if (!id) {
      navigate(basePath, { replace: true });
      return;
    }

    void getDataSyncInstance(id)
      .then((record) => {
        if (record.syncType !== syncType) {
          toast.error("实例类型与当前页面不匹配");
          navigate(basePath, { replace: true });
          return;
        }
        navigate(`${basePath}/${record.taskId}/detail?executionId=${record.id}`, {
          replace: true,
        });
      })
      .catch(() => navigate(basePath, { replace: true }));
  }, [basePath, id, navigate, syncType]);

  return (
    <div className="flex min-h-full items-center justify-center bg-[#f6f6f6]">
      <Spinner size="xlarge" label="跳转任务详情" />
    </div>
  );
}

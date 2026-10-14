import {
  Button,
  PageHeader,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Spinner,
  Switch,
  Tabs,
  TabsList,
  TabsPanel,
  TabsTab,
  toast,
} from "@yak-ops/yak-ui";
import { ArrowRight, Copy, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";

import { ScheduleDialog } from "@/app/schedule/schedule-dialog";
import { useTaskSchedules } from "@/app/schedule/use-task-schedules";
import DatabaseIcons from "@/app/datasource/icons/DatabaseIcons";
import { getDataSource, type DataSourceRecord } from "@/service/datasource";
import { getUsersByIds } from "@/service/user";

import {
  cancelDataSyncInstance,
  getDataSyncInstance,
  getDataSyncTask,
  listDataSyncInstances,
  runDataSyncTask,
  type DataSyncInstanceRecord,
  type DataSyncInstanceStatus,
  type DataSyncTaskRecord,
} from "@/service/data-sync";
import { canCancelTaskInstance } from "@/service/data-sync/execution";

import {
  DataSyncExecutionDetailContent,
  ExecutionStatusIcon,
  dataSyncDurationText,
  dataSyncTriggerText,
  isActiveDataSyncInstance,
} from "./execution-detail";
import { TaskInstanceLogPanel } from "./task-instance-log-panel";
import { solutionSummary } from "./solution-presentation";

const PAGE_SIZE = 20;
const POLL_MILLIS = 2000;

function BasicInfoField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid min-w-0 grid-cols-[88px_minmax(0,1fr)] items-start gap-2 text-sm">
      <span className="text-xs leading-5 text-[#98a2b3]">{label}</span>
      <div className="min-w-0 break-words text-[#344054]">{children}</div>
    </div>
  );
}

const INSTANCE_STATUS_ITEMS: Record<DataSyncInstanceStatus | "ALL", string> = {
  ALL: "全部状态",
  SUBMITTED: "等待",
  LAUNCHING: "启动中",
  RUNNING: "运行中",
  CANCELLING: "正在停止",
  RECONCILING: "待确认",
  FINISHED: "成功",
  FAILED: "失败",
  CANCELED: "已停止",
};

export function DataSyncTaskDetailPage() {
  const basePath = "/data-sync";
  const localScroll = true;
  const { id: taskId } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const executionId = searchParams.get("executionId");
  const tab = searchParams.get("tab");
  const activeTab = tab === "log" ? "log" : "status";

  const [task, setTask] = useState<DataSyncTaskRecord>();
  const realtime = task?.syncType === "REALTIME";
  const statusItems = { ...INSTANCE_STATUS_ITEMS, CANCELED: realtime ? "已停止" : "已取消" };
  const [taskLoading, setTaskLoading] = useState(true);
  const [dataSources, setDataSources] = useState<{
    source?: DataSourceRecord;
    target?: DataSourceRecord;
  }>({});
  const [updatedBy, setUpdatedBy] = useState("-");
  const [instances, setInstances] = useState<DataSyncInstanceRecord[]>([]);
  const [instancesLoading, setInstancesLoading] = useState(true);
  const [status, setStatus] = useState<DataSyncInstanceStatus | "ALL">("ALL");
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [selected, setSelected] = useState<DataSyncInstanceRecord>();
  const [selectedLoading, setSelectedLoading] = useState(false);
  const [running, setRunning] = useState(false);
  const [canceling, setCanceling] = useState(false);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [scheduleOpen, setScheduleOpen] = useState(false);
  const [historyScheduleId, setHistoryScheduleId] = useState<string>();
  const schedules = useTaskSchedules(!realtime && taskId ? [taskId] : []);
  const hasEnabledSchedule = Boolean(taskId && schedules.records.get(taskId)?.enabled);
  const listRequest = useRef(0);
  const detailRequest = useRef(0);
  const searchParamsSetter = useRef(setSearchParams);
  const selectedInstance =
    selected?.id === executionId && selected?.taskId === taskId ? selected : undefined;
  const hasActiveInstance =
    instances.some(isActiveDataSyncInstance) || isActiveDataSyncInstance(selectedInstance);

  useEffect(() => {
    searchParamsSetter.current = setSearchParams;
  }, [setSearchParams]);

  const loadInstances = useCallback(async () => {
    if (!taskId) return;
    const request = ++listRequest.current;
    setInstancesLoading(true);
    try {
      const result = await listDataSyncInstances({
        pageNo,
        pageSize: PAGE_SIZE,
        taskId,
        status: status === "ALL" ? undefined : status,
      });
      if (request !== listRequest.current) return;
      setInstances(result.bizData);
      setTotal(result.pagination.total);
    } catch {
      // A failed refresh retains the last observation; the transport reports the failure.
    } finally {
      if (request === listRequest.current) setInstancesLoading(false);
    }
  }, [pageNo, status, taskId]);

  useEffect(() => {
    if (!taskId) return;
    let disposed = false;
    setTask(undefined);
    setTaskLoading(true);
    void getDataSyncTask(taskId)
      .then((value) => {
        if (disposed) return;
        setTask(value);
      })
      .catch(() => {
        if (!disposed) navigate(basePath, { replace: true });
      })
      .finally(() => {
        if (!disposed) setTaskLoading(false);
      });
    return () => {
      disposed = true;
    };
  }, [basePath, navigate, taskId]);

  useEffect(() => {
    const sourceId = task?.sourceDataSourceId;
    const targetId = task?.targetDataSourceId;
    if (!sourceId || !targetId) {
      setDataSources({});
      return;
    }
    let disposed = false;
    setDataSources({});
    void Promise.all([
      getDataSource(sourceId).catch(() => undefined),
      getDataSource(targetId).catch(() => undefined),
    ]).then(([source, target]) => {
      if (!disposed) setDataSources({ source, target });
    });
    return () => {
      disposed = true;
    };
  }, [task?.sourceDataSourceId, task?.targetDataSourceId]);

  useEffect(() => {
    const userId = task?.updateBy;
    if (!userId || userId === "system") {
      setUpdatedBy(userId === "system" ? "SYSTEM" : "-");
      return;
    }
    let disposed = false;
    setUpdatedBy("-");
    void getUsersByIds([userId])
      .then((users) => {
        if (!disposed) setUpdatedBy(users[0]?.realName || users[0]?.userName || "-");
      })
      .catch(() => {
        if (!disposed) setUpdatedBy("-");
      });
    return () => {
      disposed = true;
    };
  }, [task?.updateBy]);

  useEffect(() => {
    void loadInstances();
    return () => {
      listRequest.current += 1;
    };
  }, [loadInstances]);

  useEffect(() => {
    if (instancesLoading || executionId || instances.length === 0) return;
    searchParamsSetter.current(
      (previous) => {
        if (previous.has("executionId")) return previous;
        const next = new URLSearchParams(previous);
        next.set("executionId", instances[0].id);
        return next;
      },
      { replace: true },
    );
  }, [executionId, instances, instancesLoading]);

  useEffect(() => {
    const request = ++detailRequest.current;
    if (!executionId) {
      setSelected(undefined);
      setSelectedLoading(false);
      return;
    }

    let disposed = false;
    setSelected(undefined);
    setSelectedLoading(true);
    void getDataSyncInstance(executionId)
      .then((value) => {
        if (disposed || request !== detailRequest.current) return;
        if (value.taskId !== taskId) {
          searchParamsSetter.current(
            (previous) => {
              if (previous.get("executionId") !== executionId) return previous;
              const next = new URLSearchParams(previous);
              next.delete("executionId");
              return next;
            },
            { replace: true },
          );
          return;
        }
        setSelected(value);
      })
      .catch(() => {
        if (disposed || request !== detailRequest.current) return;
        searchParamsSetter.current(
          (previous) => {
            if (previous.get("executionId") !== executionId) return previous;
            const next = new URLSearchParams(previous);
            next.delete("executionId");
            return next;
          },
          { replace: true },
        );
      })
      .finally(() => {
        if (!disposed) setSelectedLoading(false);
      });

    return () => {
      disposed = true;
      detailRequest.current += 1;
    };
  }, [executionId, taskId]);

  useEffect(() => {
    if (!autoRefresh || (!hasActiveInstance && !hasEnabledSchedule) || selectedLoading) return;
    let disposed = false;
    let pending = false;
    const timer = window.setInterval(async () => {
      if (pending) return;
      pending = true;
      const request = ++detailRequest.current;
      try {
        await loadInstances();
        if (!executionId || disposed) return;
        const value = await getDataSyncInstance(executionId);
        if (disposed || request !== detailRequest.current) return;
        if (value.taskId === taskId) setSelected(value);
      } catch {
        // Keep the last observation; the HTTP boundary reports request failures.
      } finally {
        pending = false;
      }
    }, POLL_MILLIS);
    return () => {
      disposed = true;
      window.clearInterval(timer);
    };
  }, [
    autoRefresh,
    executionId,
    hasActiveInstance,
    hasEnabledSchedule,
    loadInstances,
    selectedLoading,
    taskId,
  ]);

  const refreshExecution = async () => {
    if (refreshing) return;
    const request = ++detailRequest.current;
    setRefreshing(true);
    try {
      await loadInstances();
      if (!executionId || request !== detailRequest.current) return;
      const value = await getDataSyncInstance(executionId);
      if (request !== detailRequest.current) return;
      if (value.taskId === taskId) setSelected(value);
    } finally {
      setRefreshing(false);
    }
  };

  const selectInstance = (id: string) => {
    setSearchParams((previous) => {
      const next = new URLSearchParams(previous);
      next.set("executionId", id);
      return next;
    });
  };

  const changeTab = (value: string) => {
    setSearchParams((previous) => {
      const next = new URLSearchParams(previous);
      if (value === "status") next.delete("tab");
      else next.set("tab", value);
      return next;
    });
  };

  const changePage = (page: number) => {
    setPageNo(page);
    setInstances([]);
    setInstancesLoading(true);
    setSelected(undefined);
    setSearchParams(
      (previous) => {
        const next = new URLSearchParams(previous);
        next.delete("executionId");
        return next;
      },
      { replace: true },
    );
  };

  const changeStatus = (value: DataSyncInstanceStatus | "ALL") => {
    setStatus(value);
    changePage(1);
  };

  const run = async () => {
    if (!taskId || running) return;
    setRunning(true);
    try {
      const submitted = await runDataSyncTask(taskId);
      toast.success("运行请求已提交");
      setStatus("ALL");
      setPageNo(1);
      setInstances([]);
      setInstancesLoading(true);
      setSearchParams((previous) => {
        const next = new URLSearchParams(previous);
        next.set("executionId", submitted.instanceId);
        next.delete("tab");
        return next;
      });
      if (pageNo === 1 && status === "ALL") await loadInstances();
    } finally {
      setRunning(false);
    }
  };

  const cancel = async () => {
    if (!selectedInstance || !canCancelTaskInstance(selectedInstance.status) || canceling) return;
    const request = ++detailRequest.current;
    setCanceling(true);
    try {
      await cancelDataSyncInstance(selectedInstance.id);
      toast.success("已发送停止请求");
      const value = await getDataSyncInstance(selectedInstance.id);
      if (request === detailRequest.current) setSelected(value);
      await loadInstances();
    } finally {
      setCanceling(false);
    }
  };

  const copyTaskId = async () => {
    if (!task) return;
    try {
      await navigator.clipboard.writeText(task.id);
      toast.success("已复制任务 ID");
    } catch {
      toast.error("复制失败");
    }
  };

  if (taskLoading || !task) {
    return (
      <div className="flex h-full items-center justify-center bg-white">
        <Spinner size="large" label="加载任务详情" />
      </div>
    );
  }

  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div
      className={
        localScroll
          ? "flex h-full min-h-0 flex-col overflow-hidden bg-white text-[#242731] md:flex-row"
          : "flex min-h-full flex-col bg-white text-[#242731] md:flex-row"
      }
    >
      <aside className="flex max-h-64 w-full shrink-0 flex-col overflow-hidden border-b border-[#e6e8eb] bg-white md:max-h-none md:w-[300px] md:border-r md:border-b-0">
        <div className="shrink-0 border-b border-[#eef0f3] px-4 py-3">
          <div className="flex items-center justify-between">
            <span className="text-sm font-semibold text-[#242731]">执行记录</span>
            <Button
              size="small"
              variant="ghost"
              className="sync-execution-icon-button h-7 w-7"
              aria-label="刷新执行记录"
              title="刷新"
              disabled={instancesLoading}
              onClick={() => void loadInstances()}
            >
              <RefreshCw size={14} />
            </Button>
          </div>
          <Select
            size="small"
            items={statusItems}
            value={status}
            onValueChange={(value) =>
              changeStatus(String(value || "ALL") as DataSyncInstanceStatus | "ALL")
            }
          >
            <SelectTrigger variant="outlined" className="mt-3">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {Object.entries(statusItems).map(([value, label]) => (
                <SelectItem key={value} value={value}>
                  <SelectItemText>{label}</SelectItemText>
                  <SelectItemIndicator />
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto p-2">
          {instancesLoading && instances.length === 0 ? (
            <div className="flex min-h-32 items-center justify-center">
              <Spinner size="medium" label="加载执行记录" />
            </div>
          ) : instances.length === 0 ? (
            <div className="flex min-h-32 items-center justify-center text-sm text-[#98a2b3]">
              暂无执行记录
            </div>
          ) : (
            <div className="space-y-1">
              {instances.map((instance) => {
                const isSelected = executionId === instance.id;
                return (
                  <button
                    key={instance.id}
                    type="button"
                    aria-current={isSelected ? "true" : undefined}
                    className={
                      isSelected
                        ? "w-full cursor-pointer rounded-md bg-[var(--yak-color-primary-subtle)] px-3 py-2.5 text-left"
                        : "w-full cursor-pointer rounded-md bg-white px-3 py-2.5 text-left hover:bg-[#f6f6f6]"
                    }
                    onClick={() => selectInstance(instance.id)}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <ExecutionStatusIcon
                        status={instance.status}
                        realtime={instance.syncType === "REALTIME"}
                      />
                      <span className="truncate text-xs text-[#98a2b3]">
                        {instance.startTime || instance.createTime || "-"}
                      </span>
                    </div>
                    <div className="mt-2 truncate text-xs text-[#667085]">
                      {dataSyncTriggerText(instance.triggerType)} · v{instance.taskVersion} ·{" "}
                      {dataSyncDurationText(instance)}
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {total > PAGE_SIZE ? (
          <div className="flex shrink-0 items-center justify-between border-t border-[#eef0f3] px-3 py-2">
            <Button
              size="small"
              variant="ghost"
              disabled={pageNo <= 1 || instancesLoading}
              onClick={() => changePage(pageNo - 1)}
            >
              上一页
            </Button>
            <span className="text-xs text-[#98a2b3]">
              {pageNo} / {totalPages}
            </span>
            <Button
              size="small"
              variant="ghost"
              disabled={pageNo >= totalPages || instancesLoading}
              onClick={() => changePage(pageNo + 1)}
            >
              下一页
            </Button>
          </div>
        ) : null}
      </aside>

      <div className="flex min-h-0 min-w-0 flex-1 flex-col">
        <PageHeader
          title={task.name}
          bordered
          className="shrink-0 bg-white px-6 max-md:px-4"
          extra={
            <>
              {!realtime ? (
                <Button
                  size="small"
                  onClick={() => {
                    setHistoryScheduleId(undefined);
                    setScheduleOpen(true);
                  }}
                >
                  调度
                </Button>
              ) : null}
              {task.status === "PUBLISHED" ? (
                <Button size="small" loading={running} onClick={() => void run()}>
                  运行
                </Button>
              ) : (
                <Button size="small" onClick={() => navigate(basePath + "/" + task.id + "/edit")}>
                  编辑
                </Button>
              )}
              <Button size="small" onClick={() => navigate(basePath)}>
                返回任务列表
              </Button>
            </>
          }
        />

        <div className="min-h-0 flex-1 overflow-y-auto px-6 pb-8 pt-5 max-md:px-4">
          <div className="space-y-8">
            <section aria-labelledby="task-basic-info">
              <h2 id="task-basic-info" className="mb-5 text-sm font-semibold text-[#344054]">
                基本信息
              </h2>
              <div className="grid grid-cols-2 gap-x-10 gap-y-5 max-lg:grid-cols-1">
                <BasicInfoField label="任务 ID">
                  <div className="flex min-w-0 items-center gap-1.5">
                    <span className="truncate" title={task.id}>
                      {task.id}
                    </span>
                    <Button
                      size="small"
                      variant="ghost"
                      className="sync-execution-icon-button h-6 w-6 shrink-0"
                      aria-label="复制任务 ID"
                      onClick={() => void copyTaskId()}
                    >
                      <Copy size={13} />
                    </Button>
                  </div>
                </BasicInfoField>
                <BasicInfoField label="修改人">{updatedBy}</BasicInfoField>
                <BasicInfoField label="数据源">
                  <div className="flex min-w-0 flex-wrap items-center gap-2">
                    <DatabaseIcons dbType={dataSources.source?.dbType} width="16px" height="16px" />
                    <span>{dataSources.source?.name || "-"}</span>
                    <ArrowRight size={14} className="shrink-0 text-[#98a2b3]" />
                    <DatabaseIcons dbType={dataSources.target?.dbType} width="16px" height="16px" />
                    <span>{dataSources.target?.name || "-"}</span>
                  </div>
                </BasicInfoField>
                <BasicInfoField label="同步方案">
                  {solutionSummary(task.solutionType, task.steps)}
                </BasicInfoField>
                <BasicInfoField label="创建时间">{task.createTime || "-"}</BasicInfoField>
                <BasicInfoField label="更新时间">{task.updateTime || "-"}</BasicInfoField>
                <BasicInfoField label="当前版本">v{task.definitionVersion}</BasicInfoField>
                {task.remark ? <BasicInfoField label="备注">{task.remark}</BasicInfoField> : null}
              </div>
            </section>

            <Tabs value={activeTab} onValueChange={changeTab}>
              <TabsList>
                <TabsTab value="status">执行状态</TabsTab>
                <TabsTab value="log">运行日志</TabsTab>
              </TabsList>

              <TabsPanel value="status" className="pt-4">
                {selectedInstance ? (
                  <DataSyncExecutionDetailContent
                    record={selectedInstance}
                    onViewScheduleHistory={() => {
                      setHistoryScheduleId(selectedInstance.scheduleId || undefined);
                      setScheduleOpen(true);
                    }}
                    realtime={selectedInstance.syncType === "REALTIME"}
                    controls={
                      <>
                        <label className="flex items-center gap-2 text-xs text-[#667085]">
                          自动刷新
                          <Switch
                            size="small"
                            checked={autoRefresh}
                            onCheckedChange={setAutoRefresh}
                          />
                        </label>
                        <Button
                          size="small"
                          className="sync-execution-icon-button h-7 w-7"
                          aria-label="刷新执行状态"
                          title="刷新"
                          loading={refreshing}
                          onClick={() => void refreshExecution()}
                        >
                          <RefreshCw size={14} />
                        </Button>
                        {canCancelTaskInstance(selectedInstance.status) ? (
                          <Button
                            size="small"
                            variant="danger"
                            loading={canceling}
                            onClick={() => void cancel()}
                          >
                            停止
                          </Button>
                        ) : null}
                      </>
                    }
                  />
                ) : selectedLoading ? (
                  <div className="flex min-h-40 items-center justify-center">
                    <Spinner size="medium" label="加载实例详情" />
                  </div>
                ) : (
                  <div className="py-12 text-center text-sm text-[#98a2b3]">
                    {instances.length === 0 ? "暂无执行记录" : "选择左侧执行记录"}
                  </div>
                )}
              </TabsPanel>

              <TabsPanel value="log" className="pt-4">
                {selectedInstance && activeTab === "log" ? (
                  <TaskInstanceLogPanel
                    key={selectedInstance.id}
                    instanceId={selectedInstance.id}
                    active={isActiveDataSyncInstance(selectedInstance)}
                    embedded
                  />
                ) : selectedLoading ? (
                  <div className="flex min-h-40 items-center justify-center">
                    <Spinner size="medium" label="加载实例详情" />
                  </div>
                ) : !selectedInstance ? (
                  <div className="py-12 text-center text-sm text-[#98a2b3]">选择左侧执行记录</div>
                ) : null}
              </TabsPanel>
            </Tabs>
          </div>
        </div>
      </div>
      {!realtime ? (
        <ScheduleDialog
          targetId={task.id}
          published={task.status === "PUBLISHED"}
          open={scheduleOpen}
          historyScheduleId={historyScheduleId}
          onClose={() => setScheduleOpen(false)}
          onChanged={schedules.refresh}
          onViewInstance={(id) => {
            setStatus("ALL");
            setPageNo(1);
            setSearchParams((previous) => {
              const next = new URLSearchParams(previous);
              next.set("executionId", id);
              next.delete("tab");
              return next;
            });
            void loadInstances();
          }}
        />
      ) : null}
    </div>
  );
}

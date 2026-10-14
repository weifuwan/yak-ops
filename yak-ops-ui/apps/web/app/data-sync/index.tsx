import {
  Button,
  Input,
  Modal,
  PageHeader,
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
import { Plus } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";

import { ScheduleDialog } from "@/app/schedule/schedule-dialog";
import { TaskScheduleCell } from "@/app/schedule/task-schedule-cell";
import { useTaskSchedules } from "@/app/schedule/use-task-schedules";
import { scheduleErrorMessage, setScheduleEnabled } from "@/service/schedule";
import { listDataSources, type DataSourceRecord } from "@/service/datasource";
import { getUsersByIds, type UserRecord } from "@/service/user";
import {
  cancelDataSyncInstance,
  deleteDataSyncTask,
  listDataSyncTasks,
  publishDataSyncTask,
  runDataSyncTask,
  unpublishDataSyncTask,
  type DataSyncTaskRecord,
  type DataSyncTaskStatus,
  type DataSyncSolutionType,
} from "@/service/data-sync";

import { ExecutionProgressBar } from "@/app/data-sync/execution-progress-bar";
import { ExecutionStages } from "./execution-stages";
import { readExecutionSummary } from "@/service/data-sync/report";
import { CreateSyncTaskDialog } from "./create-dialog";
import { SOLUTION_LABELS, solutionSummary } from "./solution-presentation";
import { canCancelTaskInstance } from "@/service/data-sync/execution";
import type { TaskInstanceRecord } from "@/service/task-execution";
import { TaskRoute } from "@/app/data-sync/task-route";
import {
  DATA_SYNC_TASK_STATUS_ITEMS,
  DataSyncTaskLifecycleActions,
  DataSyncTaskStatusBadge,
  useActiveTaskInstances,
} from "@/app/data-sync/task-lifecycle";

const PAGE_SIZE = 20;

export function DataSyncPage() {
  const navigate = useNavigate();
  const location = useLocation();

  const [records, setRecords] = useState<DataSyncTaskRecord[]>([]);
  const [updateUsers, setUpdateUsers] = useState<UserRecord[]>([]);
  const [dataSources, setDataSources] = useState<DataSourceRecord[]>([]);
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [taskStatus, setTaskStatus] = useState<DataSyncTaskStatus | "ALL">("ALL");
  const [actionKey, setActionKey] = useState<string>();
  const [createOpen, setCreateOpen] = useState(
    new URLSearchParams(location.search).get("create") === "1",
  );
  const [solutionType, setSolutionType] = useState<DataSyncSolutionType | "ALL">(() => {
    const initial = new URLSearchParams(location.search).get("solutionType");
    if (
      initial === "SINGLE_TABLE_OFFLINE" ||
      initial === "SINGLE_TABLE_REALTIME" ||
      initial === "DATABASE_OFFLINE" ||
      initial === "DATABASE_REALTIME"
    )
      return initial;
    return "ALL";
  });
  const [pendingDelete, setPendingDelete] = useState<DataSyncTaskRecord>();
  const [deleting, setDeleting] = useState(false);
  const [scheduleTask, setScheduleTask] = useState<DataSyncTaskRecord>();
  const schedules = useTaskSchedules(
    records.filter((record) => record.syncType === "OFFLINE").map((record) => record.id),
  );
  const scheduleSession = useRef<AbortController>();
  useEffect(() => {
    const controller = new AbortController();
    scheduleSession.current = controller;
    return () => controller.abort();
  }, []);

  const toggleSchedule = async (task: DataSyncTaskRecord, enabled: boolean) => {
    const schedule = schedules.records.get(task.id);
    const signal = scheduleSession.current?.signal;
    if (!schedule || !signal || signal.aborted || actionKey) return;
    setActionKey(`${task.id}:schedule`);
    try {
      await setScheduleEnabled(schedule.id, schedule.revision, enabled, signal);
      if (!signal.aborted) toast.success(enabled ? "调度已启用" : "调度已停止");
    } catch (failure) {
      if (!signal.aborted) toast.error(scheduleErrorMessage(failure));
    } finally {
      if (!signal.aborted) {
        setActionKey(undefined);
        schedules.refresh();
      }
    }
  };

  const taskIds = useMemo(() => records.map((record) => record.id), [records]);
  const {
    activeByTask,
    latestByTask,
    refresh: refreshActiveInstances,
  } = useActiveTaskInstances(true, taskIds);

  const dataSourceMap = useMemo(
    () => new Map(dataSources.flatMap((item) => (item.id ? [[item.id, item] as const] : []))),
    [dataSources],
  );
  const updateUserMap = useMemo(
    () => new Map(updateUsers.map((user) => [user.id, user] as const)),
    [updateUsers],
  );
  const loadDataSources = useCallback(async () => {
    try {
      const result = await listDataSources({ pageNo: 1, pageSize: 200 });
      setDataSources(result?.bizData || []);
    } catch {
      // Keep existing endpoint labels while transport reports an unavailable catalog.
    }
  }, []);

  useEffect(() => {
    void loadDataSources();
  }, [loadDataSources]);

  const taskRequest = useRef<AbortController>();
  const loadTasks = useCallback(async () => {
    taskRequest.current?.abort();
    const controller = new AbortController();
    taskRequest.current = controller;
    setLoading(true);
    try {
      const result = await listDataSyncTasks(
        {
          pageNo,
          pageSize: PAGE_SIZE,
          keyword: keyword.trim() || undefined,
          solutionType: solutionType === "ALL" ? undefined : solutionType,
          status: taskStatus === "ALL" ? undefined : taskStatus,
        },
        controller.signal,
      );
      if (controller.signal.aborted) return;
      setRecords(result?.bizData || []);
      setTotal(result?.pagination?.total || 0);
    } catch {
      // The previous page is retained when a request fails; no successful empty page is fabricated.
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }, [keyword, pageNo, taskStatus, solutionType]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadTasks(), keyword.trim() ? 250 : 0);
    return () => {
      window.clearTimeout(timer);
      taskRequest.current?.abort();
    };
  }, [keyword, loadTasks]);

  useEffect(() => {
    const userIds = [
      ...new Set(
        records
          .map((record) => record.updateBy)
          .filter((userId): userId is string => Boolean(userId && userId !== "system")),
      ),
    ];
    if (userIds.length === 0) {
      setUpdateUsers([]);
      return;
    }

    let active = true;
    void getUsersByIds(userIds)
      .then((users) => {
        if (active) setUpdateUsers(users);
      })
      .catch(() => {
        if (active) setUpdateUsers([]);
      });
    return () => {
      active = false;
    };
  }, [records]);

  const updateOperatorText = (record: DataSyncTaskRecord) => {
    if (!record.updateBy) return "-";
    if (record.updateBy === "system") return "SYSTEM";
    const user = updateUserMap.get(record.updateBy);
    return user?.realName || user?.userName || "未知用户";
  };

  const runTask = async (record: DataSyncTaskRecord) => {
    if (actionKey) return;
    setActionKey(`${record.id}:run`);
    try {
      await runDataSyncTask(record.id);
      toast.success("运行请求已提交");
      schedules.refresh();
      await Promise.all([loadTasks(), refreshActiveInstances()]);
    } finally {
      setActionKey(undefined);
    }
  };

  const stopTask = async (record: DataSyncTaskRecord, instance: TaskInstanceRecord) => {
    if (actionKey || !canCancelTaskInstance(instance.status)) return;
    setActionKey(`${record.id}:stop`);
    try {
      await cancelDataSyncInstance(instance.id);
      toast.success("已发送停止请求");
      await Promise.all([loadTasks(), refreshActiveInstances()]);
    } finally {
      setActionKey(undefined);
    }
  };

  const publishTask = async (record: DataSyncTaskRecord) => {
    if (actionKey) return;
    setActionKey(`${record.id}:publish`);
    try {
      await publishDataSyncTask(record.id);
      toast.success("同步任务已上线");
      schedules.refresh();
      await Promise.all([loadTasks(), refreshActiveInstances()]);
    } finally {
      setActionKey(undefined);
    }
  };

  const unpublishTask = async (record: DataSyncTaskRecord) => {
    if (actionKey) return;
    setActionKey(`${record.id}:unpublish`);
    try {
      await unpublishDataSyncTask(record.id);
      toast.success("同步任务已下线");
      schedules.refresh();
      await Promise.all([loadTasks(), refreshActiveInstances()]);
    } finally {
      setActionKey(undefined);
    }
  };

  const columns: TableColumns<DataSyncTaskRecord> = [
    {
      key: "name",
      title: "任务名称",
      minWidth: 180,
      render: (_value, record) => (
        <div className="min-w-0">
          <div className="truncate text-[13px] font-medium text-[#252832]">{record.name}</div>
          <div className="mt-0.5 text-xs text-[#98a2b3]">v{record.definitionVersion}</div>
        </div>
      ),
    },
    {
      key: "route",
      title: "同步链路",
      minWidth: 300,
      render: (_value, record) => (
        <TaskRoute
          source={dataSourceMap.get(record.sourceDataSourceId)}
          target={dataSourceMap.get(record.targetDataSourceId)}
        />
      ),
    },
    {
      key: "solution",
      title: "同步方案",
      minWidth: 205,
      render: (_value, record) => (
        <span className="text-xs text-[#475467]">
          {solutionSummary(record.solutionType, record.steps)}
        </span>
      ),
    },
    {
      key: "schedule",
      title: "调度",
      width: 230,
      render: (_value, record) =>
        record.syncType === "OFFLINE" ? (
          <TaskScheduleCell
            schedule={schedules.records.get(record.id)}
            name={record.name}
            published={record.status === "PUBLISHED"}
            loading={schedules.loading}
            error={schedules.error}
            pending={Boolean(actionKey)}
            onConfigure={() => setScheduleTask(record)}
            onToggle={(enabled) => void toggleSchedule(record, enabled)}
          />
        ) : (
          <span className="text-[#98a2b3]">—</span>
        ),
    },
    {
      key: "status",
      title: "状态",
      width: 100,
      render: (_value, record) => <DataSyncTaskStatusBadge status={record.status} />,
    },
    {
      key: "execution",
      title: "执行概况",
      width: 190,
      render: (_value, record) => {
        const instance = latestByTask.get(record.id);
        if (!instance) {
          return <ExecutionProgressBar realtime={record.syncType === "REALTIME"} />;
        }
        return (
          <button
            type="button"
            className="block w-full cursor-pointer text-left"
            title="查看本次执行"
            onClick={() => navigate(`/data-sync/${record.id}/detail?executionId=${instance.id}`)}
          >
            {record.syncType === "REALTIME" ? (
              <ExecutionStages
                compact
                status={instance.status}
                summary={readExecutionSummary(instance.executionReport)}
              />
            ) : (
              <ExecutionProgressBar
                status={instance.status}
                percent={instance.progressPercent}
                realtime={false}
              />
            )}
          </button>
        );
      },
    },
    {
      key: "updated",
      title: "更新信息",
      width: 180,
      render: (_value, record) => (
        <div className="min-w-0">
          <div className="truncate text-xs font-medium text-[#475467]" title={record.updateBy}>
            {updateOperatorText(record)}
          </div>
          <div className="mt-0.5 text-xs text-[#98a2b3]">{record.updateTime || "-"}</div>
        </div>
      ),
    },
    {
      key: "actions",
      title: "操作",
      width: 320,
      fixed: "right",
      align: "center",
      render: (_value, record) => (
        <DataSyncTaskLifecycleActions
          record={record}
          activeInstance={activeByTask.get(record.id)}
          actionKey={actionKey}
          runtimeAction={{
            label: activeByTask.has(record.id)
              ? "停止"
              : record.syncType === "REALTIME"
                ? "启动"
                : "运行",
            className: activeByTask.has(record.id)
              ? "text-[#d92d20]"
              : "text-[var(--yak-color-primary)]",
            loading: actionKey === `${record.id}:run` || actionKey === `${record.id}:stop`,
            disabled: activeByTask.has(record.id)
              ? !canCancelTaskInstance(activeByTask.get(record.id)!.status)
              : record.status !== "PUBLISHED",
            onClick: () => {
              const active = activeByTask.get(record.id);
              if (active) void stopTask(record, active);
              else void runTask(record);
            },
          }}
          onPublish={(value) => void publishTask(value)}
          onUnpublish={(value) => void unpublishTask(value)}
          onEdit={(value) => navigate(`/data-sync/${value.id}/edit`)}
          onDetail={(value) => navigate(`/data-sync/${value.id}/detail`)}
          onDelete={setPendingDelete}
        />
      ),
    },
  ];

  const confirmDelete = async () => {
    if (!pendingDelete || deleting) return;
    setDeleting(true);
    try {
      await deleteDataSyncTask(pendingDelete.id);
      toast.success("同步任务已删除");
      setPendingDelete(undefined);
      schedules.refresh();
      await loadTasks();
    } finally {
      setDeleting(false);
    }
  };

  return (
    <>
      <div className="flex min-h-full flex-col bg-[#f6f6f6] text-[#242731]">
        <PageHeader title="数据同步" bordered className="bg-white px-6 max-md:px-4" />

        <div className="flex min-h-0 flex-1 px-6 pb-4 pt-5 max-md:px-4">
          <div className="flex min-h-0 flex-1 flex-col bg-white p-4">
            <div className="flex shrink-0 flex-wrap items-center gap-2">
              <Button
                size="small"
                variant="primary"
                onClick={() => {
                  setCreateOpen(true);
                }}
              >
                <Plus size={14} />
                新建同步任务
              </Button>
              <div className="w-[300px]">
                <Input
                  size="small"
                  variant="outlined"
                  value={keyword}
                  placeholder="搜索任务名称或表名"
                  onChange={(event) => {
                    setKeyword(event.target.value);
                    setPageNo(1);
                  }}
                />
              </div>
              <div className="w-[165px]">
                <Select
                  size="small"
                  items={{ ALL: "全部同步类型", ...SOLUTION_LABELS }}
                  value={solutionType}
                  onValueChange={(value) => {
                    setSolutionType(String(value) as DataSyncSolutionType | "ALL");
                    setPageNo(1);
                  }}
                >
                  <SelectTrigger variant="outlined">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ALL">
                      <SelectItemText>全部同步类型</SelectItemText>
                      <SelectItemIndicator />
                    </SelectItem>
                    {Object.entries(SOLUTION_LABELS).map(([value, label]) => (
                      <SelectItem key={value} value={value}>
                        <SelectItemText>{label}</SelectItemText>
                        <SelectItemIndicator />
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="w-[150px]">
                <Select
                  size="small"
                  items={DATA_SYNC_TASK_STATUS_ITEMS}
                  value={taskStatus}
                  onValueChange={(value) => {
                    setTaskStatus(String(value || "ALL") as DataSyncTaskStatus | "ALL");
                    setPageNo(1);
                  }}
                >
                  <SelectTrigger variant="outlined">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ALL">
                      <SelectItemText>全部状态</SelectItemText>
                      <SelectItemIndicator />
                    </SelectItem>
                    <SelectItem value="PUBLISHED">
                      <SelectItemText>已上线</SelectItemText>
                      <SelectItemIndicator />
                    </SelectItem>
                    <SelectItem value="UNPUBLISHED">
                      <SelectItemText>已下线</SelectItemText>
                      <SelectItemIndicator />
                    </SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="mt-4 min-h-0 flex-1">
              <Table<DataSyncTaskRecord>
                className="min-h-full"
                columns={columns}
                dataSource={records}
                rowKey="id"
                loading={loading}
                bordered
                size="medium"
                scroll={{ x: 1480 }}
                emptyText="还没有同步任务"
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
        </div>
      </div>

      {scheduleTask ? (
        <ScheduleDialog
          targetId={scheduleTask.id}
          published={
            (records.find((record) => record.id === scheduleTask.id) || scheduleTask).status ===
            "PUBLISHED"
          }
          open
          onClose={() => setScheduleTask(undefined)}
          onChanged={schedules.refresh}
          onViewInstance={(id) =>
            navigate(`/data-sync/${scheduleTask.id}/detail?executionId=${encodeURIComponent(id)}`)
          }
        />
      ) : null}
      {createOpen ? (
        <CreateSyncTaskDialog
          onClose={() => {
            setCreateOpen(false);
            if (new URLSearchParams(location.search).has("create"))
              navigate("/data-sync", { replace: true });
          }}
        />
      ) : null}

      <Modal
        open={Boolean(pendingDelete)}
        centered
        width={420}
        title="删除同步任务"
        onClose={() => {
          if (!deleting) setPendingDelete(undefined);
        }}
        footer={
          <>
            <Button size="small" disabled={deleting} onClick={() => setPendingDelete(undefined)}>
              取消
            </Button>
            <Button
              size="small"
              variant="danger"
              loading={deleting}
              onClick={() => void confirmDelete()}
            >
              删除
            </Button>
          </>
        }
      >
        <div className="text-sm leading-6 text-[#667085]">
          确定删除任务“{pendingDelete?.name}”吗？历史任务实例不会在这里删除。
        </div>
      </Modal>
    </>
  );
}

export default DataSyncPage;

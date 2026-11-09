import { Button, Skeleton, Table, type TableColumns } from "@yak-ops/yak-ui";
import { useNavigate } from "react-router-dom";

import { ExecutionProgressBar } from "../execution/execution-progress-bar";
import { ExecutionStages } from "../execution/execution-stages";
import { TaskRoute } from "./task-route";
import { DataSyncTaskLifecycleActions, DataSyncTaskStatusBadge } from "./task-lifecycle";
import { readExecutionSummary } from "@/service/data-sync/report";
import { canCancelTaskInstance, canManageTaskDefinition } from "@/service/data-sync/execution";
import {
  canStopWorkflow, isWorkflowActive, workflowStatusText,
  type DataSyncWorkflowInstance,
} from "@/service/data-sync";
import type { DataSourceRecord } from "@/service/datasource";
import type { UserRecord } from "@/service/user";
import type {
  DataSyncTaskListRecord,
  DataSyncTaskRecord,
  DataSyncUnreadableTaskRecord,
} from "@/service/data-sync";
import type { TaskInstanceRecord } from "@/service/task-execution/types";
import type { TaskCommand } from "./use-task-actions";

const PAGE_SIZE = 20;

function isUnreadable(record: DataSyncTaskListRecord): record is DataSyncUnreadableTaskRecord {
  return "configurationError" in record;
}

function syncPlanText(record: DataSyncTaskRecord): string {
  const scope =
    record.scope === "DATABASE"
      ? "整库同步"
      : record.scope === "MULTI_TABLE"
        ? "多表同步"
        : "单表同步";
  const full = record.steps.includes("FULL");
  const incremental = record.steps.includes("INCREMENTAL");
  const mode = full && incremental ? "全量 + 增量" : full ? "全量" : "增量";
  return `MySQL · ${scope} · ${mode}`;
}

interface Data {
  dataSourceMap: Map<string, DataSourceRecord>;
  dataSourcesLoading: boolean;
  updateUserMap: Map<string, UserRecord>;
  updateUsersLoading: boolean;
  latestByTask: Map<string, TaskInstanceRecord>;
  activeByTask: Map<string, TaskInstanceRecord>;
  workflowsByTask: Map<string, DataSyncWorkflowInstance | undefined>;
  submittedByTask: Record<string, number>;
  stateReadyByTask: Map<string, boolean>;
  stateLoadingByTask: Map<string, boolean>;
  stateErrorByTask: Set<string>;
  actionKey?: string;
}

interface Actions {
  command: (value: TaskCommand) => void;
  alert: (task: DataSyncTaskRecord) => void;
  delete: (task: DataSyncTaskRecord) => void;
  page: (page: number) => void;
}

interface Props {
  records: DataSyncTaskListRecord[];
  pageNo: number;
  total: number;
  loading: boolean;
  failed: boolean;
  data: Data;
  actions: Actions;
}

export function TaskTable({ records, pageNo, total, loading, failed, data, actions }: Props) {
  const navigate = useNavigate();
  const {
    dataSourceMap,
    dataSourcesLoading,
    updateUserMap,
    updateUsersLoading,
    latestByTask,
    activeByTask,
    workflowsByTask,
    submittedByTask,
    stateReadyByTask,
    stateLoadingByTask,
    stateErrorByTask,
    actionKey,
  } = data;

  const updateOperatorText = (record: DataSyncTaskListRecord) => {
    if (!record.updateBy) return "-";
    if (record.updateBy === "system") return "SYSTEM";
    const user = updateUserMap.get(record.updateBy);
    return user?.realName || user?.userName || "未知用户";
  };
  const workflowFor = (record: DataSyncTaskRecord) => workflowsByTask.get(record.id);
  const awaitingSubmission = (record: DataSyncTaskRecord) => {
    const workflow = workflowFor(record);
    const submitted = submittedByTask[record.id];
    return Boolean(submitted && (!workflow || workflow.id < submitted));
  };
  const workflowActive = (record: DataSyncTaskRecord) => {
    const workflow = workflowFor(record);
    return awaitingSubmission(record) || Boolean(workflow && isWorkflowActive(workflow.state));
  };
  const canManageTask = (record: DataSyncTaskRecord) =>
    !workflowActive(record) &&
    canManageTaskDefinition(
      stateReadyByTask.get(record.id) === true,
      activeByTask.get(record.id),
    );
  const runTask = (task: DataSyncTaskRecord) => actions.command({ kind: "run", task });
  const stopTask = (task: DataSyncTaskRecord, workflowInstanceId: number) =>
    actions.command({ kind: "stop", task, workflowInstanceId });
  const publishTask = (task: DataSyncTaskRecord) => actions.command({ kind: "publish", task });
  const unpublishTask = (task: DataSyncTaskRecord) => actions.command({ kind: "unpublish", task });

  const columns: TableColumns<DataSyncTaskListRecord> = [
    {
      key: "name",
      title: "任务名称",
      minWidth: 240,
      render: (_value, record) => (
        <div className="min-w-0">
          <div className="truncate text-[13px] font-medium text-[var(--yak-components-panel-text)]">
            {record.name}
          </div>
          <div className="mt-0.5 text-xs text-[var(--yak-components-muted-text)]">
            最新 V{record.definitionVersion} ·{" "}
            {record.publishedVersion ? `已发布 V${record.publishedVersion}` : "未发布"}
          </div>
        </div>
      ),
    },
    {
      key: "route",
      title: "同步链路",
      minWidth: 320,
      render: (_value, record) => {
        if (isUnreadable(record)) {
          return <span className="text-[var(--yak-components-muted-text)]">—</span>;
        }
        if (
          dataSourcesLoading &&
          (!dataSourceMap.has(record.sourceDataSourceId) ||
            !dataSourceMap.has(record.targetDataSourceId))
        ) {
          return <Skeleton width="90%" height={16} />;
        }
        return (
          <TaskRoute
            source={dataSourceMap.get(record.sourceDataSourceId)}
            target={dataSourceMap.get(record.targetDataSourceId)}
          />
        );
      },
    },
    {
      key: "product",
      title: "同步方案",
      minWidth: 220,
      render: (_value, record) =>
        isUnreadable(record) ? (
          <span
            className="block truncate text-xs text-[var(--yak-components-danger-text)]"
            title={record.configurationError}
          >
            配置异常：{record.configurationError}
          </span>
        ) : (
          <div className="text-xs text-[var(--yak-components-tab-text-hover)]">
            <div>{syncPlanText(record)}</div>
            <div className="mt-0.5 text-[var(--yak-components-muted-text)]">
              {record.scope === "DATABASE" ? "整库" : record.tableMappings.length + " 张表"}
            </div>
          </div>
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
      width: 180,
      render: (_value, record) => {
        if (isUnreadable(record)) {
          return <span className="text-[var(--yak-components-muted-text)]">—</span>;
        }
        const workflow = workflowFor(record);
        const waiting = awaitingSubmission(record);
        const latest = latestByTask.get(record.id);
        const instance = waiting || (workflow && latest?.workflowInstanceId !== workflow.id)
          ? undefined
          : latest;
        const workflowId = waiting ? submittedByTask[record.id] : workflow?.id;
        const statusText = waiting
          ? "启动中"
          : instance?.status === "RUNNING_EXECUTION"
            ? "运行中"
            : workflow
              ? workflowStatusText(workflow.state)
              : "暂无执行";
        if (!instance) {
          if (!workflowId && stateLoadingByTask.get(record.id)) {
            return <Skeleton width="100%" height={22} />;
          }
          if (!workflowId && stateErrorByTask.has(record.id)) {
            return (
              <span className="text-xs text-[var(--yak-components-danger-text)]">
                状态获取失败
              </span>
            );
          }
          if (!workflowId) return <span className="text-xs text-[var(--yak-components-muted-text)]">暂无执行</span>;
          return (
            <button
              type="button"
              className="block w-full cursor-pointer text-left text-xs text-[var(--yak-components-tab-text-hover)]"
              onClick={() => navigate(`/data-sync/${record.id}/detail?workflowInstanceId=${workflowId}`)}
            >
              <div>{statusText}</div>
              <div className="mt-1 text-[var(--yak-components-muted-text)]">
                工作流 #{workflowId} · {workflow && !isWorkflowActive(workflow.state) ? "未进入节点执行" : "等待执行节点"}
              </div>
            </button>
          );
        }
        const summary = readExecutionSummary(instance.executionReport);
        return (
          <button
            type="button"
            className="block w-full cursor-pointer text-left"
            title="查看本次执行"
            onClick={() => navigate(`/data-sync/${record.id}/detail?executionId=${instance.id}`)}
          >
            <div className="mb-1 text-xs text-[var(--yak-components-tab-text-hover)]">{statusText}</div>
            {summary ? (
              <ExecutionStages compact status={instance.status} summary={summary} />
            ) : (
              <ExecutionProgressBar
                status={instance.status}
                percent={instance.progressPercent}
                realtime={record.syncType === "REALTIME"}
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
          <div
            className="truncate text-xs font-medium text-[var(--yak-components-tab-text-hover)]"
            title={record.updateBy}
          >
            {updateUsersLoading &&
            record.updateBy &&
            record.updateBy !== "system" &&
            !updateUserMap.has(record.updateBy) ? (
              <Skeleton width={90} height={12} />
            ) : (
              updateOperatorText(record)
            )}
          </div>
          <div className="mt-0.5 text-xs text-[var(--yak-components-muted-text)]">
            {record.updateTime || "-"}
          </div>
        </div>
      ),
    },
    {
      key: "actions",
      title: "操作",
      width: 210,
      fixed: "right",
      align: "center",
      render: (_value, record) =>
        isUnreadable(record) ? (
          <Button
            size="sm"
            variant="ghost"
            onClick={() => navigate(`/data-sync/${record.id}/edit`)}
          >
            查看
          </Button>
        ) : (
          <DataSyncTaskLifecycleActions
            record={record}
            activeInstance={activeByTask.get(record.id)}
            workflowActive={workflowActive(record)}
            instanceStateReady={stateReadyByTask.get(record.id) === true}
            actionKey={actionKey}
            runtimeAction={{
              label: workflowActive(record)
                ? awaitingSubmission(record)
                  ? "启动中"
                  : workflowFor(record)?.state === "SERIAL_WAIT"
                    ? "取消"
                    : workflowFor(record)?.state === "READY_STOP"
                      ? "停止中"
                      : workflowFor(record)?.state === "PAUSE"
                        ? "已暂停"
                        : canStopWorkflow(workflowFor(record)!.state)
                          ? "停止"
                          : workflowStatusText(workflowFor(record)!.state)
                : activeByTask.has(record.id)
                  ? "停止"
                  : record.syncType === "REALTIME"
                    ? "启动"
                    : "运行",
              className: workflowActive(record) || activeByTask.has(record.id)
                ? "text-[var(--yak-components-danger-text)]"
                : "text-[var(--yak-color-primary)]",
              loading: actionKey === `${record.id}:run` || actionKey === `${record.id}:stop`,
              disabled: workflowActive(record)
                ? awaitingSubmission(record) ||
                  !workflowFor(record) ||
                  !canStopWorkflow(workflowFor(record)!.state)
                : activeByTask.has(record.id)
                  ? !canCancelTaskInstance(activeByTask.get(record.id)!.status)
                  : record.status !== "ONLINE",
              onClick: () => {
                const workflow = workflowFor(record);
                const active = activeByTask.get(record.id);
                if (workflowActive(record)) {
                  if (!awaitingSubmission(record) && workflow && canStopWorkflow(workflow.state)) {
                    stopTask(record, workflow.id);
                  }
                } else if (active && active.workflowInstanceId) {
                  stopTask(record, active.workflowInstanceId);
                } else {
                  runTask(record);
                }
              },
            }}
            onPublish={(value) => publishTask(value)}
            onUnpublish={(value) => unpublishTask(value)}
            onAlertConfig={(value) => actions.alert(value)}
            onEdit={(value) => {
              navigate(`/data-sync/${value.id}/edit`);
            }}
            onDetail={(value) => navigate(`/data-sync/${value.id}/detail`)}
            onDelete={(value) => {
              if (canManageTask(value)) actions.delete(value);
            }}
          />
        ),
    },
  ];

  return (
    <Table<DataSyncTaskListRecord>
      className="min-h-full"
      columns={columns}
      dataSource={records}
      rowKey="id"
      loading={loading}
      bordered
      size="medium"
      rowHeight={64}
      scroll={{ x: 1630 }}
      emptyText={failed ? "任务列表加载失败" : "还没有同步任务"}
      pagination={
        total > 0
          ? {
              current: pageNo,
              pageSize: PAGE_SIZE,
              total,
              disabled: loading,
              onChange: actions.page,
            }
          : false
      }
    />
  );
}

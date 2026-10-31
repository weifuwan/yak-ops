import { Button, Skeleton, Table, type TableColumns } from "@yak-ops/yak-ui";
import { useNavigate } from "react-router-dom";

import { TaskScheduleCell } from "@/app/schedule/task-schedule-cell";
import { ExecutionProgressBar } from "../execution/execution-progress-bar";
import { ExecutionStages } from "../execution/execution-stages";
import { TaskRoute } from "./task-route";
import { DataSyncTaskLifecycleActions, DataSyncTaskStatusBadge } from "./task-lifecycle";
import { readExecutionSummary } from "@/service/data-sync/report";
import { canCancelTaskInstance, canManageTaskDefinition } from "@/service/data-sync/execution";
import { syncSummary } from "../solution/presentation";
import type { DataSourceRecord } from "@/service/datasource";
import type { UserRecord } from "@/service/user";
import type {
  DataSyncTaskListRecord,
  DataSyncTaskRecord,
  DataSyncUnreadableTaskRecord,
} from "@/service/data-sync";
import type { TaskInstanceRecord } from "@/service/task-execution";
import type { ScheduleSummary } from "@/service/schedule";
import type { TaskCommand } from "./use-task-actions";

const PAGE_SIZE = 20;

function isUnreadable(record: DataSyncTaskListRecord): record is DataSyncUnreadableTaskRecord {
  return "configurationError" in record;
}

interface Data {
  dataSourceMap: Map<string, DataSourceRecord>;
  dataSourcesLoading: boolean;
  updateUserMap: Map<string, UserRecord>;
  updateUsersLoading: boolean;
  schedules: { records: Map<string, ScheduleSummary>; loading: boolean; error: boolean };
  latestByTask: Map<string, TaskInstanceRecord>;
  activeByTask: Map<string, TaskInstanceRecord>;
  instancesLoading: boolean;
  instancesReady: boolean;
  actionKey?: string;
}

interface Actions {
  configureSchedule: (task: DataSyncTaskRecord) => void;
  toggleSchedule: (task: DataSyncTaskRecord, enabled: boolean) => void;
  command: (value: TaskCommand) => void;
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
    schedules,
    latestByTask,
    activeByTask,
    instancesLoading,
    instancesReady,
    actionKey,
  } = data;

  const updateOperatorText = (record: DataSyncTaskListRecord) => {
    if (!record.updateBy) return "-";
    if (record.updateBy === "system") return "SYSTEM";
    const user = updateUserMap.get(record.updateBy);
    return user?.realName || user?.userName || "未知用户";
  };
  const canManageTask = (record: DataSyncTaskRecord) =>
    canManageTaskDefinition(instancesReady, activeByTask.get(record.id));
  const runTask = (task: DataSyncTaskRecord) => actions.command({ kind: "run", task });
  const stopTask = (task: DataSyncTaskRecord, instance: TaskInstanceRecord) =>
    actions.command({ kind: "stop", task, instance });
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
            {record.hasUnpublishedChanges ? " · 待发布" : ""}
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
            <div>{syncSummary(record.productType, record.steps)}</div>
            <div className="mt-0.5 text-[var(--yak-components-muted-text)]">
              {record.tableMappings.length} 张表
            </div>
          </div>
        ),
    },
    {
      key: "schedule",
      title: "调度",
      width: 200,
      render: (_value, record) =>
        !isUnreadable(record) &&
        (record.syncType === "OFFLINE" || schedules.records.has(record.id)) ? (
          <TaskScheduleCell
            schedule={schedules.records.get(record.id)}
            name={record.name}
            published={record.status === "PUBLISHED"}
            loading={schedules.loading}
            error={schedules.error}
            pending={Boolean(actionKey)}
            onConfigure={() => actions.configureSchedule(record)}
            onToggle={(enabled) => actions.toggleSchedule(record, enabled)}
          />
        ) : (
          <span className="text-[var(--yak-components-muted-text)]">—</span>
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
        const instance = latestByTask.get(record.id);
        if (!instance) {
          if (instancesLoading) return <Skeleton width="100%" height={22} />;
          return <ExecutionProgressBar realtime={record.syncType === "REALTIME"} />;
        }
        const summary = readExecutionSummary(instance.executionReport);
        return (
          <button
            type="button"
            className="block w-full cursor-pointer text-left"
            title="查看本次执行"
            onClick={() => navigate(`/data-sync/${record.id}/detail?executionId=${instance.id}`)}
          >
            {summary ? (
              <ExecutionStages compact status={instance.status} summary={summary} />
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
      width: 280,
      fixed: "right",
      align: "center",
      render: (_value, record) =>
        isUnreadable(record) ? (
          <Button
            size="small"
            variant="ghost"
            onClick={() => navigate(`/data-sync/${record.id}/edit`)}
          >
            查看
          </Button>
        ) : (
          <DataSyncTaskLifecycleActions
            record={record}
            activeInstance={activeByTask.get(record.id)}
            instanceStateReady={instancesReady}
            actionKey={actionKey}
            runtimeAction={{
              label: activeByTask.has(record.id)
                ? "停止"
                : record.syncType === "REALTIME"
                  ? "启动"
                  : "运行",
              className: activeByTask.has(record.id)
                ? "text-[var(--yak-components-danger-text)]"
                : "text-[var(--yak-color-primary)]",
              loading: actionKey === `${record.id}:run` || actionKey === `${record.id}:stop`,
              disabled:
                !instancesReady ||
                (activeByTask.has(record.id)
                  ? !canCancelTaskInstance(activeByTask.get(record.id)!.status)
                  : record.status !== "PUBLISHED"),
              onClick: () => {
                const active = activeByTask.get(record.id);
                if (active) stopTask(record, active);
                else runTask(record);
              },
            }}
            onPublish={(value) => publishTask(value)}
            onUnpublish={(value) => unpublishTask(value)}
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
      scroll={{ x: 1700 }}
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

import { Button, Table, type BadgeProps, type TableColumns } from "@yak-ops/yak-ui";
import {
  CheckCircle2,
  CircleHelp,
  CirclePause,
  CirclePlay,
  CircleStop,
  Clock3,
  LoaderCircle,
  XCircle,
} from "lucide-react";
import type { ReactNode } from "react";

import { formatScheduleTime } from "@/app/schedule/schedule-presentation";
import { isTaskInstanceActive } from "@/service/data-sync/execution";
import type { DataSyncInstanceRecord, DataSyncInstanceStatus } from "@/service/data-sync";

import { ExecutionProgressBar } from "./execution-progress-bar";
import { ExecutionStages } from "./execution-stages";
import { TableExecutionResults } from "./table-execution-results";
import "./execution-detail.css";

export const isActiveDataSyncInstance = (record?: Pick<DataSyncInstanceRecord, "status">): boolean =>
  record ? isTaskInstanceActive(record.status) : false;

export const dataSyncInstanceStatusMeta = (
  status: DataSyncInstanceStatus,
  realtime: boolean,
): { label: string; tone: BadgeProps["tone"] } => {
  switch (status) {
    case "SUBMITTED_SUCCESS":
      return { label: "等待", tone: "neutral" };
    case "DISPATCH":
      return { label: "派发中", tone: "info" };
    case "RUNNING_EXECUTION":
      return { label: "运行中", tone: "info" };
    case "DELAY_EXECUTION":
      return { label: "延迟执行", tone: "warning" };
    case "NEED_FAULT_TOLERANCE":
      return { label: "待容错", tone: "warning" };
    case "SUCCESS":
      return { label: "成功", tone: "success" };
    case "FORCED_SUCCESS":
      return { label: "强制成功", tone: "success" };
    case "FAILURE":
      return { label: "失败", tone: "danger" };
    case "PAUSE":
      return { label: "已暂停", tone: "neutral" };
    case "KILL":
      return { label: realtime ? "已停止" : "已取消", tone: "neutral" };
  }
};

const STATUS_ICONS = {
  SUBMITTED_SUCCESS: Clock3,
  DISPATCH: LoaderCircle,
  RUNNING_EXECUTION: CirclePlay,
  DELAY_EXECUTION: Clock3,
  NEED_FAULT_TOLERANCE: CircleHelp,
  SUCCESS: CheckCircle2,
  FORCED_SUCCESS: CheckCircle2,
  FAILURE: XCircle,
  PAUSE: CirclePause,
  KILL: CircleStop,

};

export function ExecutionStatusIcon({
  status,
  realtime,
}: {
  status: DataSyncInstanceStatus;
  realtime: boolean;
}) {
  const Icon = STATUS_ICONS[status];
  const { label } = dataSyncInstanceStatusMeta(status, realtime);
  return (
    <span
      className="sync-execution-status"
      data-status={status}
      role="img"
      aria-label={label}
      title={label}
    >
      <Icon size={14} aria-hidden="true" />
    </span>
  );
}

export const dataSyncTriggerText = (triggerType?: string) => {
  if (triggerType === "START_PROCESS") return "手动";
  if (triggerType === "SCHEDULER") return "调度";
  if (triggerType === "EXECUTE_TASK") return "工作流";
  return triggerType || "-";
};

export const dataSyncDurationText = (
  record: Pick<DataSyncInstanceRecord, "status" | "startTime" | "finishTime">,
) => {
  if (!record.startTime) return "-";
  if (!record.finishTime && !isActiveDataSyncInstance(record)) return "-";
  const start = Date.parse(record.startTime);
  const end = record.finishTime ? Date.parse(record.finishTime) : Date.now();
  if (Number.isNaN(start) || Number.isNaN(end)) return "-";
  const seconds = Math.max(0, Math.floor((end - start) / 1000));
  if (seconds < 60) return seconds + "s";
  const minutes = Math.floor(seconds / 60);
  return minutes + "m " + (seconds % 60) + "s";
};

const tablePath = (database?: string, schema?: string, table?: string) =>
  [database, schema, table].filter(Boolean).join(".") || "-";

const rowCountText = (value?: number) =>
  value === undefined ? "-" : value.toLocaleString("zh-CN");

function ExecutionProgressCard({ record }: DetailProps) {
  const realtime = record.syncType === "REALTIME";
  const scopeLabel =
    record.scope === "DATABASE"
      ? "整库同步"
      : record.scope === "MULTI_TABLE"
        ? "多表同步"
        : "单表同步";
  const hasFull = record.steps.includes("FULL");
  const hasIncremental = record.steps.includes("INCREMENTAL");
  const modeLabel = hasFull && hasIncremental
    ? "全量 + 增量"
    : hasFull ? "全量" : "增量";
  const summary =
    record.executionReport && record.executionReport.version !== 1
      ? record.executionReport
      : undefined;

  return (
    <div className="border border-[#eef0f3] bg-white px-3 py-3 text-center">
      {realtime || summary || record.steps.includes("SCHEMA") ? (
        <ExecutionStages status={record.status} steps={record.steps} summary={summary} />
      ) : (
        <ExecutionProgressBar
          status={record.status}
          percent={record.progressPercent}
          realtime={false}
        />
      )}

      <h3 className="mt-2 text-sm font-semibold text-[#344054]">
        MySQL · {scopeLabel} · {modeLabel}
      </h3>
      <div className="mt-1 text-xs text-[#98a2b3]">
        v{record.taskVersion} · {dataSyncTriggerText(record.triggerType)}
      </div>
    </div>
  );
}

interface DetailProps {
  record: DataSyncInstanceRecord;
  controls?: ReactNode;
  onViewScheduleHistory?: () => void;
}

interface TableExecutionRow {
  id: string;
  sourceTable: string;
  targetTable: string;
  status: DataSyncInstanceStatus;
  readRows?: number;
  writeRows?: number;
  startTime?: string;
  finishTime?: string;
  duration: string;
}

export function DataSyncExecutionDetailContent({
  record,
  controls,
  onViewScheduleHistory,
}: DetailProps) {
  const realtime = record.syncType === "REALTIME";
  const meta = dataSyncInstanceStatusMeta(record.status, realtime);
  const snapshot = record.definitionSnapshot;
  const rows: TableExecutionRow[] = snapshot
    ? [
        {
          id: record.id,
          sourceTable: tablePath(
            snapshot.source.database,
            snapshot.source.schema,
            snapshot.source.table,
          ),
          targetTable: tablePath(
            snapshot.target.database,
            snapshot.target.schema,
            snapshot.target.table,
          ),
          status: record.status,
          readRows: record.readRows,
          writeRows: record.writeRows,
          startTime: record.startTime,
          finishTime: record.finishTime,
          duration: dataSyncDurationText(record),
        },
      ]
    : [];
  const columns: TableColumns<TableExecutionRow> = [
    {
      key: "sourceTable",
      title: "来源表",
      minWidth: 220,
      render: (_value, row) => <span className="break-all">{row.sourceTable}</span>,
    },
    {
      key: "targetTable",
      title: "去向表",
      minWidth: 220,
      render: (_value, row) => <span className="break-all">{row.targetTable}</span>,
    },
    {
      key: "status",
      title: "执行状态",
      width: 90,
      align: "center",
      render: (_value, row) => <ExecutionStatusIcon status={row.status} realtime={realtime} />,
    },
    {
      key: "readRows",
      title: "读取条数",
      width: 110,
      align: "right",
      render: (_value, row) => rowCountText(row.readRows),
    },
    {
      key: "writeRows",
      title: "写入条数",
      width: 110,
      align: "right",
      render: (_value, row) => rowCountText(row.writeRows),
    },
    {
      key: "startTime",
      title: "开始时间",
      width: 170,
      render: (_value, row) => row.startTime || "-",
    },
    {
      key: "finishTime",
      title: "完成时间",
      width: 170,
      render: (_value, row) => row.finishTime || "-",
    },
    {
      key: "duration",
      title: "耗时",
      width: 90,
      render: (_value, row) => row.duration,
    },
  ];

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-1.5 text-xs text-[#667085]" role="status">
          <span>执行状态：</span>
          <span aria-hidden="true">
            <ExecutionStatusIcon status={record.status} realtime={realtime} />
          </span>
          <span>{meta.label}</span>
        </div>
        {controls ? <div className="flex items-center gap-3">{controls}</div> : null}
      </div>
      {record.triggerType === "SCHEDULE" ? (
        <div className="flex flex-wrap items-center gap-3 text-xs text-[#667085]">
          {record.scheduledTime ? (
            <span>计划时间：{formatScheduleTime(record.scheduledTime)}</span>
          ) : null}
          {record.scheduleRevision != null ? (
            <span>调度版本：{record.scheduleRevision}</span>
          ) : null}
          {record.scheduleId && onViewScheduleHistory ? (
            <Button size="sm" variant="ghost" onClick={onViewScheduleHistory}>
              触发记录
            </Button>
          ) : null}
        </div>
      ) : null}
      <ExecutionProgressCard record={record} />
      <div className="min-w-0 pt-4">
        {record.scope === "DATABASE" || record.executionReport ? (
          <TableExecutionResults record={record} />
        ) : (
          <Table<TableExecutionRow>
            columns={columns}
            dataSource={rows}
            rowKey="id"
            size="small"
            scroll={{ x: 1180 }}
            pagination={false}
            emptyText="暂无表级执行数据"
          />
        )}
      </div>
    </div>
  );
}

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
import { syncSummary } from "../solution/presentation";
import { TableExecutionResults } from "./table-execution-results";
import "./execution-detail.css";

export const isActiveDataSyncInstance = (record?: DataSyncInstanceRecord): boolean =>
  record ? isTaskInstanceActive(record.status) : false;

export const dataSyncInstanceStatusMeta = (
  status: DataSyncInstanceStatus,
  realtime: boolean,
): { label: string; tone: BadgeProps["tone"] } => {
  switch (status) {
    case "SUBMITTED":
      return { label: "等待", tone: "neutral" };
    case "LAUNCHING":
      return { label: "启动中", tone: "info" };
    case "RUNNING":
      return { label: "运行中", tone: "info" };
    case "CANCELLING":
      return { label: "正在停止", tone: "warning" };
    case "RECONCILING":
      return { label: "待确认", tone: "warning" };
    case "FINISHED":
      return { label: "成功", tone: "success" };
    case "FAILED":
      return { label: "失败", tone: "danger" };
    case "CANCELED":
      return { label: realtime ? "已停止" : "已取消", tone: "neutral" };
  }
};

const STATUS_ICONS = {
  SUBMITTED: Clock3,
  LAUNCHING: LoaderCircle,
  RUNNING: CirclePlay,
  CANCELLING: CirclePause,
  RECONCILING: CircleHelp,
  FINISHED: CheckCircle2,
  FAILED: XCircle,
  CANCELED: CircleStop,
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
  if (triggerType === "MANUAL") return "手动";
  if (triggerType === "SCHEDULE") return "调度";
  if (triggerType === "WORKFLOW") return "工作流";
  return triggerType || "-";
};

export const dataSyncDurationText = (record: DataSyncInstanceRecord) => {
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
        {syncSummary(record.productType, record.steps)}
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
            <Button size="small" variant="ghost" onClick={onViewScheduleHistory}>
              触发记录
            </Button>
          ) : null}
        </div>
      ) : null}
      <ExecutionProgressCard record={record} />
      <div className="min-w-0 pt-4">
        {record.productType === "DATABASE_REPLICATION" || record.executionReport ? (
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

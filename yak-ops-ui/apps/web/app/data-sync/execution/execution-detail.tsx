import { Button, Skeleton, type BadgeProps } from "@yak-ops/yak-ui";
import type { ReactNode } from "react";

import { formatScheduleTime } from "@/app/schedule/schedule-presentation";
import { ExecutionStatusIndicator } from "@/app/components/execution-status-indicator";
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

export function ExecutionStatusIcon({
  status,
  realtime,
}: {
  status: DataSyncInstanceStatus;
  realtime: boolean;
}) {
  const { label } = dataSyncInstanceStatusMeta(status, realtime);
  return (
    <ExecutionStatusIndicator
      status={status}
      label={label}
      size={14}
      showLabel={false}
    />
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

function ExecutionProgressCard({ record }: DetailProps) {
  const realtime = record.syncType === "REALTIME";
  const report = record.executionReport;
  const summary = report && report.version !== 1 ? report : undefined;
  const batchReport = report?.version === 1 ? report : undefined;

  return (
    <div className="grid min-h-28 place-items-center border border-[#eef0f3] bg-white px-3 py-3 text-center">
      <div className="w-full">
        {realtime || summary || batchReport || record.steps.includes("SCHEMA") ? (
          <ExecutionStages
            status={record.status}
            steps={record.steps}
            summary={summary}
            batchReport={batchReport}
          />
        ) : (
          <ExecutionProgressBar
            status={record.status}
            percent={record.progressPercent}
            realtime={false}
          />
        )}
      </div>
    </div>
  );
}

const TABLE_SKELETON_WIDTHS = [
  "72%",
  "72%",
  28,
  28,
  28,
  24,
  24,
  24,
  28,
  20,
  "82%",
  "82%",
  32,
] as const;

/** Mirrors the execution detail layout while a newly selected instance is loading. */
export function DataSyncExecutionDetailSkeleton({ controls }: { controls?: ReactNode }) {
  return (
    <div className="space-y-4" role="status" aria-label="加载执行详情" aria-busy="true">
      <div className="flex justify-end">
        {controls ? <div className="flex items-center gap-3">{controls}</div> : null}
      </div>

      <div className="grid min-h-28 place-items-center border border-[#eef0f3] bg-white px-3 py-3">
        <div className="grid w-full gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {Array.from({ length: 3 }, (_value, index) => (
            <div key={index} className="min-w-0">
              <Skeleton height={22} />
              <Skeleton width={64} height={14} className="mx-auto mt-2" />
            </div>
          ))}
        </div>
      </div>

      <div className="min-w-0 overflow-x-auto pt-4">
        <div className="min-w-[1565px]">
          <div className="grid grid-cols-[minmax(180px,1fr)_minmax(180px,1fr)_90px_90px_90px_75px_75px_75px_90px_70px_160px_160px_80px] items-center gap-3 bg-[#f2f4f7] px-3 py-2">
            {TABLE_SKELETON_WIDTHS.map((width, index) => (
              <div key={index} className="min-w-0">
                <Skeleton width={width} height={12} />
              </div>
            ))}
          </div>
          <div className="grid grid-cols-[minmax(180px,1fr)_minmax(180px,1fr)_90px_90px_90px_75px_75px_75px_90px_70px_160px_160px_80px] items-center gap-3 border-b border-[#eef0f3] px-3 py-3">
            {TABLE_SKELETON_WIDTHS.map((width, index) => (
              <div key={index} className="min-w-0">
                <Skeleton width={width} height={12} />
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

interface DetailProps {
  record: DataSyncInstanceRecord;
  controls?: ReactNode;
  onViewScheduleHistory?: () => void;
}

export function DataSyncExecutionDetailContent({
  record,
  controls,
  onViewScheduleHistory,
}: DetailProps) {
  return (
    <div className="space-y-4">
      {controls ? <div className="flex justify-end">{controls}</div> : null}
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
        <TableExecutionResults
          record={record}
          duration={dataSyncDurationText(record)}
        />
      </div>
    </div>
  );
}

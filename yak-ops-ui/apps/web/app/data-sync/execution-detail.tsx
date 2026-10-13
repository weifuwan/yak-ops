import { Badge, SectionCard, Table, type BadgeProps, type TableColumns } from "@yak-ops/yak-ui";
import type { ReactNode } from "react";

import { canCancelTaskInstance, isTaskInstanceActive } from "@/service/data-sync/execution";
import type {
  DataSyncAttemptRecord,
  DataSyncInstanceRecord,
  DataSyncInstanceStatus,
} from "@/service/data-sync";
import type { TaskAttemptStatus } from "@/service/task-execution";

export const isActiveDataSyncInstance = (record?: DataSyncInstanceRecord): boolean =>
  record ? isTaskInstanceActive(record.status) : false;

export const dataSyncInstanceStatusMeta = (
  status: DataSyncInstanceStatus | TaskAttemptStatus,
  realtime: boolean,
): { label: string; tone: BadgeProps["tone"] } => {
  switch (status) {
    case "SUBMITTED":
    case "PENDING":
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
    case "INTERRUPTED":
      return { label: "已中断", tone: "warning" };
  }
};

export const dataSyncTriggerText = (triggerType?: string) => {
  if (triggerType === "MANUAL") return "手动";
  if (triggerType === "SCHEDULE") return "调度";
  if (triggerType === "WORKFLOW") return "工作流";
  return triggerType || "-";
};

export const dataSyncDurationText = (record: DataSyncInstanceRecord) => {
  if (!record.startTime) return "-";
  const start = Date.parse(record.startTime);
  const end = record.finishTime ? Date.parse(record.finishTime) : Date.now();
  if (Number.isNaN(start) || Number.isNaN(end)) return "-";
  const seconds = Math.max(0, Math.floor((end - start) / 1000));
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  return `${minutes}m ${seconds % 60}s`;
};

function Value({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0">
      <div className="text-xs text-[#98a2b3]">{label}</div>
      <div className="mt-1 break-words text-sm text-[#344054]">{children}</div>
    </div>
  );
}

const tablePath = (database?: string, schema?: string, table?: string) =>
  [database, schema, table].filter(Boolean).join(".") || "-";

const writeModeText = (value?: string) => {
  if (value === "APPEND") return "追加";
  if (value === "OVERWRITE") return "覆盖";
  if (value === "UPSERT") return "更新插入";
  return value || "-";
};

interface ConfigProps {
  record: DataSyncInstanceRecord;
  realtime: boolean;
  sectionCard?: boolean;
}

export function DataSyncExecutionConfigContent({ record }: ConfigProps) {
  const snapshot = record.definitionSnapshot;
  if (!snapshot) return null;

  return (
    <SectionCard title="配置快照">
      <div className="grid grid-cols-2 gap-5 max-md:grid-cols-1">
        <div className="space-y-4">
          <Value label="来源数据源">
            {snapshot.source.dataSourceName || snapshot.source.dataSourceId}
          </Value>
          <Value label="来源表">
            {tablePath(snapshot.source.database, snapshot.source.schema, snapshot.source.table)}
          </Value>
        </div>
        <div className="space-y-4">
          <Value label="去向数据源">
            {snapshot.target.dataSourceName || snapshot.target.dataSourceId}
          </Value>
          <Value label="去向表">
            {tablePath(snapshot.target.database, snapshot.target.schema, snapshot.target.table)}
          </Value>
          <Value label="写入方式">{writeModeText(snapshot.writeMode)}</Value>
        </div>
      </div>
    </SectionCard>
  );
}

interface DetailProps {
  record: DataSyncInstanceRecord;
  attempts: DataSyncAttemptRecord[];
  realtime: boolean;
  showRealtimeStopCaution?: boolean;
  sectionCard?: boolean;
}

export function DataSyncExecutionDetailContent({ record, attempts, realtime }: DetailProps) {
  const columns: TableColumns<DataSyncAttemptRecord> = [
    {
      key: "attemptNo",
      title: "Attempt",
      width: 100,
      render: (_value, attempt) => `#${attempt.attemptNo}`,
    },
    {
      key: "status",
      title: "状态",
      width: 105,
      render: (_value, attempt) => {
        const meta = dataSyncInstanceStatusMeta(attempt.status, realtime);
        return <Badge tone={meta.tone}>{meta.label}</Badge>;
      },
    },
    {
      key: "runtimeId",
      title: "Job ID",
      minWidth: 185,
      render: (_value, attempt) => attempt.runtimeId || "-",
    },
    {
      key: "logReference",
      title: "日志引用",
      minWidth: 170,
      render: (_value, attempt) => attempt.logReference || "-",
    },
    {
      key: "endTime",
      title: "完成时间",
      width: 180,
      render: (_value, attempt) => attempt.finishTime || "-",
    },
    {
      key: "failureCode",
      title: "失败原因",
      minWidth: 180,
      render: (_value, attempt) => attempt.failureCode || "-",
    },
  ];
  const meta = dataSyncInstanceStatusMeta(record.status, realtime);

  return (
    <div className="space-y-4">
      <SectionCard title="执行状态">
        <div className="flex flex-wrap items-center gap-4">
          <Badge tone={meta.tone}>{meta.label}</Badge>
          <span className="text-sm text-[#667085]">v{record.taskVersion}</span>
          <span className="text-sm text-[#667085]">{dataSyncTriggerText(record.triggerType)}</span>
        </div>
        <div className="mt-4 grid grid-cols-3 gap-4 max-md:grid-cols-1">
          <Value label="开始时间">{record.startTime || record.createTime || "-"}</Value>
          <Value label="完成时间">{record.finishTime || "-"}</Value>
          <Value label="耗时">{dataSyncDurationText(record)}</Value>
        </div>
        {record.errorMessage ? (
          <div className="mt-4 text-sm text-[#b42318]">{record.errorMessage}</div>
        ) : null}
        {record.status === "RECONCILING" ? (
          <div className="mt-4 text-sm text-[#b54708]">执行所有权尚未确认，目标资源仍受保护</div>
        ) : null}
        {canCancelTaskInstance(record.status) && realtime ? (
          <div className="mt-4 text-xs text-[#667085]">停止不会自动恢复；重新运行会创建新实例</div>
        ) : null}
      </SectionCard>
      {attempts.length > 0 ? (
        <SectionCard title="执行尝试">
          <Table<DataSyncAttemptRecord>
            columns={columns}
            dataSource={attempts}
            rowKey="id"
            bordered
            size="small"
            pagination={false}
            scroll={{ x: 980 }}
          />
        </SectionCard>
      ) : null}
    </div>
  );
}

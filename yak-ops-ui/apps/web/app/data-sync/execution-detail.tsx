import { Alert, Badge, SectionCard, Table, type BadgeProps } from "@yak-ops/yak-ui";
import type { ReactNode } from "react";

import type {
  DataSyncAttemptRecord,
  DataSyncInstanceRecord,
  DataSyncInstanceStatus,
} from "@/service/data-sync";

export const isActiveDataSyncInstance = (record?: DataSyncInstanceRecord) =>
  record?.status === "PENDING" ||
  record?.status === "RUNNING" ||
  record?.status === "RETRY_WAITING";

export const dataSyncInstanceStatusMeta = (
  status: DataSyncInstanceStatus,
  realtime: boolean,
): { label: string; tone: BadgeProps["tone"] } => {
  switch (status) {
    case "PENDING":
      return { label: "等待", tone: "neutral" };
    case "RUNNING":
      return { label: "运行中", tone: "info" };
    case "RETRY_WAITING":
      return { label: "等待重试", tone: "warning" };
    case "SUCCEEDED":
      return { label: "成功", tone: "success" };
    case "FAILED":
      return { label: "失败", tone: "danger" };
    case "CANCELED":
      return { label: realtime ? "已停止" : "已取消", tone: "neutral" };
    case "LOST":
      return { label: "已丢失", tone: "warning" };
    default:
      return { label: status || "-", tone: "neutral" };
  }
};

export const dataSyncTriggerText = (triggerType?: string) => {
  if (triggerType === "MANUAL") return "手动运行";
  if (triggerType === "SCHEDULE") return "调度触发";
  if (triggerType === "AUTO_RECOVERY") return "自动恢复";
  if (triggerType === "RETRY") return "重试";
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

interface DetailSectionProps {
  title: ReactNode;
  children: ReactNode;
  sectionCard: boolean;
  legacyClassName: string;
  showLegacyTitle?: boolean;
}

function DetailSection({
  title,
  children,
  sectionCard,
  legacyClassName,
  showLegacyTitle = true,
}: DetailSectionProps) {
  if (sectionCard) {
    return <SectionCard title={title}>{children}</SectionCard>;
  }

  return (
    <section className={legacyClassName}>
      {showLegacyTitle ? (
        <h2 className="border-b border-[#eef0f3] bg-[#fafafa] px-4 py-2.5 text-sm font-semibold text-[#344054]">
          {title}
        </h2>
      ) : null}
      {children}
    </section>
  );
}

type ConfigurationItem = [label: string, value: string];

const endpointPathText = (database?: string, schema?: string, table?: string) =>
  [database, schema, table].filter(Boolean).join(".") || "-";

const dataSourceTypeText = (type?: string) => {
  if (type === "MYSQL") return "MySQL";
  if (type === "POSTGRESQL") return "PostgreSQL";
  if (type === "ORACLE") return "Oracle";
  return type || "-";
};

const writeModeText = (writeMode?: string) => {
  if (writeMode === "OVERWRITE") return "覆盖";
  if (writeMode === "UPSERT") return "更新插入";
  if (writeMode === "APPEND") return "追加";
  return writeMode || "-";
};

function ConfigurationGrid({ items }: { items: ConfigurationItem[] }) {
  return (
    <div className="grid grid-cols-3 gap-4 max-lg:grid-cols-2 max-md:grid-cols-1">
      {items.map(([label, value]) => (
        <div key={label} className="rounded-lg bg-[#fafafa] px-4 py-3">
          <div className="text-xs text-[#98a2b3]">{label}</div>
          <div className="mt-1 break-words text-sm font-medium text-[#344054]">{value}</div>
        </div>
      ))}
    </div>
  );
}

interface DataSyncExecutionConfigContentProps {
  record: DataSyncInstanceRecord;
  realtime: boolean;
  sectionCard?: boolean;
}

export function DataSyncExecutionConfigContent({
  record,
  realtime,
  sectionCard = false,
}: DataSyncExecutionConfigContentProps) {
  const snapshot = record.definitionSnapshot;
  if (!snapshot) {
    return (
      <DetailSection
        title="配置快照"
        sectionCard={sectionCard}
        legacyClassName="rounded-lg border border-[#e6e8eb] bg-white p-5"
      >
        <div className="py-8 text-center text-sm text-[#98a2b3]">暂无配置快照</div>
      </DetailSection>
    );
  }

  const runtimeConfig = snapshot.runtimeConfig;
  const offlineRuntimePlan = snapshot.offlineRuntimePlan;
  const realtimeConfig = snapshot.realtimeConfig;
  const retryPolicy = snapshot.retryPolicy;
  const sourceItems: ConfigurationItem[] = [
    ["数据源", snapshot.source.dataSourceName || "未知数据源"],
    ["类型", dataSourceTypeText(snapshot.source.dataSourceType)],
    [
      "数据表",
      endpointPathText(snapshot.source.database, snapshot.source.schema, snapshot.source.table),
    ],
  ];
  const targetItems: ConfigurationItem[] = [
    ["数据源", snapshot.target.dataSourceName || "未知数据源"],
    ["类型", dataSourceTypeText(snapshot.target.dataSourceType)],
    [
      "数据表",
      endpointPathText(snapshot.target.database, snapshot.target.schema, snapshot.target.table),
    ],
    ["自动建表", snapshot.autoCreateTable ? "开启" : "关闭"],
  ];
  const executionItems: ConfigurationItem[] = [
    ["任务版本", `v${snapshot.taskVersion}`],
    ["同步类型", realtime ? "实时同步" : "离线同步"],
  ];

  if (realtime) {
    sourceItems.push(
      ["CDC 队列", realtimeConfig?.queueCapacity?.toLocaleString() || "-"],
      ["读取批次", realtimeConfig?.pollBatchSize?.toLocaleString() || "-"],
    );
    targetItems.push(["写入批次", realtimeConfig?.writeBatchSize?.toLocaleString() || "-"]);
    executionItems.push(
      ["Checkpoint 间隔", realtimeConfig ? `${realtimeConfig.checkpointIntervalSeconds}s` : "-"],
      ["超时", realtimeConfig ? `${realtimeConfig.timeoutSeconds}s` : "-"],
    );
  } else {
    sourceItems.push(
      ["Fetch Size", runtimeConfig?.fetchSize?.toLocaleString() || "-"],
      ["读取批次", runtimeConfig?.readBatchSize?.toLocaleString() || "-"],
      ["读取并行度", runtimeConfig?.sourceParallelism?.toLocaleString() || "-"],
      [
        "分片目标行数",
        runtimeConfig?.splitSize == null ? "整表读取" : runtimeConfig.splitSize.toLocaleString(),
      ],
    );
    if (offlineRuntimePlan?.sourceRowCount != null) {
      sourceItems.push(["规划行数", offlineRuntimePlan.sourceRowCount.toLocaleString()]);
    }
    if (offlineRuntimePlan?.splitCount != null) {
      sourceItems.push(["规划分片数", offlineRuntimePlan.splitCount.toLocaleString()]);
    }
    targetItems.push(
      ["写入方式", writeModeText(snapshot.writeMode)],
      ["写入批次", runtimeConfig?.writeBatchSize?.toLocaleString() || "-"],
    );
    executionItems.push(
      [
        "运行策略",
        runtimeConfig?.policy === "AUTO"
          ? offlineRuntimePlan?.splitColumn && offlineRuntimePlan.statisticsAvailable === false
            ? "自动规划（安全回退）"
            : "自动规划"
          : "固定配置",
      ],
      [
        "估算行宽",
        offlineRuntimePlan?.estimatedRowBytes ? `${offlineRuntimePlan.estimatedRowBytes} B` : "-",
      ],
      ["超时", runtimeConfig ? `${runtimeConfig.timeoutSeconds}s` : "-"],
    );
  }

  executionItems.push(
    ["重试策略", retryPolicy?.mode === "SMART" ? "智能重试" : "固定重试"],
    ["最大尝试次数", (retryPolicy?.maxAttempts ?? record.maxAttempts ?? 1).toLocaleString()],
    [
      retryPolicy?.mode === "SMART" ? "起始退避" : "重试间隔",
      `${retryPolicy?.backoffSeconds ?? record.backoffSeconds ?? 0}s`,
    ],
  );

  return (
    <div className="space-y-4">
      <DetailSection
        title="数据来源"
        sectionCard={sectionCard}
        legacyClassName="rounded-lg border border-[#e6e8eb] bg-white p-5"
      >
        <ConfigurationGrid items={sourceItems} />
      </DetailSection>

      <DetailSection
        title="数据去向"
        sectionCard={sectionCard}
        legacyClassName="rounded-lg border border-[#e6e8eb] bg-white p-5"
      >
        <ConfigurationGrid items={targetItems} />
      </DetailSection>

      <DetailSection
        title="执行策略"
        sectionCard={sectionCard}
        legacyClassName="rounded-lg border border-[#e6e8eb] bg-white p-5"
      >
        <ConfigurationGrid items={executionItems} />
      </DetailSection>
    </div>
  );
}

interface DataSyncExecutionDetailContentProps {
  record: DataSyncInstanceRecord;
  attempts: DataSyncAttemptRecord[];
  realtime: boolean;
  showRealtimeStopCaution?: boolean;
  sectionCard?: boolean;
}

export function DataSyncExecutionDetailContent({
  record,
  attempts,
  realtime,
  showRealtimeStopCaution = false,
  sectionCard = false,
}: DataSyncExecutionDetailContentProps) {
  const meta = dataSyncInstanceStatusMeta(record.status, realtime);
  const readLabel = realtime ? "读取事件" : "读取";
  const writeLabel = realtime ? "写入事件" : "写入";
  const failureMessage =
    record.errorMessage ||
    [...attempts].reverse().find((attempt) => attempt.errorMessage)?.errorMessage;
  const showAttemptHistory =
    attempts.length > 1 || (record.currentAttempt || 1) > 1 || record.status === "RETRY_WAITING";

  return (
    <div className="space-y-4">
      {showRealtimeStopCaution && realtime && isActiveDataSyncInstance(record) ? (
        <Alert>停止后将从最近 Checkpoint 续跑，少量未确认事件可能重复消费。</Alert>
      ) : null}

      <DetailSection
        title="执行概览"
        sectionCard={sectionCard}
        legacyClassName="rounded-lg border border-[#e6e8eb] bg-white p-5"
        showLegacyTitle={false}
      >
        <div className="flex flex-wrap items-center gap-3">
          <Badge tone={meta.tone}>{meta.label}</Badge>
          <span className="text-sm text-[#667085]">
            {record.startTime || record.createTime || "-"} → {record.finishTime || "运行中"}
          </span>
          <span className="text-xs text-[#98a2b3]">{dataSyncTriggerText(record.triggerType)}</span>
        </div>

        {failureMessage ? (
          <div className="mt-4 rounded-lg border border-[#fecdca] bg-[#fffbfa] px-4 py-3">
            <div className="text-sm font-medium text-[#b42318]">失败原因</div>
            <div className="mt-1 whitespace-pre-wrap break-words text-sm leading-5 text-[#667085]">
              {failureMessage}
            </div>
          </div>
        ) : null}

        <div className="mt-5 grid grid-cols-4 gap-4 max-lg:grid-cols-2 max-md:grid-cols-1">
          {[
            [readLabel, (record.readRows ?? 0).toLocaleString()],
            [writeLabel, (record.writeRows ?? 0).toLocaleString()],
            ["尝试次数", `${record.currentAttempt || 1} / ${record.maxAttempts || 1}`],
            ["耗时", dataSyncDurationText(record)],
          ].map(([label, value]) => (
            <div key={label} className="rounded-lg bg-[#fafafa] px-4 py-3">
              <div className="text-xs text-[#98a2b3]">{label}</div>
              <div className="mt-1 text-lg font-semibold text-[#344054]">{value}</div>
            </div>
          ))}
        </div>

        {realtime ? (
          <div className="mt-3 text-xs leading-5 text-[#98a2b3]">
            实时指标统计 YakFlow 变更事件；UPDATE 会产生 UPDATE_BEFORE 与 UPDATE_AFTER 两个事件。
          </div>
        ) : null}
      </DetailSection>

      {showAttemptHistory ? (
        <DetailSection
          title="重试记录"
          sectionCard={sectionCard}
          legacyClassName="overflow-hidden rounded-lg border border-[#e6e8eb] bg-white"
        >
          <Table<DataSyncAttemptRecord>
            columns={[
              {
                key: "attemptNo",
                title: "次数",
                width: 90,
                render: (_value, attempt) => `#${attempt.attemptNo}`,
              },
              {
                key: "status",
                title: "状态",
                width: 110,
                render: (_value, attempt) => {
                  const attemptMeta = dataSyncInstanceStatusMeta(attempt.status, realtime);
                  return <Badge tone={attemptMeta.tone}>{attemptMeta.label}</Badge>;
                },
              },
              {
                key: "time",
                title: "开始 / 完成",
                minWidth: 260,
                render: (_value, attempt) => (
                  <div className="text-xs text-[#667085]">
                    {attempt.startTime || attempt.createTime || "-"} →{" "}
                    {attempt.finishTime || "运行中"}
                  </div>
                ),
              },
              {
                key: "metrics",
                title: "读取 / 写入",
                width: 140,
                align: "right",
                render: (_value, attempt) =>
                  `${(attempt.readRows ?? 0).toLocaleString()} / ${(attempt.writeRows ?? 0).toLocaleString()}`,
              },
              {
                key: "error",
                title: "错误",
                minWidth: 220,
                render: (_value, attempt) => (
                  <div
                    className="max-w-[360px] truncate text-xs text-[#667085]"
                    title={attempt.errorMessage || ""}
                  >
                    {attempt.errorMessage || "-"}
                  </div>
                ),
              },
            ]}
            dataSource={attempts}
            rowKey="id"
            bordered
            size="small"
            pagination={false}
            emptyText="旧实例或未执行实例暂无独立 Attempt 记录"
            scroll={{ x: 900 }}
          />
          {record.status === "RETRY_WAITING" ? (
            <div className="border-t border-[#eef0f3] px-4 py-3 text-xs text-[#b54708]">
              下一次重试：{record.nextRetryTime || "待执行"}
              {record.definitionSnapshot?.retryPolicy?.mode === "SMART"
                ? " · 智能退避"
                : ` · Backoff ${record.backoffSeconds || 0}s`}
            </div>
          ) : null}
        </DetailSection>
      ) : null}
    </div>
  );
}

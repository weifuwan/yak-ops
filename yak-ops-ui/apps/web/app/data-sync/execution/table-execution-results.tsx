import { Table, type TableColumns } from "@yak-ops/yak-ui";
import type {
  DataSyncInstanceRecord,
  DataSyncMetricsTableReport,
  DataSyncStageResult,
} from "@/service/data-sync/types";
import { formatMetricCount } from "@/service/data-sync/report";
import { StageProgress, useObservationStale } from "./execution-stages";

const pathText = (table: { database?: string; schema?: string; table?: string }) =>
  [table.database, table.schema, table.table].filter(Boolean).join(".");
const countText = formatMetricCount;
const normalizePath = (value?: string | null) => value?.trim().toLowerCase() ?? "";

function MetricsSummaryItem({
  label,
  value,
  title,
}: {
  label: string;
  value?: number | null;
  title?: string;
}) {
  return (
    <div title={title} className="min-w-0 border border-[#eef0f3] bg-white px-3 py-2">
      <div className="text-[11px] text-[#667085]">{label}</div>
      <div className="mt-1 truncate text-sm font-semibold tabular-nums text-[#101828]">
        {countText(value)}
      </div>
    </div>
  );
}

function metricsStateText(state?: string) {
  switch (state) {
    case "AVAILABLE":
      return "完整";
    case "PARTIAL":
      return "部分可用";
    case "UNAVAILABLE":
      return "不可用";
    case "STALE":
      return "采集陈旧";
    default:
      return state || "未知";
  }
}

export function TableExecutionResults({ record }: { record: DataSyncInstanceRecord }) {
  const report = record.executionReport;
  const metricsReport = record.metricsReport;
  const stale = useObservationStale(record.status, report?.observedAt);
  const realtime = record.syncType === "REALTIME";
  const hasIncremental = report
    ? report.version === 1
      ? report.tables.some((table) => table.step === "INCREMENTAL")
      : report.stages.some((stage) => stage.step === "INCREMENTAL")
    : record.steps.includes("INCREMENTAL");
  const hasFull = report
    ? report.version === 1
      ? report.tables.some((table) => table.step === "FULL")
      : report.stages.some((stage) => stage.step === "FULL")
    : record.steps.includes("FULL");
  const mappings = record.definitionSnapshot?.tableMappings || [];
  const rows = mappings.map((mapping, index) => {
    let schema: DataSyncStageResult | undefined;
    let full: DataSyncStageResult | undefined;
    let incremental: DataSyncStageResult | undefined;
    if (report && report.version !== 1) {
      const result = report.tables.find((table) => table.mappingIndex === index);
      schema = result?.stages.find((stage) => stage.step === "SCHEMA");
      full = result?.stages.find((stage) => stage.step === "FULL");
      incremental = result?.stages.find((stage) => stage.step === "INCREMENTAL");
    } else {
      const table = report?.tables.find((table) => table.mappingIndex === index);
      if (table?.step === "INCREMENTAL") incremental = table;
      else full = table;
    }
    return {
      id: String(index),
      source: pathText(mapping.source),
      target: pathText(mapping.target),
      schema,
      full,
      incremental,
    };
  });
  const columns: TableColumns<(typeof rows)[number]> = [
    {
      key: "source",
      title: "来源表",
      minWidth: 180,
      render: (_value, row) => <span className="break-all">{row.source}</span>,
    },
    {
      key: "target",
      title: "去向表",
      minWidth: 180,
      render: (_value, row) => <span className="break-all">{row.target}</span>,
    },
    ...(hasFull
      ? [
          {
            key: "full",
            title: "全量进度",
            width: 160,
            render: (_value: unknown, row: (typeof rows)[number]) => (
              <StageProgress
                stage={row.full}
                status={record.status}
                stale={stale}
                label={`${row.source} 全量进度`}
              />
            ),
          },
        ]
      : []),
  ];
  if (record.steps.includes("SCHEMA"))
    columns.splice(2, 0, {
      key: "schema",
      title: "结构迁移",
      width: 160,
      render: (_value, row) => (
        <div
          title={
            row.schema?.action === "VERIFIED"
              ? "已有结构校验通过"
              : row.schema?.action === "PREPARED"
                ? "目标结构已准备并校验"
                : undefined
          }
        >
          <StageProgress
            stage={row.schema}
            status={record.status}
            stale={stale}
            label={`${row.source} 结构迁移`}
          />
        </div>
      ),
    });
  if (hasIncremental)
    columns.push({
      key: "incremental",
      title: "增量状态",
      width: 160,
      render: (_value, row) => (
        <StageProgress
          stage={row.incremental}
          status={record.status}
          stale={stale}
          label={`${row.source} 增量状态`}
        />
      ),
    });
  columns.push(
    {
      key: "read",
      title: realtime ? "全量读取" : "读取条数",
      width: 105,
      align: "right",
      render: (_value, row) =>
        countText(realtime ? row.full?.readRows : (row.incremental || row.full)?.readRows),
    },
    {
      key: "written",
      title: realtime ? "全量提交" : "已提交条数",
      width: 105,
      align: "right",
      render: (_value, row) =>
        countText(
          realtime ? row.full?.committedRows : (row.incremental || row.full)?.committedRows,
        ),
    },
  );
  if (realtime) {
    const scope =
      report && report.version !== 1
        ? `执行代次 ${report.generation} 的变更记录数，非 SQL 影响行数`
        : "变更记录数";
    columns.push(
      {
        key: "incrementalRead",
        title: "增量读取",
        width: 105,
        align: "right",
        render: (_value, row) => <span title={scope}>{countText(row.incremental?.readRows)}</span>,
      },
      {
        key: "incrementalWritten",
        title: "增量提交",
        width: 105,
        align: "right",
        render: (_value, row) => (
          <span title={scope}>{countText(row.incremental?.committedRows)}</span>
        ),
      },
    );
  }
  columns.push({
    key: "failure",
    title: "失败原因",
    minWidth: 120,
    render: (_value, row) => {
      const failure =
        row.schema?.failureCode || row.incremental?.failureCode || row.full?.failureCode;
      if (failure === "SCHEMA_PREPARATION_FAILED") return "目标结构准备失败";
      if (failure === "READ_FAILED") return "来源读取失败";
      if (failure === "WRITE_FAILED") return "目标写入失败";
      return "—";
    },
  });

  const metricSnapshot =
    metricsReport?.finalized && metricsReport.finalSnapshot
      ? metricsReport.finalSnapshot
      : metricsReport?.latestSnapshot;
  const metricRows: Array<{
    id: string;
    source: string;
    target: string;
    metrics: DataSyncMetricsTableReport;
  }> = (metricsReport?.tables || []).map((metrics, index) => {
    const mapping = mappings.find((candidate) =>
      (metrics.sourceTable &&
        normalizePath(pathText(candidate.source)) === normalizePath(metrics.sourceTable)) ||
      (metrics.sinkTable &&
        normalizePath(pathText(candidate.target)) === normalizePath(metrics.sinkTable)),
    );
    return {
      id: `metrics-${index}`,
      source: metrics.sourceTable || (mapping ? pathText(mapping.source) : "—"),
      target: metrics.sinkTable || (mapping ? pathText(mapping.target) : "—"),
      metrics,
    };
  });
  const metricsColumns: TableColumns<(typeof metricRows)[number]> = [
    {
      key: "source",
      title: "来源表",
      minWidth: 180,
      render: (_value, row) => <span className="break-all">{row.source}</span>,
    },
    {
      key: "target",
      title: "去向表",
      minWidth: 180,
      render: (_value, row) => <span className="break-all">{row.target}</span>,
    },
    {
      key: "state",
      title: "采集状态",
      width: 100,
      render: (_value, row) => metricsStateText(row.metrics.metricsState),
    },
    {
      key: "read",
      title: "总读取",
      width: 100,
      align: "right",
      render: (_value, row) => countText(row.metrics.readRecords),
    },
    {
      key: "snapshot",
      title: "全量读取",
      width: 100,
      align: "right",
      render: (_value, row) => countText(row.metrics.snapshotRecords),
    },
    {
      key: "incremental",
      title: "增量读取",
      width: 100,
      align: "right",
      render: (_value, row) => countText(row.metrics.incrementalRecords),
    },
    {
      key: "insert",
      title: "INSERT",
      width: 90,
      align: "right",
      render: (_value, row) => countText(row.metrics.insertRecords),
    },
    {
      key: "update",
      title: "UPDATE",
      width: 90,
      align: "right",
      render: (_value, row) => countText(row.metrics.updateRecords),
    },
    {
      key: "delete",
      title: "DELETE",
      width: 90,
      align: "right",
      render: (_value, row) => countText(row.metrics.deleteRecords),
    },
    {
      key: "committed",
      title: "已提交",
      width: 100,
      align: "right",
      render: (_value, row) => (
        <span title="仅统计 JDBC Flush 成功后的记录；主键表可能等待 Checkpoint 或 Flush。">
          {countText(row.metrics.committedRecords)}
        </span>
      ),
    },
    {
      key: "ddl",
      title: "DDL",
      width: 80,
      align: "right",
      render: (_value, row) => countText(row.metrics.ddlEvents),
    },
  ];

  const collectedAt = metricsReport?.finalized
    ? metricsReport.finalCollectedAt
    : metricsReport?.latestCollectedAt;

  return (
    <div className="space-y-4">
      <Table
        columns={columns}
        dataSource={rows}
        rowKey="id"
        size="small"
        scroll={{ x: realtime ? 1400 : hasIncremental ? 1100 : 950 }}
        pagination={false}
        emptyText="暂无表映射"
      />
      {metricsReport ? (
        <section className="space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div className="text-xs font-medium text-[#344054]">CDC 指标</div>
            <div className="text-[11px] text-[#667085]">
              {metricsStateText(metricsReport.metricsState)}
              {" · "}
              采集序号{" "}
              {metricsReport.finalized
                ? metricsReport.finalSequence ?? "—"
                : metricsReport.latestSequence ?? "—"}
              {" · "}
              {collectedAt != null ? new Date(collectedAt).toLocaleString("zh-CN") : "尚无采集时间"}
            </div>
          </div>
          <div className="grid grid-cols-2 gap-2 md:grid-cols-4">
            <MetricsSummaryItem label="总读取" value={metricSnapshot?.readRecords} />
            <MetricsSummaryItem label="全量读取" value={metricSnapshot?.snapshotRecords} />
            <MetricsSummaryItem label="增量读取" value={metricSnapshot?.incrementalRecords} />
            <MetricsSummaryItem
              label="已提交"
              value={metricSnapshot?.committedRecords}
              title="仅统计 JDBC Flush 成功后的记录；主键表可能等待 Checkpoint 或 Flush。"
            />
          </div>
          {metricsReport.metricsError ? (
            <div className="text-xs text-[#b54708]" title={metricsReport.metricsError}>
              指标采集异常：{metricsReport.metricsError}
            </div>
          ) : null}
          <Table
            columns={metricsColumns}
            dataSource={metricRows}
            rowKey="id"
            size="small"
            scroll={{ x: 1220 }}
            pagination={false}
            emptyText="暂无表级指标"
          />
        </section>
      ) : null}
    </div>
  );
}

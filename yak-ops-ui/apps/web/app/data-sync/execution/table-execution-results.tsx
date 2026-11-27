import { Table, type TableColumns } from "@yak-ops/yak-ui";
import type {
  DataSyncInstanceRecord,
  DataSyncMetricsTableReport,
  DataSyncStageResult,
} from "@/service/data-sync/types";
import {
  findMetricsTableReport,
  formatMetricCount,
  selectMetricsSnapshot,
} from "@/service/data-sync/report";
import { StageProgress, useObservationStale } from "./execution-stages";

const pathText = (table: { database?: string; schema?: string; table?: string }) =>
  [table.database, table.schema, table.table].filter(Boolean).join(".");
const countText = formatMetricCount;

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


function MetricsMetaItem({
  label,
  value,
}: {
  label: string;
  value?: string | number | null;
}) {
  const display = value == null || value === "" ? "—" : String(value);
  return (
    <div className="min-w-0 border border-[#eef0f3] bg-white px-3 py-2">
      <div className="text-[11px] text-[#667085]">{label}</div>
      <div className="mt-1 truncate text-xs font-medium text-[#344054]" title={display}>
        {display}
      </div>
    </div>
  );
}

const collectedAtText = (value?: number | null) =>
  value == null || !Number.isFinite(value) ? "—" : new Date(value).toLocaleString("zh-CN");

export function DataSyncMetricsPanel({ record }: { record: DataSyncInstanceRecord }) {
  const metricsReport = record.metricsReport;
  const realtime = record.syncType === "REALTIME";
  if (!metricsReport && !record.metricsReportError && !realtime) return null;

  const mappings = record.definitionSnapshot?.tableMappings || [];
  const metricSnapshot = selectMetricsSnapshot(metricsReport);
  const metricRows: Array<{
    id: string;
    source: string;
    target: string;
    metrics: DataSyncMetricsTableReport;
  }> = (metricsReport?.tables || []).map((metrics, index) => {
    const mapping = mappings.find(
      (candidate) => findMetricsTableReport([metrics], candidate) === metrics,
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
    <section className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="text-xs font-medium text-[#344054]">CDC 指标</div>
        {metricsReport ? (
          <div className="text-[11px] text-[#667085]">
            {metricsStateText(metricsReport.metricsState)}
            {" · "}
            {metricsReport.finalized ? "最终快照" : "最新快照"}
            {" · "}
            采集序号{" "}
            {metricsReport.finalized
              ? metricsReport.finalSequence ?? "—"
              : metricsReport.latestSequence ?? "—"}
            {" · "}
            {collectedAtText(collectedAt)}
          </div>
        ) : (
          <div className="text-[11px] text-[#667085]">
            {record.metricsReportError ? "报告请求失败" : "暂无指标报告"}
          </div>
        )}
      </div>
      {metricsReport ? (
        <>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 xl:grid-cols-5">
            <MetricsSummaryItem label="总读取" value={metricSnapshot?.readRecords} />
            <MetricsSummaryItem label="全量读取" value={metricSnapshot?.snapshotRecords} />
            <MetricsSummaryItem label="增量读取" value={metricSnapshot?.incrementalRecords} />
            <MetricsSummaryItem label="INSERT" value={metricSnapshot?.insertRecords} />
            <MetricsSummaryItem label="UPDATE" value={metricSnapshot?.updateRecords} />
            <MetricsSummaryItem label="DELETE" value={metricSnapshot?.deleteRecords} />
            <MetricsSummaryItem
              label="已提交"
              value={metricSnapshot?.committedRecords}
              title="仅统计 JDBC Flush 成功后的记录；主键表可能等待 Checkpoint 或 Flush。"
            />
            <MetricsSummaryItem label="DDL 事件" value={metricSnapshot?.ddlEvents} />
            <MetricsSummaryItem label="错误记录" value={metricSnapshot?.errorRecords} />
          </div>
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 xl:grid-cols-5">
            <MetricsMetaItem label="Job ID" value={metricsReport.jobId} />
            <MetricsMetaItem label="部署模式" value={metricsReport.deploymentMode} />
            <MetricsMetaItem label="Cluster ID" value={metricsReport.clusterId} />
            <MetricsMetaItem label="Flink 状态" value={metricsReport.executionState} />
            <MetricsMetaItem label="最新序号" value={metricsReport.latestSequence} />
            <MetricsMetaItem label="最终序号" value={metricsReport.finalSequence} />
            <MetricsMetaItem
              label="最新采集时间"
              value={collectedAtText(metricsReport.latestCollectedAt)}
            />
            <MetricsMetaItem
              label="最终采集时间"
              value={collectedAtText(metricsReport.finalCollectedAt)}
            />
            <MetricsMetaItem label="最终报告" value={metricsReport.finalized ? "已固化" : "尚未固化"} />
          </div>
          {record.metricsReportError ? (
            <div className="text-xs text-[#b54708]" title={record.metricsReportError}>
              指标报告刷新失败，当前保留最近一次成功报告：{record.metricsReportError}
            </div>
          ) : null}
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
        </>
      ) : (
        <div className={record.metricsReportError ? "text-xs text-[#b42318]" : "text-xs text-[#667085]"}>
          {record.metricsReportError
            ? "指标报告获取失败：" + record.metricsReportError
            : "暂无 CDC 指标报告"}
        </div>
      )}
    </section>
  );
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
      metrics: findMetricsTableReport(metricsReport?.tables, mapping),
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
      title: metricsReport ? "总读取" : realtime ? "全量读取" : "读取条数",
      width: 105,
      align: "right",
      render: (_value, row) =>
        row.metrics
          ? countText(row.metrics.readRecords)
          : countText(realtime ? row.full?.readRows : (row.incremental || row.full)?.readRows),
    },
    {
      key: "written",
      title: metricsReport ? "已提交" : realtime ? "全量提交" : "已提交条数",
      width: 105,
      align: "right",
      render: (_value, row) =>
        row.metrics
          ? countText(row.metrics.committedRecords)
          : countText(
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
        render: (_value, row) => (
          <span title={scope}>
            {countText(row.metrics ? row.metrics.incrementalRecords : row.incremental?.readRows)}
          </span>
        ),
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
      <DataSyncMetricsPanel record={record} />
    </div>
  );
}
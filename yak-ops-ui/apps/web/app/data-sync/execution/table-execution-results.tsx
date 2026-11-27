import { Table, type TableColumns } from "@yak-ops/yak-ui";
import type { ReactNode } from "react";

import type {
  DataSyncInstanceRecord,
  DataSyncMetricsSnapshot,
  DataSyncStageResult,
  DataSyncTableMapping,
} from "@/service/data-sync/types";
import { findMetricsTableReport, formatMetricCount, selectMetricsSnapshot } from "@/service/data-sync/report";
import { StageProgress, useObservationStale } from "./execution-stages";

const pathText = (table: { database?: string; schema?: string; table?: string }) =>
  [table.database, table.schema, table.table].filter(Boolean).join(".") || "—";
const countText = formatMetricCount;

type TableMetricValues = Pick<
  DataSyncMetricsSnapshot,
  | "readRecords"
  | "snapshotRecords"
  | "incrementalRecords"
  | "insertRecords"
  | "updateRecords"
  | "deleteRecords"
  | "committedRecords"
  | "ddlEvents"
>;

interface TableExecutionRow {
  id: string;
  source: string;
  target: string;
  schema?: DataSyncStageResult;
  full?: DataSyncStageResult;
  incremental?: DataSyncStageResult;
  metrics?: TableMetricValues;
}

/** One row per configured table; CDC counters are columns in this same table. */
export function TableExecutionResults({
  record,
  statusIcon,
  duration,
}: {
  record: DataSyncInstanceRecord;
  statusIcon: ReactNode;
  duration: string;
}) {
  const metricsReport = record.metricsReport;
  const metricSnapshot = selectMetricsSnapshot(metricsReport);
  const realtime = record.syncType === "REALTIME";
  const snapshot = record.definitionSnapshot;
  const mappings: DataSyncTableMapping[] = snapshot?.tableMappings?.length
    ? snapshot.tableMappings
    : snapshot
      ? [{ source: snapshot.source, target: snapshot.target, writeMode: "UPSERT" }]
      : [];
  const report = record.executionReport;
  const stale = useObservationStale(record.status, report?.observedAt);
  const hasIncremental = report
    ? report.version === 1
      ? report.tables.some((table) => table.step === "INCREMENTAL")
      : report.stages.some((stage) => stage.step === "INCREMENTAL")
    : false;
  const hasFull = report
    ? report.version === 1
      ? report.tables.some((table) => table.step === "FULL")
      : report.stages.some((stage) => stage.step === "FULL")
    : false;
  const hasSchema = Boolean(report && record.steps.includes("SCHEMA"));

  const rows: TableExecutionRow[] = mappings.map((mapping, index) => {
    const stageTable =
      report && report.version !== 1
        ? report.tables.find((table) => table.mappingIndex === index)
        : undefined;
    const schema = stageTable?.stages.find((stage) => stage.step === "SCHEMA");
    let full = stageTable?.stages.find((stage) => stage.step === "FULL");
    let incremental = stageTable?.stages.find((stage) => stage.step === "INCREMENTAL");

    if (report?.version === 1) {
      const table = report.tables.find((item) => item.mappingIndex === index);
      if (table?.step === "INCREMENTAL") incremental = table;
      else full = table;
    }

    const matchedMetrics =
      findMetricsTableReport(metricsReport?.tables, mapping) ??
      (mappings.length === 1 && metricsReport?.tables.length === 1
        ? metricsReport.tables[0]
        : undefined);

    return {
      id: String(index),
      source: pathText(mapping.source),
      target: pathText(mapping.target),
      schema,
      full,
      incremental,
      metrics: matchedMetrics ?? (mappings.length === 1 ? metricSnapshot : undefined),
    };
  });

  // A report can still provide table rows when an older instance has no saved definition snapshot.
  if (rows.length === 0) {
    (metricsReport?.tables ?? []).forEach((metrics, index) => {
      rows.push({
        id: String(index),
        source: metrics.sourceTable || "—",
        target: metrics.sinkTable || "—",
        metrics,
      });
    });
  }

  const showCdcMetrics = realtime || Boolean(metricsReport);
  const columns: TableColumns<TableExecutionRow> = [
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
    ...(hasSchema
      ? [
          {
            key: "schema",
            title: "结构迁移",
            width: 150,
            render: (_value: unknown, row: TableExecutionRow) => (
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
                  label={row.source + " 结构迁移"}
                />
              </div>
            ),
          },
        ]
      : []),
    ...(hasFull
      ? [
          {
            key: "full",
            title: "全量进度",
            width: 150,
            render: (_value: unknown, row: TableExecutionRow) => (
              <StageProgress
                stage={row.full}
                status={record.status}
                stale={stale}
                label={row.source + " 全量进度"}
              />
            ),
          },
        ]
      : []),
    ...(hasIncremental
      ? [
          {
            key: "incremental",
            title: "增量状态",
            width: 150,
            render: (_value: unknown, row: TableExecutionRow) => (
              <StageProgress
                stage={row.incremental}
                status={record.status}
                stale={stale}
                label={row.source + " 增量状态"}
              />
            ),
          },
        ]
      : []),
    {
      key: "status",
      title: "执行状态",
      width: 75,
      align: "center",
      render: () => statusIcon,
    },
    {
      key: "readRecords",
      title: metricsReport ? "总读取" : realtime ? "全量读取" : "读取条数",
      width: 90,
      align: "right",
      render: (_value, row) =>
        countText(
          row.metrics
            ? row.metrics.readRecords
            : realtime
              ? row.full?.readRows
              : (row.incremental || row.full)?.readRows,
        ),
    },
  ];

  if (showCdcMetrics) {
    columns.push(
      {
        key: "snapshotRecords",
        title: "全量读取",
        width: 90,
        align: "right",
        render: (_value, row) =>
          countText(row.metrics ? row.metrics.snapshotRecords : row.full?.readRows),
      },
      {
        key: "incrementalRecords",
        title: "增量读取",
        width: 90,
        align: "right",
        render: (_value, row) =>
          countText(row.metrics ? row.metrics.incrementalRecords : row.incremental?.readRows),
      },
      {
        key: "insertRecords",
        title: "INSERT",
        width: 75,
        align: "right",
        render: (_value, row) => countText(row.metrics?.insertRecords),
      },
      {
        key: "updateRecords",
        title: "UPDATE",
        width: 75,
        align: "right",
        render: (_value, row) => countText(row.metrics?.updateRecords),
      },
      {
        key: "deleteRecords",
        title: "DELETE",
        width: 75,
        align: "right",
        render: (_value, row) => countText(row.metrics?.deleteRecords),
      },
    );
  }

  columns.push(
    {
      key: "committedRecords",
      title: metricsReport ? "已提交条数" : realtime ? "全量提交" : "已提交条数",
      width: 90,
      align: "right",
      render: (_value, row) => (
        <span title="仅统计 JDBC Flush 成功后的记录；主键表可能等待 Checkpoint 或 Flush。">
          {countText(
            row.metrics
              ? row.metrics.committedRecords
              : realtime
                ? row.full?.committedRows
                : (row.incremental || row.full)?.committedRows,
          )}
        </span>
      ),
    },
  );

  if (rows.some((row) => row.schema?.failureCode || row.full?.failureCode || row.incremental?.failureCode)) {
    columns.push({
      key: "failure",
      title: "失败原因",
      minWidth: 120,
      render: (_value, row) => {
        const failure = row.schema?.failureCode || row.incremental?.failureCode || row.full?.failureCode;
        if (failure === "SCHEMA_PREPARATION_FAILED") return "目标结构准备失败";
        if (failure === "READ_FAILED") return "来源读取失败";
        if (failure === "WRITE_FAILED") return "目标写入失败";
        return "—";
      },
    });
  }

  if (showCdcMetrics) {
    columns.push({
      key: "ddlEvents",
      title: "DDL",
      width: 70,
      align: "right",
      render: (_value, row) => countText(row.metrics?.ddlEvents),
    });
  }

  columns.push(
    {
      key: "startTime",
      title: "开始时间",
      width: 160,
      render: () => record.startTime || "—",
    },
    {
      key: "finishTime",
      title: "完成时间",
      width: 160,
      render: () => record.finishTime || "—",
    },
    {
      key: "duration",
      title: "耗时",
      width: 80,
      render: () => duration,
    },
  );

  return (
    <Table
      columns={columns}
      dataSource={rows}
      rowKey="id"
      size="small"
      scroll={{
        x:
          (showCdcMetrics ? 1600 : 1180) +
          (hasSchema ? 150 : 0) +
          (hasFull ? 150 : 0) +
          (hasIncremental ? 150 : 0),
      }}
      pagination={false}
      emptyText="暂无表级执行数据"
    />
  );
}

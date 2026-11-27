import { Table, type TableColumns } from "@yak-ops/yak-ui";
import type { ReactNode } from "react";

import type {
  DataSyncInstanceRecord,
  DataSyncMetricsSnapshot,
  DataSyncStageResult,
  DataSyncTableMapping,
} from "@/service/data-sync/types";
import { findMetricsTableReport, formatMetricCount, selectMetricsSnapshot } from "@/service/data-sync/report";

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

  const rows: TableExecutionRow[] = mappings.map((mapping, index) => {
    const stageTable =
      report && report.version !== 1
        ? report.tables.find((table) => table.mappingIndex === index)
        : undefined;
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
      title: metricsReport ? "已提交" : realtime ? "全量提交" : "已提交条数",
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
      scroll={{ x: showCdcMetrics ? 1550 : 1180 }}
      pagination={false}
      emptyText="暂无表级执行数据"
    />
  );
}

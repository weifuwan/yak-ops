import { Table, type TableColumns } from "@yak-ops/yak-ui";
import type { DataSyncInstanceRecord, DataSyncStageResult } from "@/service/data-sync/types";
import { StageProgress, useObservationStale } from "./execution-stages";

const pathText = (table: { database?: string; schema?: string; table?: string }) =>
  [table.database, table.schema, table.table].filter(Boolean).join(".");
const countText = (value?: number | null) => value?.toLocaleString("zh-CN") ?? "—";

export function TableExecutionResults({ record }: { record: DataSyncInstanceRecord }) {
  const report = record.executionReport;
  const stale = useObservationStale(record.status, report?.observedAt);
  const realtime = record.steps.includes("INCREMENTAL");
  const mappings = record.definitionSnapshot?.tableMappings || [];
  const rows = mappings.map((mapping, index) => {
    let full: DataSyncStageResult | undefined;
    let incremental: DataSyncStageResult | undefined;
    if (report?.version === 2) {
      const result = report.tables.find((table) => table.mappingIndex === index);
      full = result?.stages.find((stage) => stage.step === "FULL");
      incremental = result?.stages.find((stage) => stage.step === "INCREMENTAL");
    } else {
      full = report?.tables.find((table) => table.mappingIndex === index);
    }
    return {
      id: String(index),
      source: pathText(mapping.source),
      target: pathText(mapping.target),
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
    {
      key: "full",
      title: "全量进度",
      width: 160,
      render: (_value, row) => (
        <StageProgress
          stage={row.full}
          status={record.status}
          stale={stale}
          label={`${row.source} 全量进度`}
        />
      ),
    },
  ];
  if (realtime)
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
      render: (_value, row) => countText(row.full?.readRows),
    },
    {
      key: "written",
      title: realtime ? "全量提交" : "已提交条数",
      width: 105,
      align: "right",
      render: (_value, row) => countText(row.full?.committedRows),
    },
  );
  if (realtime) {
    const scope =
      report?.version === 2
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
      const failure = row.incremental?.failureCode || row.full?.failureCode;
      if (failure === "READ_FAILED") return "来源读取失败";
      if (failure === "WRITE_FAILED") return "目标写入失败";
      return "—";
    },
  });
  return (
    <Table
      columns={columns}
      dataSource={rows}
      rowKey="id"
      size="small"
      scroll={{ x: realtime ? 1400 : 950 }}
      pagination={false}
      emptyText="暂无表映射"
    />
  );
}

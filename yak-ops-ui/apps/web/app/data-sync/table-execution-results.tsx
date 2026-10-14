import { Table, type TableColumns } from "@yak-ops/yak-ui";
import { useEffect, useState } from "react";
import type { DataSyncInstanceRecord, DataSyncInstanceStatus } from "@/service/data-sync";
import { ExecutionProgressBar } from "./execution-progress-bar";

const STATE_LABELS = {
  WAITING: "等待",
  RUNNING: "运行中",
  FINISHED: "成功",
  FAILED: "失败",
  CANCELED: "已停止",
  UNKNOWN: "未取得状态",
};
const INSTANCE_STATES: Record<string, DataSyncInstanceStatus> = {
  WAITING: "SUBMITTED",
  RUNNING: "RUNNING",
  FINISHED: "FINISHED",
  FAILED: "FAILED",
  CANCELED: "CANCELED",
};
const pathText = (table: { database?: string; schema?: string; table?: string }) =>
  [table.database, table.schema, table.table].filter(Boolean).join(".");

export function TableExecutionResults({ record }: { record: DataSyncInstanceRecord }) {
  const [now, setNow] = useState(Date.now());
  const active = ["RUNNING", "LAUNCHING", "CANCELLING", "RECONCILING"].includes(record.status);
  useEffect(() => {
    if (!active) return;
    const timer = window.setInterval(() => setNow(Date.now()), 2000);
    return () => window.clearInterval(timer);
  }, [active]);
  const report = record.executionReport;
  const stale =
    active &&
    (!report || now - Date.parse(report.observedAt) > 10000 || record.status === "RECONCILING");
  const mappings = record.definitionSnapshot?.tableMappings || [];
  const rows = mappings.map((mapping, index) => {
    const result = report?.tables.find((table) => table.mappingIndex === index);
    let status: keyof typeof STATE_LABELS = result?.status || "UNKNOWN";
    if (stale) status = "UNKNOWN";
    return {
      id: String(index),
      source: pathText(mapping.source),
      target: pathText(mapping.target),
      status,
      result,
    };
  });
  const columns: TableColumns<(typeof rows)[number]> = [
    {
      key: "source",
      title: "来源表",
      minWidth: 190,
      render: (_value, row) => <span className="break-all">{row.source}</span>,
    },
    {
      key: "target",
      title: "去向表",
      minWidth: 190,
      render: (_value, row) => <span className="break-all">{row.target}</span>,
    },
    {
      key: "progress",
      title: "全量进度",
      width: 170,
      render: (_value, row) =>
        row.status === "UNKNOWN" ? (
          <span title={stale ? "观测已过期" : "未取得表级观测"}>—</span>
        ) : (
          <ExecutionProgressBar
            status={INSTANCE_STATES[row.status]}
            percent={row.result?.progressPercent}
            realtime={false}
            ariaLabel={`${row.source} 全量进度`}
          />
        ),
    },
    { key: "status", title: "状态", width: 90, render: (_value, row) => STATE_LABELS[row.status] },
    {
      key: "read",
      title: "读取条数",
      width: 100,
      align: "right",
      render: (_value, row) => row.result?.readRows.toLocaleString("zh-CN") ?? "—",
    },
    {
      key: "written",
      title: "已提交条数",
      width: 110,
      align: "right",
      render: (_value, row) => row.result?.committedRows.toLocaleString("zh-CN") ?? "—",
    },
    {
      key: "failure",
      title: "失败原因",
      minWidth: 120,
      render: (_value, row) =>
        row.result?.failureCode === "READ_FAILED"
          ? "来源读取失败"
          : row.result?.failureCode === "WRITE_FAILED"
            ? "目标写入失败"
            : "—",
    },
  ];
  return (
    <Table
      columns={columns}
      dataSource={rows}
      rowKey="id"
      size="small"
      scroll={{ x: 1000 }}
      pagination={false}
      emptyText="暂无表映射"
    />
  );
}

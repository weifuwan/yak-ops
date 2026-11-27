import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";
import {
  findMetricsTableReport,
  formatMetricCount,
  selectMetricsSnapshot,
} from "../apps/web/service/data-sync/report.ts";

const root = join(dirname(fileURLToPath(import.meta.url)), "../apps/web");
const tableResults = readFileSync(
  join(root, "app/data-sync/execution/table-execution-results.tsx"),
  "utf8",
);
const executionDetail = readFileSync(
  join(root, "app/data-sync/execution/execution-detail.tsx"),
  "utf8",
);
const executionHistory = readFileSync(
  join(root, "app/data-sync/detail/use-execution-history.ts"),
  "utf8",
);

test("nullable metric values remain distinct from a measured zero", () => {
  assert.equal(formatMetricCount(null), "—");
  assert.equal(formatMetricCount(undefined), "—");
  assert.equal(formatMetricCount(0), "0");
  assert.equal(formatMetricCount(1234), "1,234");
});


test("FINAL metrics snapshot is preferred only after the report is finalized", () => {
  const latest = { readRecords: 2000 };
  const final = { readRecords: 2400 };
  assert.equal(
    selectMetricsSnapshot({ finalized: true, latestSnapshot: latest, finalSnapshot: final }),
    final,
  );
  assert.equal(
    selectMetricsSnapshot({ finalized: false, latestSnapshot: latest, finalSnapshot: final }),
    latest,
  );
  assert.equal(selectMetricsSnapshot(null), undefined);
});

test("table metrics are matched by source and sink path, ignoring case", () => {
  const mapping = {
    source: { database: "yak_e2e_source", table: "pk_table" },
    target: { database: "yak_e2e_target", table: "pk_table_target" },
    writeMode: "UPSERT",
  };
  const matched = {
    sourceTable: "YAK_E2E_SOURCE.pk_table",
    sinkTable: "yak_e2e_target.pk_table_target",
    metricsState: "AVAILABLE",
    readRecords: 2000,
    snapshotRecords: 2000,
    incrementalRecords: 0,
    insertRecords: 0,
    updateRecords: 0,
    deleteRecords: 0,
    committedRecords: 0,
    ddlEvents: 0,
  };
  assert.equal(findMetricsTableReport([matched], mapping), matched);
  assert.equal(findMetricsTableReport([], mapping), undefined);

  const sharedSourceReports = [
    { ...matched, sinkTable: "sink_a.orders_a" },
    { ...matched, sinkTable: "sink_b.orders_b" },
  ];
  const secondMapping = {
    source: mapping.source,
    target: { database: "sink_b", table: "orders_b" },
    writeMode: "UPSERT",
  };
  assert.equal(findMetricsTableReport(sharedSourceReports, secondMapping), sharedSourceReports[1]);

  const ambiguousMapping = {
    source: mapping.source,
    target: { database: "missing_sink", table: "missing_table" },
    writeMode: "UPSERT",
  };
  assert.equal(findMetricsTableReport(sharedSourceReports, ambiguousMapping), undefined);
});

test("CDC metrics render in both execution-detail branches and populate the quick-count columns", () => {
  assert.match(executionDetail, /readRows: metrics \? metrics\.readRecords/);
  assert.match(executionDetail, /writeRows: metrics \? metrics\.committedRecords/);
  assert.match(executionDetail, /<DataSyncMetricsPanel record=\{record\} \/>/);
  assert.match(tableResults, /row\.metrics\s*\?\s*countText\(row\.metrics\.readRecords\)/);
  assert.match(tableResults, /row\.metrics\s*\?\s*countText\(row\.metrics\.committedRecords\)/);
  assert.match(tableResults, /record\.scope === "DATABASE" && metricsReport/);
  assert.match(executionDetail, /title: "已提交条数"/);
});

test("all execution-level CDC counters and report metadata are visible", () => {
  for (const label of [
    'label="总读取"',
    'label="全量读取"',
    'label="增量读取"',
    'label="INSERT"',
    'label="UPDATE"',
    'label="DELETE"',
    'label="已提交"',
    'label="DDL 事件"',
    'label="错误记录"',
  ]) {
    assert.ok(tableResults.includes(label), "missing metric summary: " + label);
  }
  for (const field of [
    "metricsReport.jobId",
    "metricsReport.deploymentMode",
    "metricsReport.clusterId",
    "metricsReport.executionState",
    "metricsReport.latestSequence",
    "metricsReport.finalSequence",
    "metricsReport.latestCollectedAt",
    "metricsReport.finalCollectedAt",
    "metricsReport.finalized",
  ]) {
    assert.ok(tableResults.includes(field), "missing report metadata: " + field);
  }
});

test("metrics report request errors stay isolated from instance detail and retain the last good report", () => {
  assert.match(executionHistory, /previousMetricsReport/);
  assert.match(executionHistory, /metricsReportError: metricsResult\.error/);
  assert.match(executionHistory, /catch\(\(error: unknown\) =>/);
  assert.doesNotMatch(executionHistory, /getDataSyncInstanceReport\(executionId!\)\.catch\(\(\) => undefined\)/);
});

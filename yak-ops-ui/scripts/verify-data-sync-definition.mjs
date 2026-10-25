import assert from "node:assert/strict";
import {
  buildSyncParameters,
  createDefinitionRequest,
  definitionPageRequest,
  toSyncTaskRecord,
  updateDefinitionRequest,
} from "../apps/web/service/data-sync/definition.ts";
import { readSyncParameters } from "../apps/web/service/data-sync/parameters.ts";

const common = {
  name: "orders",
  debug: false,
  sourceDataSourceId: "mysql-source",
  sourceTable: "orders",
  targetDataSourceId: "pg-target",
  targetSchema: "public",
  targetTable: "ods_orders",
};
const offline = {
  ...common,
  solutionType: "SINGLE_TABLE_OFFLINE",
  steps: ["FULL"],
  writeMode: "APPEND",
};
const realtime = {
  ...common,
  debug: true,
  solutionType: "SINGLE_TABLE_REALTIME",
  steps: ["FULL", "INCREMENTAL"],
  writeMode: "UPSERT",
};
const created = createDefinitionRequest(realtime);
const json = JSON.parse(created.parameters);
assert.deepEqual(json, {
  solutionType: "SINGLE_TABLE_REALTIME",
  steps: ["FULL", "INCREMENTAL"],
  schemaChangesEnabled: true,
  allowDropColumns: false,
  source: { datasourceId: "mysql-source" },
  sink: { datasourceId: "pg-target" },
  tableSelection: { mode: "EXPLICIT" },
  tableMappings: [
    {
      source: { table: "orders" },
      target: { schema: "public", table: "ods_orders" },
      writeMode: "UPSERT",
    },
  ],
});
assert.equal(created.taskType, "DATA_SYNC");
assert.equal(created.debug, true);
assert.equal(json.debug, undefined);
assert.equal(json.source.type, undefined);
assert.equal("parametersFormat" in created, false);
assert.equal(buildSyncParameters(offline).tableMappings[0].writeMode, "APPEND");
const keyless = createDefinitionRequest({
  ...realtime,
  targetDataSourceId: "mysql-target",
  targetSchema: undefined,
  writeMode: "RETRACT",
  keylessEnabled: true,
  chunkKeyColumn: "bucket",
  schemaChangesEnabled: false,
}).parameters;
assert.equal(readSyncParameters(3, keyless).tableMappings[0].source.chunkKeyColumn, "bucket");
assert.equal(
  toSyncTaskRecord({
    id: "keyless",
    name: "keyless",
    taskType: "DATA_SYNC",
    status: "UNPUBLISHED",
    definitionVersion: 1,
    parameterSchemaVersion: 3,
    parameters: keyless,
  }).keylessEnabled,
  true,
);
for (const invalid of [
  { ...JSON.parse(keyless), schemaChangesEnabled: true },
  { ...JSON.parse(keyless), steps: ["SCHEMA", "FULL", "INCREMENTAL"] },
  {
    ...JSON.parse(keyless),
    tableMappings: [{ ...JSON.parse(keyless).tableMappings[0], writeMode: "UPSERT" }],
  },
]) {
  assert.throws(() => readSyncParameters(3, JSON.stringify(invalid)));
}
const metadata = updateDefinitionRequest({ name: "renamed", remark: "remark", debug: true });
assert.equal(metadata.parameters, undefined);
assert.equal(metadata.debug, true);
assert.equal(
  JSON.parse(updateDefinitionRequest(offline).parameters).tableMappings[0].source.table,
  "orders",
);
assert.deepEqual(definitionPageRequest({ pageNo: 1, pageSize: 20 }).parameterFilters, {});
assert.deepEqual(
  definitionPageRequest({ pageNo: 1, pageSize: 20, solutionType: "SINGLE_TABLE_REALTIME" })
    .parameterFilters,
  { solutionType: "SINGLE_TABLE_REALTIME" },
);
assert.deepEqual(
  definitionPageRequest({
    pageNo: 1,
    pageSize: 20,
    syncType: "OFFLINE",
    sourceDataSourceId: "mysql-source",
    targetDataSourceId: "pg-target",
  }).parameterFilters,
  { sourceType: "JDBC", sourceDataSourceId: "mysql-source", targetDataSourceId: "pg-target" },
);
const definition = {
  id: "task",
  name: "orders",
  taskType: "DATA_SYNC",
  status: "UNPUBLISHED",
  definitionVersion: 5,
  parameterSchemaVersion: 3,
  parameters: created.parameters,
};
const result = toSyncTaskRecord(definition);
assert.equal(result.solutionType, "SINGLE_TABLE_REALTIME");
assert.equal(result.syncType, "REALTIME");
assert.deepEqual(result.steps, ["FULL", "INCREMENTAL"]);
assert.equal(result.targetSchema, "public");
assert.equal(result.definitionVersion, 5);
const oldParameters = JSON.stringify({
  source: { type: "MYSQL_CDC", datasourceId: "mysql-source", table: "orders" },
  transform: [],
  sink: {
    type: "JDBC",
    datasourceId: "pg-target",
    schema: "public",
    table: "ods_orders",
    writeMode: "UPSERT",
  },
});
const oldDefinition = { ...definition, parameterSchemaVersion: 2, parameters: oldParameters };
const oldRecord = toSyncTaskRecord(oldDefinition);
assert.equal(oldRecord.schemaChangesEnabled, false);
assert.equal(result.schemaChangesEnabled, true);
const disabledJson = createDefinitionRequest({
  ...realtime,
  schemaChangesEnabled: false,
}).parameters;
assert.equal(readSyncParameters(3, disabledJson).schemaChangesEnabled, false);
const missingPolicy = { ...json };
delete missingPolicy.schemaChangesEnabled;
assert.equal(readSyncParameters(3, JSON.stringify(missingPolicy)).schemaChangesEnabled, undefined);
const invalidPolicy = { ...json, schemaChangesEnabled: "false" };
assert.throws(() => readSyncParameters(3, JSON.stringify(invalidPolicy)));
const invalidOfflinePolicy = { ...buildSyncParameters(offline), schemaChangesEnabled: true };
assert.throws(() => readSyncParameters(3, JSON.stringify(invalidOfflinePolicy)));
assert.equal(buildSyncParameters(offline).schemaChangesEnabled, undefined);
const dropEnabled = buildSyncParameters({ ...realtime, allowDropColumns: true });
assert.equal(dropEnabled.allowDropColumns, true);
assert.equal(buildSyncParameters(realtime).allowDropColumns, false);
assert.equal(
  toSyncTaskRecord({ ...definition, parameters: JSON.stringify(dropEnabled) }).allowDropColumns,
  true,
);
assert.throws(() =>
  readSyncParameters(3, JSON.stringify({ ...dropEnabled, schemaChangesEnabled: false })),
);
assert.throws(() =>
  readSyncParameters(3, JSON.stringify({ ...dropEnabled, allowDropColumns: "true" })),
);
assert.throws(() =>
  readSyncParameters(
    3,
    JSON.stringify({ ...buildSyncParameters(offline), allowDropColumns: true }),
  ),
);
assert.equal(oldDefinition.parameters, oldParameters);
assert.throws(() => readSyncParameters(1, oldParameters));
assert.throws(() => readSyncParameters(3, oldParameters));
assert.throws(() => readSyncParameters(2, created.parameters));
for (const patch of [
  { solutionType: "UNSUPPORTED_SOLUTION" },
  { steps: ["FULL"] },
  { steps: ["FULL", "FULL"] },
  { steps: ["SCHEMA"] },
  { steps: ["SCHEMA", "FULL", "INCREMENTAL", "SCHEMA"] },
  { tableSelection: { mode: "RULES" } },
  { tableMappings: [] },
  { tableMappings: [...json.tableMappings, ...json.tableMappings] },
  { source: { ...json.source, type: "JDBC" } },
  { tableMappings: [{ ...json.tableMappings[0], writeMode: "APPEND" }] },
])
  assert.throws(() => readSyncParameters(3, JSON.stringify({ ...json, ...patch })));
assert.throws(() => toSyncTaskRecord({ ...definition, taskType: "UNKNOWN" }));
console.log("Data Sync v3 and pinned-v2 definition contracts passed");

const batch = {
  ...offline,
  solutionType: "DATABASE_OFFLINE",
  tableMappings: [
    {
      source: { table: "orders" },
      target: { schema: "public", table: "orders_copy" },
      writeMode: "APPEND",
    },
    {
      source: { table: "users" },
      target: { schema: "public", table: "users_copy" },
      writeMode: "UPSERT",
    },
  ],
};
const batchJson = createDefinitionRequest(batch).parameters;
assert.equal(readSyncParameters(3, batchJson).tableMappings.length, 2);
assert.deepEqual(
  toSyncTaskRecord({ ...definition, parameters: batchJson }).tableMappings.map(
    (row) => row.target.table,
  ),
  ["orders_copy", "users_copy"],
);
assert.throws(() => buildSyncParameters({ ...batch, tableMappings: [] }));
assert.throws(() => readSyncParameters(3, batchJson.replace("users_copy", "orders_copy")));
assert.throws(() =>
  readSyncParameters(3, batchJson.replace("DATABASE_OFFLINE", "SINGLE_TABLE_OFFLINE")),
);
const offlineIncremental = {
  ...offline,
  writeMode: "UPSERT",
  steps: ["INCREMENTAL"],
  incremental: {
    mode: "TIMESTAMP",
    column: "updated_at",
    initialValue: "2026-10-01T00:00:00",
    delaySeconds: 120,
    lookbackSeconds: 600,
  },
};
const incrementalJson = createDefinitionRequest(offlineIncremental).parameters;
const projectedIncremental = toSyncTaskRecord({ ...definition, parameters: incrementalJson });
assert.deepEqual(projectedIncremental.incremental, offlineIncremental.incremental);
assert.equal(readSyncParameters(3, incrementalJson).steps[0], "INCREMENTAL");
assert.throws(() => readSyncParameters(3, incrementalJson.replace('"UPSERT"', '"APPEND"')));
assert.throws(() => readSyncParameters(3, incrementalJson.replace('"updated_at"', '""')));
assert.throws(() => readSyncParameters(3, incrementalJson.replace('"2026-10-01T00:00:00"', '""')));
const initialLoadIncremental = createDefinitionRequest({
  ...offlineIncremental,
  steps: ["SCHEMA", "FULL", "INCREMENTAL"],
  incremental: { mode: "TIMESTAMP", column: "updated_at", delaySeconds: 120, lookbackSeconds: 600 },
}).parameters;
assert.deepEqual(readSyncParameters(3, initialLoadIncremental).steps, [
  "SCHEMA",
  "FULL",
  "INCREMENTAL",
]);
assert.equal(
  readSyncParameters(3, initialLoadIncremental).tableMappings[0].incremental.column,
  "updated_at",
);
assert.throws(() => readSyncParameters(3, incrementalJson.replace('"INCREMENTAL"', '"SCHEMA"')));
console.log("Explicit multi-table and bounded incremental parameter contracts passed");

const databaseRealtime = {
  ...batch,
  solutionType: "DATABASE_REALTIME",
  steps: ["FULL", "INCREMENTAL"],
  tableMappings: batch.tableMappings.map((row) => ({ ...row, writeMode: "UPSERT" })),
};
const continuousJson = createDefinitionRequest(databaseRealtime).parameters;
assert.equal(readSyncParameters(3, continuousJson).tableMappings.length, 2);
assert.equal(toSyncTaskRecord({ ...definition, parameters: continuousJson }).syncType, "REALTIME");
assert.equal(
  readSyncParameters(
    3,
    createDefinitionRequest({
      ...databaseRealtime,
      tableMappings: databaseRealtime.tableMappings.slice(0, 1),
    }).parameters,
  ).solutionType,
  "DATABASE_REALTIME",
);
assert.throws(() => readSyncParameters(3, continuousJson.replace("UPSERT", "APPEND")));
assert.throws(() =>
  readSyncParameters(3, continuousJson.replace('["FULL","INCREMENTAL"]', '["INCREMENTAL"]')),
);

const { readExecutionReport, readExecutionSummary } =
  await import("../apps/web/service/data-sync/report.ts");
const stage = (step, status, percent) => ({
  step,
  status,
  progressPercent: percent,
  progressUnit: step === "FULL" ? "SPLITS" : "RECORDS",
  readRows: 2,
  committedRows: 2,
  plannedSplits: step === "FULL" ? 1 : null,
  completedSplits: step === "FULL" ? 1 : null,
  failureCode: null,
});
const summary = {
  version: 2,
  observedAt: new Date().toISOString(),
  generation: 1,
  restored: false,
  counterScope: "GENERATION",
  phase: "STREAMING",
  checkpoints: { handoffId: 5, completedId: 7 },
  stages: [stage("FULL", "FINISHED", 100), stage("INCREMENTAL", "RUNNING", null)],
};
const observed = {
  ...summary,
  tables: [0, 1].map((mappingIndex) => ({ mappingIndex, stages: summary.stages })),
  summary,
};
assert.deepEqual(readExecutionSummary(JSON.stringify(summary)), summary);
assert.equal(readExecutionReport(JSON.stringify(observed), 2).tables.length, 2);
assert.equal(readExecutionReport(JSON.stringify(summary), 2), undefined);
assert.equal(readExecutionReport(JSON.stringify(observed), 3), undefined);
assert.equal(
  readExecutionReport(
    JSON.stringify({ ...observed, tables: [observed.tables[0], observed.tables[0]] }),
    2,
  ),
  undefined,
);
assert.equal(
  readExecutionSummary(
    JSON.stringify({
      ...summary,
      stages: [summary.stages[0], { ...summary.stages[1], progressPercent: 50 }],
    }),
  ),
  undefined,
);
assert.equal(
  readExecutionSummary(
    JSON.stringify({
      ...summary,
      stages: [summary.stages[0], { ...summary.stages[1], readRows: -1 }],
    }),
  ),
  undefined,
);
const restored = {
  ...observed,
  generation: 2,
  restored: true,
  stages: [
    {
      ...summary.stages[0],
      readRows: null,
      committedRows: null,
      plannedSplits: null,
      completedSplits: null,
    },
    summary.stages[1],
  ],
};
assert.equal(readExecutionReport(JSON.stringify(restored), 2).generation, 2);
console.log(
  "Database realtime parameters, stage summaries, restored and invalid observation contracts passed",
);

assert.deepEqual(
  readSyncParameters(3, JSON.stringify({ ...json, steps: ["SCHEMA", "FULL", "INCREMENTAL"] }))
    .steps,
  ["SCHEMA", "FULL", "INCREMENTAL"],
);

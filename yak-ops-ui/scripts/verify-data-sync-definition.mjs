import assert from "node:assert/strict";
import {
  buildSyncParameters,
  createDefinitionRequest,
  definitionPageRequest,
  projectSyncTaskPage,
  toSyncTaskRecord,
  updateDefinitionRequest,
} from "../apps/web/service/data-sync/definition.ts";
import {
  displaySyncSteps,
  parseSyncParameters,
  readSyncParameters,
  syncTableMappings,
  validateSyncParameters,
} from "../apps/web/service/data-sync/parameters.ts";

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
const json = created.parameters;
assert.equal(created.taskType, "DATA_SYNC");
assert.equal(created.debug, true);
assert.equal(typeof created.parameters, "object");
assert.equal(json.debug, undefined);
assert.deepEqual(Object.keys(json), [
  "solutionType",
  "steps",
  "source",
  "transform",
  "sink",
  "env",
]);
assert.deepEqual(json.steps, ["FULL", "INCREMENTAL"]);
assert.deepEqual(json.transform, []);
assert.deepEqual(json.env, {});
assert.equal(json.source.datasourceId, "mysql-source");
assert.equal(json.source.tables.length, 1);
assert.equal(json.source.tables[0].table, "orders");
assert.equal(json.sink.tables[0].input, json.source.tables[0].id);
assert.equal(json.sink.tables[0].table, "ods_orders");
assert.equal(json.sink.tables[0].write.mode, "UPSERT");
assert.equal(json.sink.schemaPolicy.initial, "VERIFY");
assert.equal(json.sink.schemaPolicy.evolution.enabled, true);
assert.equal("tableMappings" in json, false);
assert.equal("tableSelection" in json, false);
assert.equal("schemaChangesEnabled" in json, false);
assert.equal("type" in json.source, false);
assert.equal(buildSyncParameters(offline).sink.tables[0].write.mode, "APPEND");
assert.equal(readSyncParameters(4, json), json);
assert.deepEqual(displaySyncSteps(json), ["FULL", "INCREMENTAL"]);
assert.equal(syncTableMappings(json)[0].source.table, "orders");

const record = {
  id: "d-1",
  name: "orders",
  taskType: "DATA_SYNC",
  status: "UNPUBLISHED",
  debug: true,
  definitionVersion: 1,
  parameterSchemaVersion: 4,
  parameters: json,
};
const projected = toSyncTaskRecord(record);
assert.equal(projected.sourceDataSourceId, "mysql-source");
assert.equal(projected.targetDataSourceId, "pg-target");
assert.equal(projected.tableMappings[0].writeMode, "UPSERT");
assert.equal(projected.syncType, "REALTIME");

const keyless = createDefinitionRequest({
  ...realtime,
  targetDataSourceId: "mysql-target",
  targetSchema: undefined,
  writeMode: "RETRACT",
  keylessEnabled: true,
  chunkKeyColumn: "bucket",
  schemaChangesEnabled: false,
}).parameters;
assert.equal(keyless.source.tables[0].snapshot.chunkKeyColumn, "bucket");
assert.equal(keyless.sink.tables[0].write.transaction.mode, "CHECKPOINT_XA");
assert.equal(keyless.sink.schemaPolicy.evolution.enabled, false);
assert.equal(readSyncParameters(4, keyless), keyless);
assert.equal(toSyncTaskRecord({ ...record, parameters: keyless }).keylessEnabled, true);
assert.throws(() => readSyncParameters(3, keyless));
assert.throws(() => readSyncParameters(2, keyless));
assert.throws(() => parseSyncParameters(4, "{"));
assert.throws(() => readSyncParameters(4, { ...keyless, tableMappings: [] }));
assert.throws(() =>
  readSyncParameters(4, {
    ...keyless,
    source: { ...keyless.source, tables: [{ ...keyless.source.tables[0], xa: true }] },
  }),
);
assert.throws(() =>
  readSyncParameters(4, {
    ...keyless,
    sink: {
      ...keyless.sink,
      tables: keyless.sink.tables.map((table) => ({
        ...table,
        write: { mode: "RETRACT" },
      })),
    },
  }),
);
assert.throws(() =>
  readSyncParameters(4, {
    ...keyless,
    sink: {
      ...keyless.sink,
      schemaPolicy: {
        ...keyless.sink.schemaPolicy,
        evolution: { enabled: true, allowDropColumns: false },
      },
    },
  }),
);
const upsertKeyless = createDefinitionRequest({
  ...realtime,
  keylessEnabled: true,
  chunkKeyColumn: "bucket",
  schemaChangesEnabled: false,
  writeMode: "UPSERT",
  targetPrimaryKeys: ["bucket"],
}).parameters;
assert.deepEqual(upsertKeyless.sink.tables[0].write.primaryKeys, ["bucket"]);
assert.equal(upsertKeyless.sink.tables[0].write.transaction, undefined);
assert.equal(upsertKeyless.source.tables[0].snapshot.chunkKeyColumn, "bucket");
assert.throws(() =>
  validateSyncParameters({
    ...upsertKeyless,
    sink: {
      ...upsertKeyless.sink,
      tables: upsertKeyless.sink.tables.map((table) => ({
        ...table,
        write: { ...table.write, primaryKeys: ["bucket", "bucket"] },
      })),
    },
  }),
);

const prepared = createDefinitionRequest({
  ...offline,
  steps: ["SCHEMA", "FULL"],
}).parameters;
assert.deepEqual(prepared.steps, ["FULL"]);
assert.equal(prepared.sink.schemaPolicy.initial, "CREATE_OR_ALTER");
assert.throws(() =>
  readSyncParameters(4, {
    ...prepared,
    sink: {
      ...prepared.sink,
      tables: prepared.sink.tables.map((table) => ({
        ...table,
        schemaPolicy: prepared.sink.schemaPolicy,
      })),
    },
  }),
);
const sinkWithoutPolicy = { ...prepared.sink };
delete sinkWithoutPolicy.schemaPolicy;
assert.throws(() => readSyncParameters(4, { ...prepared, sink: sinkWithoutPolicy }));
assert.deepEqual(displaySyncSteps(prepared), ["SCHEMA", "FULL"]);
assert.throws(() =>
  readSyncParameters(4, {
    ...prepared,
    steps: ["SCHEMA", "FULL"],
  }),
);
assert.throws(() =>
  readSyncParameters(4, {
    ...prepared,
    steps: ["INCREMENTAL"],
  }),
);
assert.throws(() =>
  readSyncParameters(4, {
    ...prepared,
    env: { "execution.debug": true },
  }),
);
const metadata = updateDefinitionRequest({ name: "rename", remark: "remark", debug: false });
assert.equal("parameters" in metadata, false);
assert.equal(updateDefinitionRequest(offline).parameters.source.tables[0].table, "orders");
assert.deepEqual(
  definitionPageRequest({
    pageNo: 1,
    pageSize: 20,
    solutionType: "DATABASE_REALTIME",
  }).parameterFilters,
  { solutionType: "DATABASE_REALTIME" },
);

const incremental = {
  mode: "TIMESTAMP",
  column: "updated_at",
  initialValue: "2026-10-01T00:00:00",
  delaySeconds: 0,
  lookbackSeconds: 5,
};
const offlineIncremental = createDefinitionRequest({
  ...offline,
  steps: ["INCREMENTAL"],
  writeMode: "UPSERT",
  incremental,
}).parameters;
assert.deepEqual(offlineIncremental.source.tables[0].incremental, incremental);
assert.equal(offlineIncremental.sink.tables[0].write.mode, "UPSERT");
assert.throws(() =>
  readSyncParameters(4, {
    ...offlineIncremental,
    source: {
      ...offlineIncremental.source,
      tables: [{ ...offlineIncremental.source.tables[0], incremental: undefined }],
    },
  }),
);
assert.throws(() =>
  readSyncParameters(4, {
    ...offlineIncremental,
    sink: {
      ...offlineIncremental.sink,
      tables: [{ ...offlineIncremental.sink.tables[0], write: { mode: "APPEND" } }],
    },
  }),
);

const batch = createDefinitionRequest({
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
      writeMode: "APPEND",
    },
  ],
}).parameters;
assert.equal(batch.source.tables.length, 2);
assert.equal(batch.sink.tables.length, 2);
assert.equal(batch.source.tables[1].table, "users");
assert.equal(batch.sink.tables[1].input, batch.source.tables[1].id);
assert.equal(toSyncTaskRecord({ ...record, parameters: batch }).tableMappings.length, 2);
assert.throws(() =>
  readSyncParameters(4, {
    ...batch,
    solutionType: "SINGLE_TABLE_OFFLINE",
  }),
);
assert.throws(() =>
  readSyncParameters(4, {
    ...batch,
    sink: {
      ...batch.sink,
      tables: [batch.sink.tables[0], { ...batch.sink.tables[1], input: "table-1" }],
    },
  }),
);
assert.throws(() =>
  readSyncParameters(4, {
    ...batch,
    sink: {
      ...batch.sink,
      tables: [
        batch.sink.tables[0],
        {
          ...batch.sink.tables[1],
          table: batch.sink.tables[0].table,
        },
      ],
    },
  }),
);

const page = projectSyncTaskPage({
  bizData: [
    record,
    { ...record, id: "old", parameterSchemaVersion: 3 },
    { ...record, id: "corrupt", parameters: null },
  ],
  pagination: { pageNo: 1, pageSize: 20, total: 3 },
});
assert.equal(page.bizData.length, 3);
assert.equal(page.bizData[0].id, "d-1");
assert.ok(page.bizData[1].configurationError);
assert.ok(page.bizData[2].configurationError);

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

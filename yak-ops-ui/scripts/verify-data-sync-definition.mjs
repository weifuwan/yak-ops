import assert from "node:assert/strict";
import {
  buildSyncParameters,
  createDefinitionRequest,
  toSyncTaskRecord,
} from "../apps/web/service/data-sync/definition.ts";
import {
  readSyncParameters,
  pipelineSolutionType,
  syncTableMappings,
  displaySyncSteps,
} from "../apps/web/service/data-sync/parameters.ts";

const base = {
  name: "orders",
  debug: false,
  solutionType: "SINGLE_TABLE_REALTIME",
  steps: ["FULL", "INCREMENTAL"],
  writeMode: "UPSERT",
  sourceDataSourceId: "source",
  sourceTable: "orders",
  targetDataSourceId: "target",
  targetSchema: "public",
  targetTable: "ods_orders",
};
const definition = createDefinitionRequest(base);
const v5 = definition.parameters;
assert.deepEqual(Object.keys(v5), ["source", "sink", "route", "transform", "pipeline"]);
assert.equal(v5.source.type, "MYSQL_CDC");
assert.equal(v5.source.startup.mode, "INITIAL");
assert.equal(v5.sink.type, "JDBC");
assert.equal(v5.route[0]["source-table"], "orders");
assert.equal(v5.route[0]["sink-table"], "public.ods_orders");
assert.equal(v5.sink.tables[0].write.mode, "UPSERT");
assert.equal(v5.pipeline["execution.runtime-mode"], "STREAMING");
assert.equal(v5.pipeline["pipeline.table-scope"], "SINGLE");
assert.equal(pipelineSolutionType(v5), "SINGLE_TABLE_REALTIME");
assert.deepEqual(displaySyncSteps(v5), ["FULL", "INCREMENTAL"]);
assert.equal(syncTableMappings(v5)[0].source.table, "orders");
assert.equal(readSyncParameters(5, v5), v5);
assert.throws(() => readSyncParameters(4, v5));
assert.throws(() => readSyncParameters(5, { ...v5, solutionType: "SINGLE_TABLE_REALTIME" }));
assert.throws(() => readSyncParameters(5, { ...v5, route: [] }));
const record = {
  id: "d-1",
  name: "orders",
  taskType: "DATA_SYNC",
  status: "UNPUBLISHED",
  debug: false,
  definitionVersion: 1,
  parameterSchemaVersion: 5,
  parameters: v5,
};
assert.equal(toSyncTaskRecord(record).solutionType, "SINGLE_TABLE_REALTIME");

// V5 edit/save round trips must not turn pinned Binlog offsets into LATEST_OFFSET.
// For modes without an editable form, the existing startup object is read-only.
for (const startup of [
  { mode: "INITIAL" },
  { mode: "SNAPSHOT" },
  { mode: "EARLIEST_OFFSET" },
  { mode: "LATEST_OFFSET" },
  { mode: "SPECIFIC_OFFSETS", file: "mysql-bin.000123", pos: 45678, skipEvents: 2, skipRows: 3 },
  { mode: "SPECIFIC_OFFSETS", gtidSet: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa:1-9" },
  { mode: "TIMESTAMP", timestampMillis: 1700000000123 },
]) {
  const original = {
    ...v5,
    source: { ...v5.source, startup },
    pipeline: {
      ...v5.pipeline,
      "execution.runtime-mode": startup.mode === "SNAPSHOT" ? "BATCH" : "STREAMING",
    },
    sink: {
      ...v5.sink,
      schemaPolicy: {
        ...v5.sink.schemaPolicy,
        evolution: { enabled: startup.mode !== "SNAPSHOT", allowDropColumns: false },
      },
    },
  };
  const loaded = toSyncTaskRecord({ ...record, parameters: original });
  assert.deepEqual(loaded.startup, startup);
  const saved = buildSyncParameters({
    ...base,
    solutionType: loaded.solutionType,
    steps: loaded.steps,
    startup: loaded.startup,
  });
  assert.deepEqual(saved.source.startup, startup);
  assert.deepEqual(readSyncParameters(5, saved).source.startup, startup);
}
const batch = buildSyncParameters({
  ...base,
  solutionType: "DATABASE_OFFLINE",
  steps: ["FULL"],
  writeMode: "APPEND",
  tableMappings: [
    { source: { table: "orders" }, target: { table: "orders_copy" }, writeMode: "APPEND" },
    { source: { table: "users" }, target: { table: "users_copy" }, writeMode: "APPEND" },
  ],
});
assert.equal(batch.source.type, "JDBC");
assert.equal(batch.source.syncMode, "FULL");
assert.equal(batch.route.length, 2);
assert.equal(pipelineSolutionType(batch), "DATABASE_OFFLINE");
const singleRouteDatabase = buildSyncParameters({
  ...base,
  solutionType: "DATABASE_REALTIME",
  tableMappings: [
    {
      source: { table: "orders" },
      target: { table: "orders_copy" },
      writeMode: "UPSERT",
    },
  ],
});
assert.equal(singleRouteDatabase.route.length, 1);
assert.equal(singleRouteDatabase.pipeline["pipeline.table-scope"], "DATABASE");
assert.equal(pipelineSolutionType(singleRouteDatabase), "DATABASE_REALTIME");
assert.throws(() =>
  readSyncParameters(5, {
    ...singleRouteDatabase,
    sink: {
      ...singleRouteDatabase.sink,
      tables: [
        {
          ...singleRouteDatabase.sink.tables[0],
          write: { mode: "RETRACT", transaction: { mode: "CHECKPOINT_XA" } },
        },
      ],
    },
  }),
);
assert.equal(
  toSyncTaskRecord({ ...record, parameters: singleRouteDatabase }).solutionType,
  "DATABASE_REALTIME",
);
assert.throws(() =>
  readSyncParameters(5, {
    ...singleRouteDatabase,
    pipeline: { ...singleRouteDatabase.pipeline, "pipeline.table-scope": "SINGLE" },
    route: [singleRouteDatabase.route[0], singleRouteDatabase.route[0]],
  }),
);
assert.equal(syncTableMappings(batch).length, 2);
assert.throws(() => readSyncParameters(5, { ...batch, route: [batch.route[0], batch.route[0]] }));
console.log("V5 pipeline definition and UI projection contracts passed");

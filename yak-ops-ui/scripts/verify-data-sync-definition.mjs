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
const definitionJson = createDefinitionRequest(base).parameters;
assert.deepEqual(Object.keys(definitionJson), ["source", "sink", "route", "transform", "pipeline"]);
assert.equal(definitionJson.source.type, "MYSQL_CDC");
assert.equal(definitionJson.source.startup.mode, "INITIAL");
assert.equal(definitionJson.route[0]["source-table"], "orders");
assert.equal(definitionJson.route[0]["sink-table"], "public.ods_orders");
assert.equal("pipelineYaml" in definitionJson, false);

const v6 = readSyncParameters(6, definitionJson);
assert.equal(v6.source.type, "MYSQL_CDC");
assert.equal(v6.source.startup.mode, "INITIAL");
assert.equal(v6.sink.type, "JDBC");
assert.equal(v6.route[0]["source-table"], "orders");
assert.equal(v6.sink.tables[0].write.mode, "UPSERT");
assert.equal(v6.pipeline["execution.runtime-mode"], "STREAMING");
assert.equal(pipelineSolutionType(v6), "SINGLE_TABLE_REALTIME");
assert.deepEqual(displaySyncSteps(v6), ["FULL", "INCREMENTAL"]);
assert.equal(syncTableMappings(v6)[0].source.table, "orders");
assert.throws(() => readSyncParameters(5, definitionJson));
assert.throws(() => readSyncParameters(6, { source: [] }));
assert.throws(() => readSyncParameters(6, { ...definitionJson, unknown: 1 }));

const record = {
  id: "d-1",
  name: "orders",
  taskType: "DATA_SYNC",
  status: "UNPUBLISHED",
  debug: false,
  definitionVersion: 1,
  parameterSchemaVersion: 6,
  parameters: definitionJson,
};
assert.equal(toSyncTaskRecord(record).solutionType, "SINGLE_TABLE_REALTIME");

for (const startup of [
  { mode: "INITIAL" },
  { mode: "SNAPSHOT" },
  { mode: "EARLIEST_OFFSET" },
  { mode: "LATEST_OFFSET" },
  { mode: "SPECIFIC_OFFSETS", file: "mysql-bin.000123", pos: 45678, skipEvents: 2, skipRows: 3 },
  { mode: "SPECIFIC_OFFSETS", gtidSet: "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa:1-9" },
  { mode: "TIMESTAMP", timestampMillis: 1700000000123 },
]) {
  const steps =
    startup.mode === "SNAPSHOT"
      ? ["FULL"]
      : startup.mode === "INITIAL"
        ? ["FULL", "INCREMENTAL"]
        : ["INCREMENTAL"];
  const created = createDefinitionRequest({ ...base, steps, startup }).parameters;
  const loaded = toSyncTaskRecord({ ...record, parameters: created });
  assert.deepEqual(loaded.startup, startup);
  const saved = buildSyncParameters({
    ...base,
    solutionType: loaded.solutionType,
    steps: loaded.steps,
    startup: loaded.startup,
  });
  assert.deepEqual(saved.source.startup, startup);
  assert.deepEqual(readSyncParameters(6, saved).source.startup, startup);
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
assert.equal(pipelineSolutionType(readSyncParameters(6, batch)), "DATABASE_OFFLINE");

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
assert.equal(singleRouteDatabase.pipeline["pipeline.table-scope"], "DATABASE");
assert.equal(pipelineSolutionType(readSyncParameters(6, singleRouteDatabase)), "DATABASE_REALTIME");
assert.equal(syncTableMappings(readSyncParameters(6, batch)).length, 2);
const withServerIndex = { ...definitionJson, index: { sourceType: "MYSQL_CDC" } };
assert.deepEqual(readSyncParameters(6, withServerIndex), definitionJson);
console.log("V6 JSON API, startup round-trip, and UI projection contracts passed");

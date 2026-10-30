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
assert.equal(syncTableMappings(batch).length, 2);
assert.throws(() => readSyncParameters(5, { ...batch, route: [batch.route[0], batch.route[0]] }));
console.log("V5 pipeline definition and UI projection contracts passed");

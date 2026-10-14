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
assert.deepEqual(toSyncTaskRecord(oldDefinition), result);
assert.equal(oldDefinition.parameters, oldParameters);
assert.throws(() => readSyncParameters(1, oldParameters));
assert.throws(() => readSyncParameters(3, oldParameters));
assert.throws(() => readSyncParameters(2, created.parameters));
for (const patch of [
  { solutionType: "DATABASE_REALTIME" },
  { steps: ["FULL"] },
  { steps: ["FULL", "FULL"] },
  { steps: ["SCHEMA", "FULL", "INCREMENTAL"] },
  { tableSelection: { mode: "RULES" } },
  { tableMappings: [] },
  { tableMappings: [...json.tableMappings, ...json.tableMappings] },
  { source: { ...json.source, type: "JDBC" } },
  { tableMappings: [{ ...json.tableMappings[0], writeMode: "APPEND" }] },
])
  assert.throws(() => readSyncParameters(3, JSON.stringify({ ...json, ...patch })));
assert.throws(() => toSyncTaskRecord({ ...definition, taskType: "UNKNOWN" }));
console.log("Data Sync v3 and pinned-v2 definition contracts passed");

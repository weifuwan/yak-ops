import assert from "node:assert/strict";

import {
  buildSyncParameters,
  createDefinitionRequest,
  definitionPageRequest,
  toSyncTaskRecord,
  updateDefinitionRequest,
} from "../apps/web/service/data-sync/definition.ts";

const common = {
  name: "orders",
  debug: false,
  sourceDataSourceId: "mysql-source",
  sourceTable: "orders",
  targetDataSourceId: "pg-target",
  targetSchema: "public",
  targetTable: "ods_orders",
};
const offline = { ...common, syncType: "OFFLINE", writeMode: "APPEND" };
const realtime = { ...common, debug: true, syncType: "REALTIME" };

assert.deepEqual(buildSyncParameters(offline), {
  source: { type: "JDBC", datasourceId: "mysql-source", table: "orders" },
  transform: [],
  sink: {
    type: "JDBC",
    datasourceId: "pg-target",
    schema: "public",
    table: "ods_orders",
    writeMode: "APPEND",
  },
});
assert.equal(buildSyncParameters(realtime).source.type, "MYSQL_CDC");
assert.equal(buildSyncParameters(realtime).sink.writeMode, "UPSERT");

const created = createDefinitionRequest(realtime);
assert.equal(created.taskType, "DATA_SYNC");
assert.equal(created.debug, true);
assert.equal(JSON.parse(created.parameters).debug, undefined);
assert.equal("parametersFormat" in created, false);
assert.equal(typeof created.parameters, "string");
assert.equal(JSON.parse(created.parameters).sink.writeMode, "UPSERT");

const metadataOnly = updateDefinitionRequest({
  name: "renamed",
  remark: "new remark",
  debug: true,
});
assert.equal(metadataOnly.debug, true);
assert.equal(metadataOnly.parameters, undefined);
assert.equal(JSON.parse(updateDefinitionRequest(offline).parameters).source.table, "orders");

const realtimeFilters = definitionPageRequest({
  pageNo: 1,
  pageSize: 20,
  syncType: "REALTIME",
}).parameterFilters;
assert.deepEqual(realtimeFilters, { sourceType: "MYSQL_CDC" });
assert.deepEqual(
  definitionPageRequest({
    pageNo: 1,
    pageSize: 20,
    syncType: "OFFLINE",
    sourceDataSourceId: "mysql-source",
    targetDataSourceId: "pg-target",
  }).parameterFilters,
  {
    sourceType: "JDBC",
    sourceDataSourceId: "mysql-source",
    targetDataSourceId: "pg-target",
  },
);

const result = toSyncTaskRecord({
  id: "definition-1",
  name: "orders",
  taskType: "DATA_SYNC",
  status: "UNPUBLISHED",
  definitionVersion: 2,
  parameterSchemaVersion: 2,
  parameters: created.parameters,
});
assert.equal(result.syncType, "REALTIME");
assert.equal(result.debug, false);
assert.equal(result.targetSchema, "public");
assert.equal(result.definitionVersion, 2);

const oldDefinition = {
  id: "old",
  name: "old",
  taskType: "DATA_SYNC",
  status: "UNPUBLISHED",
  definitionVersion: 1,
  parameterSchemaVersion: 1,
  parameters: "{}",
};
assert.throws(() => toSyncTaskRecord(oldDefinition));
console.log("Data Sync Definition contract checks passed");

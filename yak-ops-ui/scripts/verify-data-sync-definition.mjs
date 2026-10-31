import assert from "node:assert/strict";
import { connectorChoices } from "../apps/web/service/data-sync/connectors.ts";
import {
  buildSyncParameters,
  createDefinitionRequest,
  toSyncTaskRecord,
  definitionPageRequest,
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
  sourceConnector: "mysql",
  sinkConnector: "jdbc-postgres",
  sourceProducesChangelog: true,
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
assert.equal(definitionJson.source.type, "mysql");
assert.equal(definitionJson.source.startup.mode, "INITIAL");
assert.equal(definitionJson.route[0]["source-table"], "orders");
assert.equal(definitionJson.route[0]["sink-table"], "public.ods_orders");
assert.equal("pipelineYaml" in definitionJson, false);

const pipeline = readSyncParameters(7, definitionJson);
assert.equal(pipeline.source.type, "mysql");
assert.equal(pipeline.source.startup.mode, "INITIAL");
assert.equal(pipeline.sink.type, "jdbc-postgres");
assert.equal(pipeline.route[0]["source-table"], "orders");
assert.equal(pipeline.sink.tables[0].write.mode, "UPSERT");
assert.equal(pipeline.pipeline["execution.runtime-mode"], "STREAMING");
assert.equal(pipelineSolutionType(pipeline), "SINGLE_TABLE_REALTIME");
assert.deepEqual(displaySyncSteps(pipeline), ["FULL", "INCREMENTAL"]);
assert.equal(syncTableMappings(pipeline)[0].source.table, "orders");
assert.throws(() => readSyncParameters(5, definitionJson));
assert.throws(() => readSyncParameters(6, definitionJson));
assert.throws(() => readSyncParameters(7, { source: [] }));
assert.throws(() => readSyncParameters(7, { ...definitionJson, unknown: 1 }));
const withoutTransforms = { ...definitionJson };
delete withoutTransforms.transform;
assert.deepEqual(readSyncParameters(7, withoutTransforms).transform, []);
assert.equal("transform" in withoutTransforms, false);

const record = {
  id: "d-1",
  name: "orders",
  taskType: "DATA_SYNC",
  status: "UNPUBLISHED",
  debug: false,
  definitionVersion: 1,
  parameterSchemaVersion: 7,
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
  assert.deepEqual(readSyncParameters(7, saved).source.startup, startup);
}
const batch = buildSyncParameters({
  ...base,
  solutionType: "DATABASE_OFFLINE",
  sourceConnector: "jdbc-mysql",
  sourceProducesChangelog: false,
  steps: ["FULL"],
  writeMode: "APPEND",
  tableMappings: [
    { source: { table: "orders" }, target: { table: "orders_copy" }, writeMode: "APPEND" },
    { source: { table: "users" }, target: { table: "users_copy" }, writeMode: "APPEND" },
  ],
});
assert.equal(batch.source.type, "jdbc-mysql");
assert.equal(batch.source.syncMode, "FULL");
assert.equal(batch.route.length, 2);
assert.equal(pipelineSolutionType(readSyncParameters(7, batch)), "DATABASE_OFFLINE");

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
assert.equal(pipelineSolutionType(readSyncParameters(7, singleRouteDatabase)), "DATABASE_REALTIME");
assert.equal(syncTableMappings(readSyncParameters(7, batch)).length, 2);
assert.throws(() =>
  readSyncParameters(7, {
    ...batch,
    source: {
      ...batch.source,
      tables: [{ ...batch.source.tables[0], database: "app" }, batch.source.tables[1]],
    },
    route: [{ ...batch.route[0], "source-table": "app.orders" }, batch.route[1]],
  }),
);
const withServerIndex = { ...definitionJson, index: { sourceType: "mysql" } };
assert.deepEqual(readSyncParameters(7, withServerIndex), definitionJson);
console.log("Pipeline JSON API, startup round-trip, and UI projection contracts passed");

const transformed = createDefinitionRequest({
  ...base,
  transforms: [
    { "source-table": "orders", projection: "id, amount * 2 AS amount", filter: "amount > 10" },
  ],
}).parameters;
const transformedTask = toSyncTaskRecord({ ...record, parameters: transformed });
assert.deepEqual(
  buildSyncParameters({ ...base, transforms: transformedTask.transforms }).transform,
  transformed.transform,
);
const snapshot = createDefinitionRequest({
  ...base,
  steps: ["FULL"],
  startup: { mode: "SNAPSHOT" },
}).parameters;
const snapshotTask = toSyncTaskRecord({ ...record, parameters: snapshot });
assert.equal(snapshotTask.solutionType, "SINGLE_TABLE_OFFLINE");
assert.equal(snapshotTask.sourceConnector, "mysql");
assert.equal(snapshotTask.sourceProducesChangelog, true);
const appendSnapshot = createDefinitionRequest({
  ...base,
  steps: ["FULL"],
  startup: { mode: "SNAPSHOT" },
  writeMode: "APPEND",
}).parameters;
assert.equal(readSyncParameters(7, appendSnapshot).sink.tables[0].write.mode, "APPEND");
assert.deepEqual(
  definitionPageRequest({ pageNo: 1, pageSize: 10, solutionType: "DATABASE_REALTIME" })
    .parameterFilters,
  { runtimeMode: "STREAMING", tableScope: "DATABASE" },
);
const choices = connectorChoices([
  {
    sourceConnector: "custom-source",
    sinkConnector: "custom-sink",
    producesChangelog: true,
    supportsIncrementalBatch: false,
    supportsSchemaChanges: true,
    supportsRetract: false,
    boundedness: "CONTINUOUS_UNBOUNDED",
  },
]);
assert.equal(choices.length, 4);
assert.ok(
  choices.every(
    (choice) =>
      choice.sourceConnector === "custom-source" && choice.sinkConnector === "custom-sink",
  ),
);
assert.ok(
  choices
    .find((choice) => choice.solutionType === "SINGLE_TABLE_OFFLINE")
    .allowedCombinations.every((choice) => choice.supportsScheduling),
);
assert.deepEqual(
  choices.find((choice) => choice.solutionType === "SINGLE_TABLE_OFFLINE").allowedCombinations[0]
    .writeModes,
  ["APPEND", "UPSERT"],
);
console.log("Connector discovery, transform preservation, and bounded snapshot contracts passed");

const pipelineOptions = {
  name: "orders-pipeline",
  parallelism: 2,
  "local-time-zone": "Asia/Shanghai",
  "schema.change.behavior": "evolve",
  "schema.operator.uid": "orders-schema",
  "route-mode": "FIRST_MATCH",
  "transform.decimal.precision.mode": "UP_TO_38",
};
const configured = createDefinitionRequest({ ...base, pipelineOptions }).parameters;
const configuredTask = toSyncTaskRecord({ ...record, parameters: configured });
assert.deepEqual(
  buildSyncParameters({ ...base, pipelineOptions: configuredTask.pipelineOptions }).pipeline,
  configured.pipeline,
);
assert.deepEqual(configured.pipeline, {
  ...pipelineOptions,
  "execution.runtime-mode": "STREAMING",
  "pipeline.table-scope": "SINGLE",
});
for (const invalidOptions of [
  { "schema-operator.rpc-timeout": "1 min" },
  { parallelism: 0 },
  { parallelism: {} },
  { "route-mode": false },
]) {
  assert.throws(() =>
    readSyncParameters(7, {
      ...definitionJson,
      pipeline: { ...definitionJson.pipeline, ...invalidOptions },
    }),
  );
}
console.log("Supported Pipeline option round-trip and unsupported option rejection passed");

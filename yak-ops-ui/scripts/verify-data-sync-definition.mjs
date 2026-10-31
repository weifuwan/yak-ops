import assert from "node:assert/strict";
import { connectorChoices } from "../apps/web/service/data-sync/connectors.ts";
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
  pipelineProductType,
  pipelineSyncType,
  pipelineTableScope,
  readSyncParameters,
  syncTableMappings,
} from "../apps/web/service/data-sync/parameters.ts";
import { fieldProjection } from "../apps/web/service/data-sync/transforms.ts";

const mapping = {
  source: { database: "app", table: "orders" },
  target: { database: "ods", table: "orders_copy" },
  writeMode: "UPSERT",
};
const replication = {
  name: "orders",
  debug: false,
  productType: "DATABASE_REPLICATION",
  sourceConnector: "mysql",
  sinkConnector: "jdbc-mysql",
  sourceProducesChangelog: true,
  steps: ["FULL", "INCREMENTAL"],
  writeMode: "UPSERT",
  sourceDataSourceId: "source",
  sourceDatabase: "app",
  sourceTable: "orders",
  targetDataSourceId: "target",
  targetDatabase: "ods",
  targetTable: "orders_copy",
  tableMappings: [mapping],
};
const offline = {
  ...replication,
  productType: "SINGLE_TABLE_BATCH",
  sourceConnector: "jdbc-mysql",
  sourceProducesChangelog: false,
  steps: ["FULL"],
  writeMode: "APPEND",
  tableMappings: undefined,
};
const definition = (parameters, changes = {}) => ({
  id: "d-1",
  name: "orders",
  taskType: "DATA_SYNC",
  status: "UNPUBLISHED",
  debug: false,
  definitionVersion: 1,
  parameterSchemaVersion: 7,
  parameters,
  ...changes,
});
const parameters = createDefinitionRequest(replication).parameters;
assert.deepEqual(Object.keys(parameters), ["source", "sink", "route", "transform", "pipeline"]);
assert.equal(parameters.source.type, "mysql");
assert.equal(parameters.source.startup.mode, "INITIAL");
assert.equal(parameters.route[0]["source-table"], "app.orders");
assert.equal(parameters.route[0]["sink-table"], "ods.orders_copy");
assert.equal("productType" in parameters, false);
assert.equal("pipelineYaml" in parameters, false);
assert.equal(parameters.pipeline["pipeline.table-scope"], "DATABASE");
assert.equal(parameters.pipeline["execution.runtime-mode"], "STREAMING");
assert.equal(pipelineProductType(readSyncParameters(7, parameters)), "DATABASE_REPLICATION");
assert.deepEqual(displaySyncSteps(parameters), ["FULL", "INCREMENTAL"]);
assert.deepEqual(syncTableMappings(parameters), [
  {
    ...mapping,
    source: { ...mapping.source, schema: undefined },
    target: { ...mapping.target, schema: undefined },
  },
]);
assert.deepEqual(updateDefinitionRequest({ name: "renamed", remark: "new remark", debug: true }), {
  name: "renamed",
  remark: "new remark",
  debug: true,
});
assert.equal("parameters" in updateDefinitionRequest({ name: "renamed" }), false);
assert.deepEqual(updateDefinitionRequest(replication).parameters, parameters);

for (const [steps, mode, runtime] of [
  [["FULL", "INCREMENTAL"], "INITIAL", "STREAMING"],
  [["INCREMENTAL"], "LATEST_OFFSET", "STREAMING"],
  [["FULL"], "SNAPSHOT", "BATCH"],
]) {
  const created = buildSyncParameters({ ...replication, steps });
  const loaded = toSyncTaskRecord(definition(created));
  assert.equal(created.source.startup.mode, mode);
  assert.equal(created.pipeline["execution.runtime-mode"], runtime);
  assert.equal(created.pipeline["pipeline.table-scope"], "DATABASE");
  assert.equal(loaded.productType, "DATABASE_REPLICATION");
  assert.equal(loaded.syncType, runtime === "BATCH" ? "OFFLINE" : "REALTIME");
  assert.deepEqual(loaded.steps, steps);
  assert.deepEqual(buildSyncParameters(loaded), created);
}
const snapshot = buildSyncParameters({ ...replication, steps: ["FULL"] });
for (const scope of ["SINGLE", "DATABASE", undefined]) {
  const scoped = { ...snapshot, pipeline: { ...snapshot.pipeline, "pipeline.table-scope": scope } };
  assert.equal(pipelineProductType(readSyncParameters(7, scoped)), "DATABASE_REPLICATION");
  assert.equal(pipelineSyncType(scoped), "OFFLINE");
  assert.equal(pipelineTableScope(scoped), scope ?? "SINGLE");
}
const appendSnapshot = buildSyncParameters({
  ...replication,
  steps: ["FULL"],
  tableMappings: [{ ...mapping, writeMode: "APPEND" }],
});
assert.equal(readSyncParameters(7, appendSnapshot).sink.tables[0].write.mode, "APPEND");
const multiple = buildSyncParameters({
  ...replication,
  tableMappings: [
    mapping,
    {
      source: { database: "app", table: "users" },
      target: { database: "ods", table: "users_copy" },
      writeMode: "UPSERT",
    },
  ],
});
assert.equal(toSyncTaskRecord(definition(multiple)).tableMappings.length, 2);
assert.equal(pipelineProductType(readSyncParameters(7, multiple)), "DATABASE_REPLICATION");
assert.throws(() => buildSyncParameters({ ...replication, tableMappings: [] }), /请选择同步表/);
assert.throws(() => buildSyncParameters({ ...offline, sourceProducesChangelog: true }), /来源能力/);
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
  const created = buildSyncParameters({ ...replication, steps, startup });
  const loaded = toSyncTaskRecord(definition(created));
  const saved = buildSyncParameters(loaded);
  assert.equal(loaded.productType, "DATABASE_REPLICATION");
  assert.deepEqual(loaded.startup, startup);
  assert.deepEqual(saved.source.startup, startup);
  assert.deepEqual(readSyncParameters(7, saved).source.startup, startup);
  assert.notEqual(saved.source.startup, loaded.startup);
}
const defaultStartup = structuredClone(parameters);
delete defaultStartup.source.startup;
const defaultTask = toSyncTaskRecord(definition(defaultStartup));
assert.equal(defaultTask.productType, "DATABASE_REPLICATION");
assert.deepEqual(defaultTask.steps, ["FULL", "INCREMENTAL"]);
assert.equal(buildSyncParameters(defaultTask).source.startup.mode, "INITIAL");
assert.throws(() =>
  readSyncParameters(7, {
    ...defaultStartup,
    source: { ...defaultStartup.source, syncMode: "FULL" },
  }),
);
assert.throws(() =>
  readSyncParameters(7, {
    ...parameters,
    pipeline: { ...parameters.pipeline, "execution.runtime-mode": "BATCH" },
  }),
);
const retract = buildSyncParameters({
  ...replication,
  schemaChangesEnabled: false,
  tableMappings: [
    { ...mapping, source: { ...mapping.source, chunkKeyColumn: "bucket" }, writeMode: "RETRACT" },
  ],
});
assert.equal(retract.pipeline["pipeline.table-scope"], "SINGLE");
assert.deepEqual(retract.sink.tables[0].write, {
  mode: "RETRACT",
  transaction: { mode: "CHECKPOINT_XA" },
});
assert.deepEqual(buildSyncParameters(toSyncTaskRecord(definition(retract))), retract);
assert.throws(
  () =>
    buildSyncParameters({
      ...replication,
      tableMappings: [{ ...mapping, writeMode: "RETRACT" }, mapping],
    }),
  /一张表/,
);
for (const steps of [["FULL"], ["INCREMENTAL"], ["FULL", "INCREMENTAL"]]) {
  const incremental = steps.includes("INCREMENTAL")
    ? {
        mode: "INCREASING",
        column: "id",
        initialValue: "0",
        delaySeconds: 0,
        lookbackSeconds: 0,
      }
    : undefined;
  const created = buildSyncParameters({
    ...offline,
    steps,
    incremental,
    writeMode: steps.includes("INCREMENTAL") ? "UPSERT" : "APPEND",
  });
  const loaded = toSyncTaskRecord(definition(created));
  assert.equal(created.source.startup, undefined);
  assert.equal(created.pipeline["pipeline.table-scope"], "SINGLE");
  assert.equal(created.pipeline["execution.runtime-mode"], "BATCH");
  assert.equal(loaded.productType, "SINGLE_TABLE_BATCH");
  assert.equal(loaded.syncType, "OFFLINE");
  assert.deepEqual(loaded.steps, steps);
  assert.deepEqual(buildSyncParameters(loaded), created);
}
const transforms = [
  {
    "source-table": "app.orders",
    projection: fieldProjection([
      { source: "id", target: "order_id" },
      { source: "name", target: "label" },
    ]),
    filter: "amount > 10",
    "primary-keys": "order_id",
    description: "Mapped physical key",
  },
  {
    "source-table": "app\\..*",
    projection: "id, CONCAT(name, ', ', CAST(amount AS STRING)) AS label",
    filter: "label <> 'x,y'",
  },
  { "source-table": "app.other", projection: "*", "table-options.delimiter": ";" },
];
const configured = buildSyncParameters({
  ...offline,
  transforms,
  pipelineOptions: {
    name: "orders-pipeline",
    parallelism: 2,
    "local-time-zone": "Asia/Shanghai",
    "schema.change.behavior": "exception",
    "schema.operator.uid": "orders-schema",
    "route-mode": "FIRST_MATCH",
    "transform.decimal.precision.mode": "UP_TO_38",
  },
});
const configuredTask = toSyncTaskRecord(definition(configured));
const reserialized = buildSyncParameters(configuredTask);
assert.deepEqual(reserialized.transform, transforms);
assert.deepEqual(reserialized.pipeline, configured.pipeline);
assert.notEqual(reserialized.transform[0], configuredTask.transforms[0]);
assert.equal(configuredTask.transforms[1].projection, transforms[1].projection);
const withoutTransforms = structuredClone(parameters);
delete withoutTransforms.transform;
assert.deepEqual(readSyncParameters(7, withoutTransforms).transform, []);
assert.equal("transform" in withoutTransforms, false);
assert.deepEqual(
  readSyncParameters(7, {
    ...parameters,
    index: { sourceType: "untrusted", productType: "SINGLE_TABLE_BATCH" },
  }),
  parameters,
);
const singleJdbc = buildSyncParameters(offline);
const unsupportedJdbc = structuredClone(singleJdbc);
unsupportedJdbc.pipeline["pipeline.table-scope"] = "DATABASE";
assert.throws(() => toSyncTaskRecord(definition(unsupportedJdbc)), /多表 JDBC/);
unsupportedJdbc.source.tables.push({ database: "app", table: "users" });
unsupportedJdbc.sink.tables.push({
  database: "ods",
  table: "users_copy",
  write: { mode: "APPEND" },
});
unsupportedJdbc.route.push({ "source-table": "app.users", "sink-table": "ods.users_copy" });
assert.throws(() => toSyncTaskRecord(definition(unsupportedJdbc)), /多表 JDBC/);
const page = projectSyncTaskPage({
  bizData: [
    definition(snapshot, { id: "snapshot" }),
    definition(singleJdbc, { id: "single" }),
    definition(unsupportedJdbc, { id: "unsupported" }),
    definition(parameters, { id: "old-version", parameterSchemaVersion: 6 }),
    definition({ broken: true }, { id: "invalid" }),
  ],
  pagination: { pageNo: 2, pageSize: 5, total: 17 },
});
assert.deepEqual(page.pagination, { pageNo: 2, pageSize: 5, total: 17 });
assert.deepEqual(
  page.bizData.map((task) => task.id),
  ["snapshot", "single", "unsupported", "old-version", "invalid"],
);
assert.equal(page.bizData[0].productType, "DATABASE_REPLICATION");
assert.equal(page.bizData[1].productType, "SINGLE_TABLE_BATCH");
for (const item of page.bizData.slice(2)) {
  assert.ok(item.configurationError);
  assert.equal("productType" in item, false);
  assert.equal("sourceConnector" in item, false);
  assert.equal(item.name, "orders");
  assert.equal(item.definitionVersion, 1);
}
assert.deepEqual(
  definitionPageRequest({
    pageNo: 3,
    pageSize: 10,
    productType: "DATABASE_REPLICATION",
    syncType: "OFFLINE",
    sourceDataSourceId: "source",
    targetDataSourceId: "target",
  }).parameterFilters,
  {
    dataSyncProfile: "DATABASE_REPLICATION",
    runtimeMode: "BATCH",
    sourceDataSourceId: "source",
    targetDataSourceId: "target",
  },
);
const choices = connectorChoices([
  {
    sourceConnector: "custom-stream",
    sinkConnector: "custom-sink",
    producesChangelog: true,
    supportsIncrementalBatch: false,
    supportsSchemaChanges: true,
    supportsRetract: true,
    boundedness: "CONTINUOUS_UNBOUNDED",
  },
  {
    sourceConnector: "custom-batch",
    sinkConnector: "custom-sink",
    producesChangelog: false,
    supportsIncrementalBatch: true,
    supportsSchemaChanges: false,
    supportsRetract: false,
    boundedness: "BOUNDED",
  },
]);
assert.deepEqual(
  choices.map((choice) => choice.productType),
  ["DATABASE_REPLICATION", "SINGLE_TABLE_BATCH"],
);
assert.equal(choices[0].sourceConnector, "custom-stream");
assert.deepEqual(choices[0].defaultSteps, ["FULL", "INCREMENTAL"]);
assert.deepEqual(choices[1].defaultSteps, ["FULL"]);
const copyStrategies = choices[0].allowedCombinations.filter(
  (choice) => !choice.steps.includes("SCHEMA"),
);
assert.deepEqual(
  copyStrategies.map((choice) => choice.steps),
  [["FULL", "INCREMENTAL"], ["INCREMENTAL"], ["FULL"]],
);
assert.deepEqual(
  copyStrategies.map((choice) => choice.supportsScheduling),
  [false, false, true],
);
assert.deepEqual(copyStrategies[0].writeModes, ["UPSERT", "RETRACT"]);
assert.deepEqual(copyStrategies[2].writeModes, ["APPEND", "UPSERT"]);
assert.ok(choices[1].allowedCombinations.every((choice) => choice.supportsScheduling));
assert.ok(choices[1].allowedCombinations.every((choice) => !choice.steps.includes("SCHEMA")));
assert.deepEqual(connectorChoices([]), []);
assert.deepEqual(
  connectorChoices([
    {
      sourceConnector: "not-bounded",
      sinkConnector: "target",
      producesChangelog: false,
      supportsIncrementalBatch: false,
      supportsSchemaChanges: false,
      supportsRetract: false,
      boundedness: "CONTINUOUS_UNBOUNDED",
    },
  ]),
  [],
);
for (const invalidOptions of [
  { "schema-operator.rpc-timeout": "1 min" },
  { parallelism: 0 },
  { parallelism: {} },
  { "route-mode": false },
  { "schema.change.behavior": "ignore" },
  { "schema.change.behavior": "exception" },
])
  assert.throws(() =>
    readSyncParameters(7, {
      ...parameters,
      pipeline: { ...parameters.pipeline, ...invalidOptions },
    }),
  );
for (const version of [5, 6, 8]) assert.throws(() => readSyncParameters(version, parameters));
assert.throws(() => readSyncParameters(7, { source: [] }));
assert.throws(() => readSyncParameters(7, { ...parameters, unknown: 1 }));
assert.throws(() =>
  buildSyncParameters({ ...replication, pipelineOptions: { "schema.change.behavior": "ignore" } }),
);
console.log(
  "Data Sync product/strategy/scope, generated definitions, startup and transform round-trip contracts passed.",
);

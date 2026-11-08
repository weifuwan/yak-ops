import assert from "node:assert/strict";
import { test } from "node:test";
import {
  createPipelineDefinition,
  addPipelineSourceTables,
  chunkKeyColumn,
  pipelineTableMappings,
  pipelineParameters,
  readPipelineDefinition,
  setPipelineDatasource,
  setPipelineSourceTable,
  setPipelineSinkTable,
  setPipelineChunkKeyColumn,
  setPipelineTargetTableName,
  setPipelineTransformValue,
  transformValue,
  validatePipelineDefinition,
  sourceTable,
  sinkTable,
  createTaskRequest,
  updateTaskRequest,
  publishTaskRequest,
} from "../apps/web/service/data-sync/native-pipeline.ts";

function complete() {
  let definition = createPipelineDefinition();
  definition = setPipelineDatasource(definition, "source", "12");
  definition = setPipelineDatasource(definition, "sink", "34");
  definition = setPipelineSourceTable(definition, { database: "from_db", table: "orders" });
  return setPipelineSinkTable(definition, { database: "to_db", table: "orders" });
}

test("new, edited, saved and reopened Pipeline uses one unmodified contract", () => {
  const initial = complete();
  const saved = pipelineParameters(initial);
  const reopened = readPipelineDefinition(JSON.parse(JSON.stringify(saved)));
  assert.deepEqual(reopened, initial);
  assert.equal(saved.definition.source.tables, "from_db.orders");
  assert.equal(reopened.route[0]["sink-table"], "to_db.orders");
  assert.deepEqual(sourceTable(reopened), { database: "from_db", table: "orders" });
  assert.deepEqual(sinkTable(reopened), { database: "to_db", table: "orders" });
  assert.equal(initial.pipeline.parallelism, 1);
});

test("multiple source tables keep one-to-one native route mappings", () => {
  let definition = createPipelineDefinition();
  definition = setPipelineDatasource(definition, "source", "12");
  definition = setPipelineDatasource(definition, "sink", "34");
  definition = addPipelineSourceTables(definition, [
    { database: "from_db", table: "orders" },
    { database: "from_db", table: "customers" },
  ], "to_db");
  definition = setPipelineTargetTableName(
    definition,
    { database: "from_db", table: "customers" },
    "to_db",
    "customer_archive",
  );

  validatePipelineDefinition(definition);
  assert.equal(definition.source.tables, "from_db.orders,from_db.customers");
  assert.deepEqual(pipelineTableMappings(definition), [
    {
      source: { database: "from_db", table: "orders" },
      target: { database: "to_db", table: "orders" },
    },
    {
      source: { database: "from_db", table: "customers" },
      target: { database: "to_db", table: "customer_archive" },
    },
  ]);
});

test("per-table transform and snapshot chunk key use native Pipeline fields", () => {
  let definition = createPipelineDefinition();
  definition = setPipelineDatasource(definition, "source", "12");
  definition = setPipelineDatasource(definition, "sink", "34");
  definition = addPipelineSourceTables(
    definition,
    [{ database: "from_db", table: "orders" }],
    "to_db",
  );
  const source = { database: "from_db", table: "orders" };

  definition = setPipelineTransformValue(
    definition,
    source,
    "primary-keys",
    "tenant_id,id",
  );
  definition = setPipelineTransformValue(
    definition,
    source,
    "projection",
    "*,name AS user_name",
  );
  definition = setPipelineTransformValue(
    definition,
    source,
    "filter",
    "id > 0",
  );
  definition = setPipelineChunkKeyColumn(definition, source, "id");

  validatePipelineDefinition(definition);
  assert.equal(transformValue(definition, source, "primary-keys"), "tenant_id,id");
  assert.equal(transformValue(definition, source, "projection"), "*,name AS user_name");
  assert.equal(transformValue(definition, source, "filter"), "id > 0");
  assert.equal(chunkKeyColumn(definition, source), "id");
  assert.deepEqual(definition.transform, [{
    "source-table": "from_db.orders",
    "primary-keys": "tenant_id,id",
    projection: "*,name AS user_name",
    filter: "id > 0",
  }]);
  assert.equal(
    definition.source["scan.incremental.snapshot.chunk.key-column"],
    "from_db.orders:id",
  );
});

test("native connector options and routes survive persisted round trip unchanged", () => {
  const historical = complete();
  historical.source["scan.incremental.snapshot.chunk.size"] = 4096;
  historical.source["server-time-zone"] = "Asia/Singapore";
  historical.sink["sink.buffer-flush.max-rows"] = 500;
  historical.route[0].description = "保留原来的路由说明";
  historical.pipeline["schema.operator.uid"] = "schema-uid";
  const result = pipelineParameters(readPipelineDefinition({ definition: historical }));
  assert.deepEqual(result.definition, historical);
});

test("source change invalidates the stale target mapping atomically", () => {
  const changed = setPipelineSourceTable(complete(), { database: "other", table: "new_table" });
  assert.equal(changed.source.tables, changed.route[0]["source-table"]);
  assert.equal(changed.route[0]["sink-table"], "");
  assert.throws(() => validatePipelineDefinition(changed), /映射/);
});

test("unsupported configuration is rejected rather than silently converted", () => {
  const original = complete();
  for (const change of [
    (value) => { value.source["scan.startup.mode"] = "earliest-offset"; },
    (value) => { value.pipeline["execution.runtime-mode"] = "BATCH"; },
    (value) => { value.sink["exclude.schema.changes"] = ["unknown.change"]; },
    (value) => { value.transform = [{ "source-table": "from_db.orders", "partition-keys": "region" }]; },
    (value) => { value.route.push({ "source-table": "x", "sink-table": "y" }); },
    (value) => { value.source.password = "secret"; },
    (value) => { value.pipeline.parallelism = 129; },
    (value) => { value.source["datasource-id"] = "2147483648"; },
  ]) {
    const invalid = structuredClone(original);
    change(invalid);
    assert.throws(() => pipelineParameters(invalid));
  }
  assert.throws(() => readPipelineDefinition({ definition: { source: {} } }));
});

test("native schema change settings are accepted without conversion", () => {
  const pipeline = complete();
  pipeline.pipeline["schema.change.behavior"] = "try_evolve";
  pipeline.sink["exclude.schema.changes"] = [
    "drop.column",
    "truncate.table",
    "drop.table",
    "alter.table.comment",
  ];
  assert.deepEqual(pipelineParameters(pipeline).definition, pipeline);
});

test("changing metadata does not mutate the Pipeline tree", () => {
  const version = pipelineParameters(complete());
  const restored = readPipelineDefinition(version);
  const metadata = { name: "renamed", remark: "edited" };
  assert.deepEqual(restored, version.definition);
  assert.equal(metadata.name, "renamed");
  assert.deepEqual(version.definition, complete());
});

test("create/update/publish requests use one Pipeline contract and a revision guard", () => {
  const pipeline = complete();
  const create = createTaskRequest("orders", "comment", pipeline);
  assert.deepEqual(create.parameters.definition, pipeline);
  const metadataOnly = updateTaskRequest("renamed", "note", pipeline, 7, false);
  assert.equal(metadataOnly.expectedRevision, 7);
  assert.equal("parameters" in metadataOnly, false);
  const updated = updateTaskRequest("renamed", "note", pipeline, 7, true);
  assert.deepEqual(updated.parameters.definition, pipeline);
  assert.deepEqual(publishTaskRequest(8, 8), { version: 8, expectedRevision: 8 });
  assert.deepEqual(pipeline, complete());
});

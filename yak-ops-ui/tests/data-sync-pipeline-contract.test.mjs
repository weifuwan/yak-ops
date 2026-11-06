import assert from "node:assert/strict";
import { test } from "node:test";
import {
  encodeNativePipelineParameters,
  decodeNativePipelineParameters,
} from "../apps/web/service/data-sync/pipeline-definition.ts";

const valid = () => ({
  source: {
    type: "mysql",
    datasourceId: "1",
    startup: { mode: "INITIAL" },
    tables: [{ database: "src_db", table: "orders" }],
  },
  sink: {
    type: "jdbc",
    datasourceId: "2",
    schemaPolicy: {
      initial: "CREATE_OR_ALTER",
      evolution: { enabled: true, allowDropColumns: false },
    },
    tables: [{ database: "dst_db", table: "orders", write: { mode: "UPSERT" } }],
  },
  route: [{ "source-table": "src_db.orders", "sink-table": "dst_db.orders" }],
  transform: [],
  pipeline: {
    "execution.runtime-mode": "STREAMING",
    parallelism: 2,
  },
});

test("native Pipeline definitions preserve datasource references and table routing", () => {
  const saved = encodeNativePipelineParameters(valid());
  const { source, sink, route, pipeline } = saved.definition;
  assert.equal(source.type, "mysql");
  assert.equal(source["datasource-id"], "1");
  assert.equal(source.tables, "\\Qsrc_db.orders\\E");
  assert.deepEqual(sink["exclude.schema.changes"], ["drop.column"]);
  assert.equal(route[0]["sink-table"], "dst_db.orders");
  assert.equal(pipeline["execution.runtime-mode"], "STREAMING");
  assert.equal("pipeline.table-scope" in pipeline, false);
  assert.equal(JSON.stringify(saved).includes("password"), false);

  const reopened = decodeNativePipelineParameters(saved);
  assert.equal(reopened.source.datasourceId, "1");
  assert.equal(reopened.sink.datasourceId, "2");
  assert.deepEqual(reopened.source.tables[0], { database: "src_db", table: "orders" });
  assert.equal(reopened.sink.tables[0].write.mode, "UPSERT");
  assert.equal(reopened.pipeline.parallelism, 2);
});

test("native Connector options and route metadata survive editor round-trips", () => {
  const value = valid();
  value.source.pipelineOptions = {
    "server-time-zone": "Asia/Shanghai",
    "scan.incremental.snapshot.chunk.size": 4096,
  };
  value.sink.pipelineOptions = { "sink.buffer-flush.max-rows": 500 };
  value.route[0].description = "订单表同步";

  const saved = encodeNativePipelineParameters(value);
  assert.equal(saved.definition.source["server-time-zone"], "Asia/Shanghai");
  assert.equal(saved.definition.source["scan.incremental.snapshot.chunk.size"], 4096);
  assert.equal(saved.definition.sink["sink.buffer-flush.max-rows"], 500);
  assert.equal(saved.definition.route[0].description, "订单表同步");

  const reopened = decodeNativePipelineParameters(saved);
  assert.equal(reopened.source.pipelineOptions["server-time-zone"], "Asia/Shanghai");
  assert.equal(reopened.sink.pipelineOptions["sink.buffer-flush.max-rows"], 500);
  assert.equal(reopened.route[0].description, "订单表同步");
});

test("unsupported source, schema policy, and transforms cannot silently degrade", () => {
  const bad = valid();
  bad.source.type = "postgres-cdc";
  assert.throws(() => encodeNativePipelineParameters(bad), /MySQL/);

  const drop = valid();
  drop.sink.schemaPolicy.evolution.allowDropColumns = true;
  assert.throws(() => encodeNativePipelineParameters(drop), /MySQL/);

  const verify = valid();
  verify.sink.schemaPolicy.initial = "VERIFY";
  assert.throws(() => encodeNativePipelineParameters(verify), /MySQL/);

  const transform = valid();
  transform.transform.push({ "source-table": "src_db.orders", projection: "*" });
  assert.throws(() => encodeNativePipelineParameters(transform), /MySQL/);

  const sourceRead = valid();
  sourceRead.source.tables[0].read = { where: "id > 0" };
  assert.throws(() => encodeNativePipelineParameters(sourceRead), /MySQL/);

  const sinkBatch = valid();
  sinkBatch.sink.tables[0].write.batchSize = 500;
  assert.throws(() => encodeNativePipelineParameters(sinkBatch), /MySQL/);
});

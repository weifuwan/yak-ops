import assert from "node:assert/strict";
import { readExecutionReport, readExecutionSummary } from "../apps/web/service/data-sync/report.ts";
import { displaySyncSteps, readSyncParameters } from "../apps/web/service/data-sync/parameters.ts";

const schema = (status = "FINISHED", completed = 1, action = "PREPARED") => ({
  step: "SCHEMA",
  status,
  progressPercent: completed * 100,
  progressUnit: "TABLES",
  readRows: null,
  committedRows: null,
  plannedSplits: null,
  completedSplits: null,
  failureCode: status === "FAILED" ? "SCHEMA_PREPARATION_FAILED" : null,
  plannedTables: 1,
  completedTables: completed,
  action,
});
const full = {
  step: "FULL",
  status: "FINISHED",
  progressPercent: 100,
  progressUnit: "SPLITS",
  readRows: 2,
  committedRows: 2,
  plannedSplits: 1,
  completedSplits: 1,
  failureCode: null,
};
const incremental = {
  step: "INCREMENTAL",
  status: "RUNNING",
  progressPercent: null,
  progressUnit: "RECORDS",
  readRows: 1,
  committedRows: 1,
  plannedSplits: null,
  completedSplits: null,
  failureCode: null,
};
function report(continuous = false) {
  const stages = [schema("FINISHED", 1, null), full, ...(continuous ? [incremental] : [])];
  return {
    version: 3,
    observedAt: new Date().toISOString(),
    generation: continuous ? 1 : 0,
    restored: false,
    counterScope: continuous ? "GENERATION" : "INSTANCE",
    phase: continuous ? "STREAMING" : "FULL",
    checkpoints: { handoffId: continuous ? 4 : null, completedId: continuous ? 4 : null },
    stages,
    tables: [{ mappingIndex: 0, stages: [schema(), ...stages.slice(1)] }],
  };
}
for (const continuous of [false, true]) {
  const value = report(continuous);
  const steps = continuous ? ["SCHEMA", "FULL", "INCREMENTAL"] : ["SCHEMA", "FULL"];
  assert.deepEqual(readExecutionReport(JSON.stringify(value), 1, steps), value);
  const { tables: _tables, ...summary } = value;
  assert.deepEqual(readExecutionSummary(JSON.stringify(summary)), summary);
  assert.equal(readExecutionReport(JSON.stringify(value), 1, steps.slice(1)), undefined);
  assert.equal(readExecutionReport(JSON.stringify(value), 2, steps), undefined);
  for (const modify of [
    (bad) => {
      bad.tables[0].mappingIndex = 1;
    },
    (bad) => {
      bad.stages[0].completedTables = 0;
    },
    (bad) => {
      bad.tables[0].stages[0].action = "DROPPED";
    },
    (bad) => {
      bad.tables[0].stages[0].failureCode = "password-value";
    },
    (bad) => {
      bad.tables[0].stages[0].readRows = 0;
    },
    (bad) => {
      bad.counterScope = continuous ? "INSTANCE" : "GENERATION";
    },
    (bad) => {
      bad.stages[0] = schema("RUNNING", 0, null);
      bad.tables[0].stages[0] = schema("RUNNING", 0, null);
    },
  ]) {
    const bad = structuredClone(value);
    modify(bad);
    assert.equal(readExecutionReport(JSON.stringify(bad), 1, steps), undefined);
  }
}
const partial = report();
const waitingFull = {
  ...full,
  status: "CANCELED",
  progressPercent: null,
  readRows: null,
  committedRows: null,
  plannedSplits: null,
  completedSplits: null,
};
partial.phase = "SCHEMA";
partial.stages = [
  { ...schema("FAILED", 1, null), plannedTables: 3, progressPercent: 33 },
  waitingFull,
];
partial.tables = [
  { mappingIndex: 0, stages: [schema(), waitingFull] },
  { mappingIndex: 1, stages: [schema("FAILED", 0, null), waitingFull] },
  { mappingIndex: 2, stages: [schema("CANCELED", 0, null), waitingFull] },
];
assert.deepEqual(readExecutionReport(JSON.stringify(partial), 3, ["SCHEMA", "FULL"]), partial);
const unknown = structuredClone(partial);
unknown.stages[0].status = "UNKNOWN";
unknown.stages[0].failureCode = null;
unknown.tables[1].stages[0] = schema("UNKNOWN", 0, null);
assert.deepEqual(readExecutionReport(JSON.stringify(unknown), 3, ["SCHEMA", "FULL"]), unknown);
const restored = report(true);
restored.generation = 2;
restored.restored = true;
restored.stages[1] = {
  ...full,
  readRows: null,
  committedRows: null,
  plannedSplits: null,
  completedSplits: null,
};
restored.tables[0].stages[1] = restored.stages[1];
assert.deepEqual(
  readExecutionReport(JSON.stringify(restored), 1, ["SCHEMA", "FULL", "INCREMENTAL"]),
  restored,
);
for (const kind of [
  "SINGLE_TABLE_OFFLINE",
  "DATABASE_OFFLINE",
  "SINGLE_TABLE_REALTIME",
  "DATABASE_REALTIME",
]) {
  const dataSteps = ["FULL", ...(kind.endsWith("REALTIME") ? ["INCREMENTAL"] : [])];
  const parameters = {
    solutionType: kind,
    steps: dataSteps,
    source: {
      datasourceId: "source",
      tables: [{ id: "orders", table: "orders" }],
    },
    transform: [],
    sink: {
      datasourceId: "target",
      schemaPolicy: {
        initial: "CREATE_OR_ALTER",
        evolution: { enabled: false, allowDropColumns: false },
      },
      tables: [{ input: "orders", table: "new_orders", write: { mode: "UPSERT" } }],
    },
    env: {},
  };
  assert.deepEqual(displaySyncSteps(readSyncParameters(4, parameters)), ["SCHEMA", ...dataSteps]);
  for (const invalid of [
    ["SCHEMA"],
    ["SCHEMA", ...dataSteps],
    [...dataSteps, ...dataSteps],
    ["INCREMENTAL"],
  ]) {
    assert.throws(() => readSyncParameters(4, { ...parameters, steps: invalid }));
  }
}
console.log(
  "Schema migration contracts passed: legal combinations, pinned stages, partial results, uncertainty and restored counters",
);

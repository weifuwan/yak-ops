import assert from "node:assert/strict";

import {
  canCancelTaskInstance,
  canManageTaskDefinition,
  isTaskInstanceActive,
  matchesDataSyncInstance,
  toDataSyncInstanceRecord,
} from "../apps/web/service/data-sync/execution.ts";
import { createTaskRunIdempotencyKey } from "../apps/web/service/task-execution/keys.ts";

const key = createTaskRunIdempotencyKey();
assert.match(key, /^[0-9a-f-]{36}$/);
assert.notEqual(key, createTaskRunIdempotencyKey());

const version = {
  definitionId: "definition-1",
  version: 2,
  name: "orders",
  taskType: "DATA_SYNC",
  parameterSchemaVersion: 7,
  parameters: {
    source: {
      type: "mysql",
      datasourceId: "mysql-source",
      startup: { mode: "INITIAL" },
      tables: [{ table: "orders" }],
    },
    sink: {
      type: "jdbc-postgres",
      datasourceId: "pg-target",
      schemaPolicy: {
        initial: "VERIFY",
        evolution: { enabled: false, allowDropColumns: false },
      },
      tables: [{ schema: "public", table: "ods_orders", write: { mode: "UPSERT" } }],
    },
    route: [{ "source-table": "orders", "sink-table": "public.ods_orders" }],
    transform: [],
    pipeline: { "execution.runtime-mode": "STREAMING" },
  },
};
const instance = {
  id: "instance-1",
  definitionId: "definition-1",
  definitionVersion: 2,
  taskType: "DATA_SYNC",
  triggerType: "MANUAL",
  status: "RECONCILING",
  runtimeEngine: "YAKFLOW",
  runtimeId: "job-1",
  createTime: "2026-10-13T09:00:00",
  failureCode: "EXECUTION_OWNER_UNCERTAIN",
};
const mapped = toDataSyncInstanceRecord(instance, version);
assert.equal(mapped.id, instance.id);
assert.equal(mapped.taskId, "definition-1");
assert.equal(mapped.taskVersion, 2);
assert.equal(mapped.productType, "DATABASE_REPLICATION");
assert.equal(mapped.definitionSnapshot.productType, "DATABASE_REPLICATION");
assert.equal(mapped.syncType, "REALTIME");
assert.equal(mapped.status, "RECONCILING");
assert.equal(mapped.runtimeId, "job-1");
assert.equal(mapped.errorMessage, "EXECUTION_OWNER_UNCERTAIN");
assert.equal(mapped.definitionSnapshot.source.dataSourceId, "mysql-source");
assert.equal(mapped.definitionSnapshot.target.schema, "public");
assert.equal(mapped.readRows, undefined);
assert.equal(mapped.writeRows, undefined);
assert.equal(mapped.triggerType, "MANUAL");
assert.equal(isTaskInstanceActive(mapped.status), true);
assert.equal(canCancelTaskInstance(mapped.status), false);
assert.equal(canCancelTaskInstance("SUBMITTED"), true);
assert.equal(canCancelTaskInstance("LAUNCHING"), true);
assert.equal(canCancelTaskInstance("RUNNING"), true);
assert.equal(canCancelTaskInstance("CANCELLING"), false);
assert.equal(isTaskInstanceActive("LAUNCHING"), true);
assert.equal(isTaskInstanceActive("FINISHED"), false);
assert.equal(isTaskInstanceActive("CANCELED"), false);

for (const status of ["SUBMITTED", "LAUNCHING", "RUNNING", "CANCELLING", "RECONCILING"]) {
  assert.equal(canManageTaskDefinition(true, { status }), false);
}
for (const status of ["FINISHED", "FAILED", "CANCELED"]) {
  assert.equal(canManageTaskDefinition(true, { status }), true);
}
assert.equal(canManageTaskDefinition(true), true);
assert.equal(canManageTaskDefinition(false), false);
assert.equal(canManageTaskDefinition(false, { status: "FINISHED" }), false);
assert.equal(canManageTaskDefinition(true, { status: "UNKNOWN" }), false);

assert.equal(
  matchesDataSyncInstance(mapped, { pageNo: 1, pageSize: 20, syncType: "REALTIME" }),
  true,
);
assert.equal(
  matchesDataSyncInstance(mapped, { pageNo: 1, pageSize: 20, syncType: "OFFLINE" }),
  false,
);
assert.equal(
  matchesDataSyncInstance(mapped, { pageNo: 1, pageSize: 20, status: "RUNNING" }),
  false,
);
assert.equal(matchesDataSyncInstance(mapped, { pageNo: 1, pageSize: 20, activeOnly: true }), true);
assert.equal(matchesDataSyncInstance(mapped, { pageNo: 1, pageSize: 20, keyword: "orders" }), true);
assert.equal(
  matchesDataSyncInstance(mapped, { pageNo: 1, pageSize: 20, triggerType: "MANUAL" }),
  true,
);
assert.equal(
  matchesDataSyncInstance(mapped, { pageNo: 1, pageSize: 20, triggerType: "WORKFLOW" }),
  false,
);

assert.throws(() => toDataSyncInstanceRecord(instance, { ...version, version: 3 }));
assert.throws(() => toDataSyncInstanceRecord(instance, { ...version, parameterSchemaVersion: 2 }));
assert.throws(() => toDataSyncInstanceRecord({ ...instance, taskType: "UNKNOWN" }, version));

const scheduled = toDataSyncInstanceRecord(
  {
    ...instance,
    triggerType: "SCHEDULE",
    scheduleId: "deleted-schedule",
    scheduleRevision: 7,
    scheduledTime: "2026-10-13T01:00:00Z",
  },
  version,
);
assert.equal(scheduled.scheduleId, "deleted-schedule");
assert.equal(scheduled.scheduleRevision, 7);
assert.equal(scheduled.scheduledTime, "2026-10-13T01:00:00Z");
assert.equal(mapped.scheduleId, undefined);
assert.equal(mapped.scheduledTime, undefined);

for (const [mode, runtime, steps] of [
  ["INITIAL", "STREAMING", ["FULL", "INCREMENTAL"]],
  ["LATEST_OFFSET", "STREAMING", ["INCREMENTAL"]],
  ["SNAPSHOT", "BATCH", ["FULL"]],
]) {
  for (const scope of ["SINGLE", "DATABASE"]) {
    const strategyVersion = {
      ...version,
      parameters: {
        ...version.parameters,
        source: { ...version.parameters.source, startup: { mode } },
        pipeline: { "execution.runtime-mode": runtime, "pipeline.table-scope": scope },
      },
    };
    const result = toDataSyncInstanceRecord(instance, strategyVersion);
    assert.equal(result.productType, "DATABASE_REPLICATION");
    assert.equal(result.definitionSnapshot.productType, "DATABASE_REPLICATION");
    assert.equal(result.syncType, runtime === "BATCH" ? "OFFLINE" : "REALTIME");
    assert.deepEqual(result.steps, steps);
    assert.deepEqual(result.definitionSnapshot.steps, steps);
  }
}

const snapshotVersion = {
  ...version,
  parameters: {
    ...version.parameters,
    source: { ...version.parameters.source, startup: { mode: "SNAPSHOT" } },
    pipeline: { "execution.runtime-mode": "BATCH", "pipeline.table-scope": "DATABASE" },
  },
};
const finishedReport = JSON.stringify({
  version: 1,
  observedAt: "2026-10-13T09:05:00Z",
  tables: [
    {
      mappingIndex: 0,
      step: "FULL",
      status: "FINISHED",
      progressPercent: 100,
      progressUnit: "SPLITS",
      readRows: 2,
      committedRows: 2,
      plannedSplits: 1,
      completedSplits: 1,
      failureCode: null,
    },
  ],
});
const finishedSnapshot = toDataSyncInstanceRecord(
  {
    ...instance,
    status: "FINISHED",
    failureCode: null,
    executionReport: finishedReport,
    triggerType: "SCHEDULE",
    scheduleId: "snapshot-schedule",
    scheduleRevision: 3,
  },
  snapshotVersion,
);
assert.equal(finishedSnapshot.productType, "DATABASE_REPLICATION");
assert.equal(finishedSnapshot.syncType, "OFFLINE");
assert.equal(finishedSnapshot.status, "FINISHED");
assert.equal(finishedSnapshot.executionReport.tables[0].committedRows, 2);
assert.equal(finishedSnapshot.scheduleId, "snapshot-schedule");
assert.equal(isTaskInstanceActive(finishedSnapshot.status), false);
assert.equal(canCancelTaskInstance(finishedSnapshot.status), false);
assert.equal(canManageTaskDefinition(true, finishedSnapshot), true);
assert.equal(
  matchesDataSyncInstance(finishedSnapshot, { pageNo: 1, pageSize: 20, syncType: "OFFLINE" }),
  true,
);
assert.equal(
  matchesDataSyncInstance(finishedSnapshot, { pageNo: 1, pageSize: 20, activeOnly: true }),
  false,
);

const offlineVersion = {
  ...version,
  parameters: {
    ...version.parameters,
    source: {
      type: "jdbc-mysql",
      datasourceId: "mysql-source",
      syncMode: "FULL",
      tables: [{ table: "orders" }],
    },
    pipeline: { "execution.runtime-mode": "BATCH", "pipeline.table-scope": "SINGLE" },
  },
};
const offlineExecution = toDataSyncInstanceRecord(instance, offlineVersion);
assert.equal(offlineExecution.productType, "SINGLE_TABLE_BATCH");
assert.equal(offlineExecution.syncType, "OFFLINE");
assert.deepEqual(offlineExecution.steps, ["FULL"]);
assert.throws(
  () =>
    toDataSyncInstanceRecord(instance, {
      ...offlineVersion,
      parameters: {
        ...offlineVersion.parameters,
        pipeline: { "execution.runtime-mode": "BATCH", "pipeline.table-scope": "DATABASE" },
      },
    }),
  /多表 JDBC/,
);

const newerVersion = {
  ...snapshotVersion,
  version: 3,
  name: "new name",
  parameters: {
    ...snapshotVersion.parameters,
    source: { ...snapshotVersion.parameters.source, tables: [{ table: "customers" }] },
    route: [{ "source-table": "customers", "sink-table": "public.ods_orders" }],
  },
};
const newerExecution = toDataSyncInstanceRecord(
  { ...instance, definitionVersion: 3 },
  newerVersion,
);
assert.equal(newerExecution.taskName, "new name");
assert.equal(newerExecution.definitionSnapshot.source.table, "customers");
assert.equal(newerExecution.syncType, "OFFLINE");
assert.equal(mapped.taskName, "orders");
assert.equal(mapped.definitionSnapshot.source.table, "orders");
assert.equal(mapped.syncType, "REALTIME");
assert.throws(() => toDataSyncInstanceRecord(instance, newerVersion), /version mismatch/);

console.log(
  "Task Execution product/runtime separation, bound-version history, scheduling and lifecycle contracts passed.",
);

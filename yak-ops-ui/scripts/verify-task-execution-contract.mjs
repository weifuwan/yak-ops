import assert from "node:assert/strict";

import {
  canCancelTaskInstance,
  isTaskInstanceActive,
  matchesDataSyncInstance,
  toDataSyncAttemptRecord,
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
  parameterSchemaVersion: 2,
  parameters: JSON.stringify({
    source: { type: "MYSQL_CDC", datasourceId: "mysql-source", table: "orders" },
    transform: [],
    sink: {
      type: "JDBC",
      datasourceId: "pg-target",
      schema: "public",
      table: "ods_orders",
      writeMode: "UPSERT",
    },
  }),
};
const instance = {
  id: "instance-1",
  definitionId: "definition-1",
  definitionVersion: 2,
  taskType: "DATA_SYNC",
  triggerType: "MANUAL",
  status: "RECONCILING",
  createTime: "2026-10-13T09:00:00",
  failureCode: "EXECUTION_OWNER_UNCERTAIN",
};
const mapped = toDataSyncInstanceRecord(instance, version);
assert.equal(mapped.id, instance.id);
assert.equal(mapped.taskId, "definition-1");
assert.equal(mapped.taskVersion, 2);
assert.equal(mapped.syncType, "REALTIME");
assert.equal(mapped.status, "RECONCILING");
assert.equal(mapped.errorMessage, "EXECUTION_OWNER_UNCERTAIN");
assert.equal(mapped.definitionSnapshot.source.dataSourceId, "mysql-source");
assert.equal(mapped.definitionSnapshot.target.schema, "public");
assert.equal(mapped.readRows, undefined);
assert.equal(mapped.writeRows, undefined);
assert.equal(mapped.triggerType, "MANUAL");
assert.equal(isTaskInstanceActive(mapped.status), true);
assert.equal(canCancelTaskInstance(mapped.status), false);
assert.equal(canCancelTaskInstance("SUBMITTED"), true);
assert.equal(canCancelTaskInstance("RUNNING"), true);
assert.equal(canCancelTaskInstance("CANCELLING"), false);
assert.equal(isTaskInstanceActive("FINISHED"), false);
assert.equal(isTaskInstanceActive("CANCELED"), false);

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
assert.throws(() => toDataSyncInstanceRecord({ ...instance, taskType: "UNKNOWN" }, version));

const attempt = toDataSyncAttemptRecord(
  {
    id: "attempt-1",
    attemptNumber: 1,
    status: "INTERRUPTED",
    runtimeEngine: "YAKFLOW",
    runtimeId: "job-1",
    logReference: "local:attempt-1",
    failureCode: "EXECUTION_OWNER_UNCERTAIN",
  },
  instance.id,
);
assert.equal(attempt.executionId, instance.id);
assert.equal(attempt.attemptNo, 1);
assert.equal(attempt.status, "INTERRUPTED");
assert.equal(attempt.runtimeId, "job-1");
assert.equal(attempt.logReference, "local:attempt-1");
assert.equal(attempt.failureCode, "EXECUTION_OWNER_UNCERTAIN");
assert.equal(attempt.readRows, undefined);

console.log("Task Execution frontend contract checks passed");

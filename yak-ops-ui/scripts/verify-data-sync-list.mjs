import assert from "node:assert/strict";
import { projectSyncTaskPage } from "../apps/web/service/data-sync/definition.ts";
import { readSyncParameters } from "../apps/web/service/data-sync/parameters.ts";
import {
  parseTaskListFilters,
  updateTaskListFilters,
} from "../apps/web/app/data-sync/list/filters.ts";
import { listQueryKeys } from "../apps/web/app/data-sync/list/query-keys.ts";

assert.deepEqual(parseTaskListFilters(new URLSearchParams()), {
  pageNo: 1,
  keyword: "",
  solutionType: "ALL",
  status: "ALL",
});
assert.deepEqual(
  parseTaskListFilters(new URLSearchParams("page=-3&solutionType=UNKNOWN&status=RUNNING")),
  {
    pageNo: 1,
    keyword: "",
    solutionType: "ALL",
    status: "ALL",
  },
);
assert.deepEqual(
  parseTaskListFilters(
    new URLSearchParams(
      "page=4&keyword=%20orders%20&solutionType=DATABASE_OFFLINE&status=PUBLISHED",
    ),
  ),
  {
    pageNo: 4,
    keyword: "orders",
    solutionType: "DATABASE_OFFLINE",
    status: "PUBLISHED",
  },
);
const original = new URLSearchParams(
  "create=1&page=3&keyword=orders&solutionType=DATABASE_OFFLINE",
);
const updated = updateTaskListFilters(original, {
  keyword: " updated ",
  status: "UNPUBLISHED",
  pageNo: 1,
});
assert.equal(updated.get("create"), "1");
assert.equal(updated.get("page"), null);
assert.equal(updated.get("keyword"), "updated");
assert.equal(updated.get("status"), "UNPUBLISHED");
assert.equal(updated.get("solutionType"), "DATABASE_OFFLINE");
assert.equal(original.get("keyword"), "orders");
assert.notDeepEqual(
  listQueryKeys.tasks("user-a:workspace-a"),
  listQueryKeys.tasks("user-a:workspace-b"),
);
assert.notDeepEqual(
  listQueryKeys.schedules("user-a:workspace-a"),
  listQueryKeys.schedules("user-b:workspace-a"),
);
console.log("Data Sync list filter and scope checks passed.");

const baseParameters = {
  source: {
    type: "MYSQL_CDC",
    datasourceId: "source",
    startup: { mode: "INITIAL" },
    tables: [{ table: "orders" }],
  },
  sink: {
    type: "JDBC",
    datasourceId: "target",
    schemaPolicy: {
      initial: "VERIFY",
      evolution: { enabled: false, allowDropColumns: false },
    },
    tables: [{ table: "orders_copy", write: { mode: "UPSERT" } }],
  },
  route: [{ "source-table": "orders", "sink-table": "orders_copy" }],
  transform: [],
  pipeline: { "execution.runtime-mode": "STREAMING" },
};
const definition = (id, parameters, parameterSchemaVersion = 6) => ({
  id,
  name: "task-" + id,
  status: "PUBLISHED",
  taskType: "DATA_SYNC",
  definitionVersion: 4,
  parameterSchemaVersion,
  parameters,
  updateBy: "operator",
  updateTime: "2026-10-25 12:00:00",
});
const normal = definition("valid", baseParameters);
const obsolete = definition("old-version", baseParameters, 3);
assert.throws(() => readSyncParameters(3, obsolete.parameters));
const validXa = definition("xa", {
  ...baseParameters,
  source: {
    ...baseParameters.source,
    tables: [
      {
        ...baseParameters.source.tables[0],
        snapshot: { chunkKeyColumn: "bucket" },
      },
    ],
  },
  sink: {
    ...baseParameters.sink,
    tables: [
      {
        table: "orders_copy",
        write: {
          mode: "RETRACT",
          transaction: { mode: "CHECKPOINT_XA" },
        },
      },
    ],
  },
});
const page = {
  bizData: [
    normal,
    obsolete,
    validXa,
    definition("invalid", { ...baseParameters, sink: null }, 99),
    definition("unknown-version", baseParameters, 99),
  ],
  pagination: { pageNo: 2, pageSize: 20, total: 45 },
};
const projected = projectSyncTaskPage(page);
assert.deepEqual(projected.pagination, page.pagination);
assert.deepEqual(
  projected.bizData.map((task) => task.id),
  ["valid", "old-version", "xa", "invalid", "unknown-version"],
);
assert.equal(projected.bizData[0].syncType, "REALTIME");
assert.equal(projected.bizData[2].chunkKeyColumn, "bucket");
assert.equal(projected.bizData[2].tableMappings[0].source.chunkKeyColumn, "bucket");
assert.equal(projected.bizData[2].writeMode, "RETRACT");
for (const record of [projected.bizData[1], projected.bizData[3], projected.bizData[4]]) {
  assert.equal(record.name, "task-" + record.id);
  assert.equal(record.definitionVersion, 4);
  assert.equal(record.status, "PUBLISHED");
  assert.equal(record.updateBy, "operator");
  assert.ok(record.configurationError);
  assert.equal("sourceDataSourceId" in record, false);
}
assert.match(projected.bizData[1].configurationError, /不支持的同步任务参数版本/);
assert.match(projected.bizData[4].configurationError, /不支持的同步任务参数版本/);
assert.equal("configurationError" in projected.bizData[0], false);
assert.equal("configurationError" in projected.bizData[2], false);
assert.throws(() => projectSyncTaskPage({ bizData: null, pagination: page.pagination }));
console.log("Data Sync strict V6 list projection and unreadable definition isolation passed.");

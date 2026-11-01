import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";
import * as jsxRuntime from "react/jsx-runtime";
import ts from "typescript";
import { projectSyncTaskPage } from "../apps/web/service/data-sync/definition.ts";
import { readSyncParameters } from "../apps/web/service/data-sync/parameters.ts";
import {
  canCancelTaskInstance,
  canManageTaskDefinition,
} from "../apps/web/service/data-sync/execution.ts";
import { readExecutionSummary } from "../apps/web/service/data-sync/report.ts";
import { syncSummary } from "../apps/web/app/data-sync/solution/presentation.ts";
import {
  parseTaskListFilters,
  updateTaskListFilters,
} from "../apps/web/app/data-sync/list/filters.ts";
import { listQueryKeys } from "../apps/web/app/data-sync/list/query-keys.ts";

assert.deepEqual(parseTaskListFilters(new URLSearchParams()), {
  pageNo: 1,
  keyword: "",
  productType: "ALL",
  status: "ALL",
});
assert.deepEqual(
  parseTaskListFilters(new URLSearchParams("page=-3&productType=UNKNOWN&status=RUNNING")),
  {
    pageNo: 1,
    keyword: "",
    productType: "ALL",
    status: "ALL",
  },
);
assert.deepEqual(
  parseTaskListFilters(
    new URLSearchParams(
      "page=4&keyword=%20orders%20&productType=DATABASE_REPLICATION&status=PUBLISHED",
    ),
  ),
  {
    pageNo: 4,
    keyword: "orders",
    productType: "DATABASE_REPLICATION",
    status: "PUBLISHED",
  },
);
const original = new URLSearchParams(
  "create=1&page=3&keyword=orders&productType=DATABASE_REPLICATION",
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
assert.equal(updated.get("productType"), "DATABASE_REPLICATION");
assert.equal(original.get("keyword"), "orders");
const cleared = updateTaskListFilters(updated, { productType: "ALL", keyword: "" });
assert.equal(cleared.get("productType"), null);
assert.equal(cleared.get("keyword"), null);
assert.equal(
  parseTaskListFilters(new URLSearchParams("productType=SINGLE_TABLE_BATCH")).productType,
  "SINGLE_TABLE_BATCH",
);
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
    type: "mysql",
    datasourceId: "source",
    startup: { mode: "INITIAL" },
    tables: [{ table: "orders" }],
  },
  sink: {
    type: "jdbc-postgres",
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
const definition = (id, parameters, parameterSchemaVersion = 7) => ({
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
const snapshot = definition("snapshot", {
  ...baseParameters,
  source: { ...baseParameters.source, startup: { mode: "SNAPSHOT" } },
  pipeline: { "execution.runtime-mode": "BATCH", "pipeline.table-scope": "DATABASE" },
});
const singleBatch = definition("single-batch", {
  ...baseParameters,
  source: {
    type: "jdbc-mysql",
    datasourceId: "source",
    syncMode: "FULL",
    tables: [{ table: "orders" }],
  },
  pipeline: { "execution.runtime-mode": "BATCH", "pipeline.table-scope": "SINGLE" },
});
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
    snapshot,
    singleBatch,
  ],
  pagination: { pageNo: 2, pageSize: 20, total: 45 },
};
const projected = projectSyncTaskPage(page);
assert.deepEqual(projected.pagination, page.pagination);
assert.deepEqual(
  projected.bizData.map((task) => task.id),
  ["valid", "old-version", "xa", "invalid", "unknown-version", "snapshot", "single-batch"],
);
assert.equal(projected.bizData[0].syncType, "REALTIME");
assert.equal(projected.bizData[0].productType, "DATABASE_REPLICATION");
assert.equal(projected.bizData[5].productType, "DATABASE_REPLICATION");
assert.equal(projected.bizData[5].syncType, "OFFLINE");
assert.deepEqual(projected.bizData[5].steps, ["FULL"]);
assert.equal(projected.bizData[6].productType, "SINGLE_TABLE_BATCH");
assert.equal(projected.bizData[6].syncType, "OFFLINE");
assert.equal(projected.bizData[2].tableMappings[0].source.snapshot.chunkKeyColumn, "bucket");
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
console.log(
  "Data Sync strict Pipeline list projection and unreadable definition isolation passed.",
);

function compile(file, mocks) {
  const { outputText } = ts.transpileModule(readFileSync(new URL(file, import.meta.url), "utf8"), {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
      jsx: ts.JsxEmit.ReactJSX,
    },
  });
  const exports = {};
  vm.runInNewContext(outputText, {
    exports,
    require(name) {
      if (name === "react/jsx-runtime") return jsxRuntime;
      if (Object.hasOwn(mocks, name)) return mocks[name];
      throw Error("Unexpected dependency: " + name);
    },
  });
  return exports;
}

let pageQuery;
let pageRequest;
const { useTaskPage } = compile("../apps/web/app/data-sync/list/use-list-queries.ts", {
  "@tanstack/react-query": {
    useQuery: (options) => {
      pageQuery = options;
      return { isSuccess: true };
    },
  },
  react: { useMemo: (factory) => factory() },
  "@/service/datasource": {},
  "@/service/data-sync": {
    listDataSyncTasks: async (request) => {
      pageRequest = request;
    },
  },
  "@/service/schedule": {},
  "@/service/task-execution": {},
  "@/service/user": {},
  "./query-keys": { listQueryKeys },
});
const replicationFilter = parseTaskListFilters(
  new URLSearchParams("productType=DATABASE_REPLICATION&page=3"),
);
useTaskPage("user-a:workspace-a", replicationFilter);
await pageQuery.queryFn({ signal: new AbortController().signal });
assert.equal(pageRequest.productType, "DATABASE_REPLICATION");
assert.equal(pageRequest.pageNo, 3);
assert.equal(pageRequest.pageSize, 20);
const replicationKey = [...pageQuery.queryKey];
useTaskPage("user-a:workspace-a", { ...replicationFilter, productType: "SINGLE_TABLE_BATCH" });
assert.notDeepEqual([...pageQuery.queryKey], replicationKey);

const { TaskTable } = compile("../apps/web/app/data-sync/list/task-table.tsx", {
  "@yak-ops/yak-ui": { Table: "Table", Skeleton: "Skeleton", Button: "Button" },
  "react-router-dom": { useNavigate: () => () => {} },
  "@/app/schedule/task-schedule-cell": { TaskScheduleCell: "TaskScheduleCell" },
  "../execution/execution-progress-bar": { ExecutionProgressBar: "ExecutionProgressBar" },
  "../execution/execution-stages": { ExecutionStages: "ExecutionStages" },
  "./task-route": { TaskRoute: "TaskRoute" },
  "./task-lifecycle": {
    DataSyncTaskLifecycleActions: "DataSyncTaskLifecycleActions",
    DataSyncTaskStatusBadge: "DataSyncTaskStatusBadge",
  },
  "@/service/data-sync/report": { readExecutionSummary },
  "@/service/data-sync/execution": { canCancelTaskInstance, canManageTaskDefinition },
  "../solution/presentation": { syncSummary },
});
const activeByTask = new Map();
const latestByTask = new Map();
const table = TaskTable({
  records: projected.bizData,
  pageNo: 2,
  total: 45,
  loading: false,
  failed: false,
  data: {
    dataSourceMap: new Map(),
    updateUserMap: new Map(),
    schedules: { records: new Map(), loading: false, error: false },
    latestByTask,
    activeByTask,
    instancesReady: true,
  },
  actions: {},
});
const renderCell = (key, task) =>
  table.props.columns.find((column) => column.key === key).render(undefined, task);
const boundedReplication = projected.bizData[5];
assert.equal(
  renderCell("product", boundedReplication).props.children[0].props.children,
  syncSummary("DATABASE_REPLICATION", ["FULL"]),
);
assert.equal(renderCell("schedule", boundedReplication).type, "TaskScheduleCell");
assert.equal(renderCell("actions", boundedReplication).props.runtimeAction.label, "运行");
assert.equal(renderCell("actions", boundedReplication).props.runtimeAction.disabled, false);
assert.equal(renderCell("actions", projected.bizData[0]).props.runtimeAction.label, "启动");
assert.notEqual(renderCell("schedule", projected.bizData[0]).type, "TaskScheduleCell");
activeByTask.set(boundedReplication.id, { id: "active-snapshot", status: "RUNNING" });
assert.equal(renderCell("actions", boundedReplication).props.runtimeAction.label, "停止");
assert.equal(renderCell("actions", boundedReplication).props.runtimeAction.disabled, false);
assert.equal(renderCell("actions", projected.bizData[1]).props.children, "查看");
latestByTask.set(projected.bizData[0].id, {
  id: "previous-snapshot",
  status: "FINISHED",
  progressPercent: 100,
});
const previousSnapshot = renderCell("execution", projected.bizData[0]).props.children;
assert.equal(previousSnapshot.type, "ExecutionProgressBar");
assert.equal(previousSnapshot.props.percent, 100);
assert.equal(previousSnapshot.props.realtime, false);
latestByTask.set(boundedReplication.id, {
  id: "previous-stream",
  status: "RUNNING",
  executionReport: JSON.stringify({
    version: 2,
    observedAt: "2030-10-14T00:01:00Z",
    generation: 0,
    restored: false,
    counterScope: "GENERATION",
    phase: "STREAMING",
    checkpoints: { handoffId: 1, completedId: 1 },
    stages: [
      {
        step: "FULL",
        status: "FINISHED",
        progressUnit: "SPLITS",
        progressPercent: 100,
        readRows: 1,
        committedRows: 1,
        plannedSplits: 1,
        completedSplits: 1,
        failureCode: null,
      },
      {
        step: "INCREMENTAL",
        status: "RUNNING",
        progressUnit: "RECORDS",
        progressPercent: null,
        readRows: 1,
        committedRows: 1,
        plannedSplits: null,
        completedSplits: null,
        failureCode: null,
      },
    ],
  }),
});
assert.equal(renderCell("execution", boundedReplication).props.children.type, "ExecutionStages");
console.log("Product filtering and bounded replication list actions passed.");

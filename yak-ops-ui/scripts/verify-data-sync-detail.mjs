import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";
import * as jsxRuntime from "react/jsx-runtime";
import ts from "typescript";
import { detailQueryKeys } from "../apps/web/app/data-sync/detail/query-keys.ts";
import { syncSummary } from "../apps/web/app/data-sync/solution/presentation.ts";
import {
  canManageTaskDefinition,
  isTaskInstanceActive,
} from "../apps/web/service/data-sync/execution.ts";

assert.notDeepEqual(
  detailQueryKeys.activeInstance("user-a:ws-a", "t1"),
  detailQueryKeys.activeInstance("user-a:ws-b", "t1"),
);
assert.notDeepEqual(
  detailQueryKeys.task("user-a:ws-a", "t1"),
  detailQueryKeys.task("user-b:ws-a", "t1"),
);
assert.notDeepEqual(
  detailQueryKeys.instance("user-a:ws-a", "t1", "i1"),
  detailQueryKeys.instance("user-a:ws-b", "t1", "i1"),
);
assert.notDeepEqual(
  detailQueryKeys.instancePage("user-a:ws-a", "t1", 1, "ALL"),
  detailQueryKeys.instancePage("user-a:ws-a", "t1", 2, "ALL"),
);
assert.notDeepEqual(
  detailQueryKeys.instancePage("user-a:ws-a", "t1", 1, "RUNNING"),
  detailQueryKeys.instancePage("user-a:ws-a", "t1", 1, "ALL"),
);
assert.notDeepEqual(
  detailQueryKeys.instance("user-a:ws-a", "t1", "i1"),
  detailQueryKeys.instance("user-a:ws-a", "t2", "i1"),
);

const actionsSource = readFileSync(
  new URL("../apps/web/app/data-sync/detail/use-execution-actions.ts", import.meta.url),
  "utf8",
);
const { outputText } = ts.transpileModule(actionsSource, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
});
function createActions({ active = [], instanceStatus = "RUNNING", published = true } = {}) {
  const called = [];
  const services = {
    "@yak-ops/yak-ui": {
      toast: {
        success: (msg) => called.push("toast:" + msg),
        error: (msg) => called.push("error:" + msg),
      },
    },
    "@/service/data-sync": {
      runDataSyncTask: async () => {
        called.push("submit");
        return { instanceId: "i-new" };
      },
      cancelDataSyncInstance: async () => {
        called.push("cancel");
      },
    },
    "@/service/data-sync/execution": { canCancelTaskInstance: (s) => s === "RUNNING" },
    "@/service/task-execution": {
      listLatestTaskInstances: async () => {
        called.push("check-latest");
        return active;
      },
    },
    react: { useState: (initial) => [initial, (next) => called.push("state:" + String(next))] },
  };
  const exports = {};
  vm.runInNewContext(outputText, {
    exports,
    require(name) {
      if (Object.hasOwn(services, name)) return services[name];
      throw Error("Unexpected dependency: " + name);
    },
    Error,
  });
  const actions = exports.useExecutionActions({
    taskId: "t1",
    task: { id: "t1", status: published ? "PUBLISHED" : "UNPUBLISHED" },
    canStart: true,
    selectedInstance: { id: "i1", status: instanceStatus },
    refreshActiveInstances: async () => called.push("refresh-active"),
    refreshAfterCancel: async () => called.push("refresh-detail"),
    selectSubmitted: (id) => called.push("select:" + id),
  });
  return { actions, called };
}

const normal = createActions();
await normal.actions.run();
assert.deepEqual(
  normal.called.filter((s) =>
    ["check-latest", "submit", "refresh-active", "select:i-new"].includes(s),
  ),
  ["check-latest", "submit", "refresh-active", "select:i-new"],
);

const conflict = createActions({ active: [{ id: "i-old" }] });
await conflict.actions.run();
assert.ok(conflict.called.includes("error:任务正在执行，请先停止"));
assert.ok(conflict.called.includes("refresh-active"));
assert.equal(
  conflict.called.includes("submit"),
  false,
  "never submit if an active instance exists",
);

const offline = createActions({ published: false });
await offline.actions.run();
assert.equal(offline.called.length, 0, "unpublished tasks cannot execute");

const stopped = createActions();
await stopped.actions.cancel();
assert.ok(stopped.called.includes("cancel"));
assert.ok(stopped.called.includes("refresh-active"));
assert.ok(stopped.called.includes("refresh-detail"));
assert.ok(stopped.called.indexOf("cancel") < stopped.called.indexOf("refresh-detail"));

const finalized = createActions({ instanceStatus: "FINISHED" });
await finalized.actions.cancel();
assert.equal(finalized.called.length, 0, "finished executions cannot be canceled");

const logSource = readFileSync(
  new URL("../apps/web/app/data-sync/detail/log/use-instance-logs.ts", import.meta.url),
  "utf8",
);
assert.match(logSource, /activeRef\.current = active/);
assert.match(logSource, /finalPollsRemainingRef\.current = 2/);
assert.match(logSource, /finalPollsRemainingRef\.current -= 1/);
assert.match(logSource, /\[instanceId, revision\]/);
assert.doesNotMatch(logSource, /\[instanceId, active, revision\]/);
assert.match(logSource, /trimLogContent\(previous \+ page\.content\)/);
assert.match(logSource, /pages < 5/);
console.log("Detail query scope, task commands, cancellation and log cursor checks passed");

function compileComponent(file, mocks) {
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

function elements(value) {
  if (Array.isArray(value)) return value.flatMap(elements);
  if (!value || typeof value !== "object" || !value.props) return [];
  return [value, ...elements(value.props.children)];
}

const { TaskSummary } = compileComponent("../apps/web/app/data-sync/detail/task-summary.tsx", {
  "@yak-ops/yak-ui": { Button: "Button" },
  "lucide-react": { ArrowRight: "ArrowRight", Copy: "Copy" },
  "@/app/datasource/icons/DatabaseIcons": { default: "DatabaseIcons" },
  "../solution/presentation": { syncSummary },
});
let task = {
  id: "t1",
  name: "Replication snapshot",
  productType: "DATABASE_REPLICATION",
  steps: ["FULL"],
  syncType: "OFFLINE",
  status: "PUBLISHED",
  definitionVersion: 4,
  tableMappings: [{ source: { table: "orders" }, target: { table: "orders_copy" } }],
};
const taskSummary = elements(TaskSummary({ task, updatedBy: "operator", dataSources: {} }));
assert.equal(
  taskSummary.find((element) => element.props.label === "同步方案").props.children,
  syncSummary("DATABASE_REPLICATION", ["FULL"]),
);
assert.equal(
  taskSummary.find((element) => element.props.label === "同步表数").props.children[0],
  1,
);

let scheduleIds;
const { DataSyncTaskDetailPage } = compileComponent("../apps/web/app/data-sync/detail/index.tsx", {
  "@yak-ops/yak-ui": { Button: "Button", PageHeader: "PageHeader", Spinner: "Spinner" },
  react: { useState: (initial) => [initial, () => {}] },
  "react-router-dom": {
    useNavigate: () => () => {},
    useParams: () => ({ id: "t1" }),
  },
  "@/app/schedule/dialog/schedule-dialog": { ScheduleDialog: "ScheduleDialog" },
  "@/app/schedule/use-task-schedules": {
    useTaskSchedules: (ids) => {
      scheduleIds = ids;
      return { records: new Map() };
    },
  },
  "@/service/data-sync/execution": { canManageTaskDefinition },
  "./use-active-task-instance": { useActiveTaskInstance: () => ({ ready: true }) },
  "./execution-sidebar": { ExecutionSidebar: "ExecutionSidebar" },
  "./task-summary": { TaskSummary: "TaskSummary" },
  "./execution-tabs": { ExecutionTabs: "ExecutionTabs" },
  "./use-task-information": {
    useTaskInformation: () => ({ task, dataSources: {}, updatedBy: "operator" }),
    useDetailScope: () => "user-a:ws-a",
  },
  "./use-execution-history": { useExecutionHistory: () => ({ instances: [] }) },
  "./use-execution-actions": { useExecutionActions: () => ({}) },
});
let detailElements = elements(DataSyncTaskDetailPage());
assert.deepEqual([...scheduleIds], ["t1"]);
assert.ok(detailElements.some((element) => element.type === "ScheduleDialog"));
let header = detailElements.find((element) => element.type === "PageHeader");
assert.ok(elements(header.props.extra).some((element) => element.props.children === "运行"));
task = { ...task, syncType: "REALTIME", steps: ["FULL", "INCREMENTAL"], definitionVersion: 5 };
detailElements = elements(DataSyncTaskDetailPage());
assert.deepEqual([...scheduleIds], []);
assert.equal(
  detailElements.some((element) => element.type === "ScheduleDialog"),
  false,
);
header = detailElements.find((element) => element.type === "PageHeader");
assert.ok(elements(header.props.extra).some((element) => element.props.children === "启动"));

const icons = Object.fromEntries(
  [
    "CheckCircle2",
    "CircleHelp",
    "CirclePause",
    "CirclePlay",
    "CircleStop",
    "Clock3",
    "LoaderCircle",
    "XCircle",
  ].map((name) => [name, name]),
);
const { DataSyncExecutionDetailContent, dataSyncInstanceStatusMeta } = compileComponent(
  "../apps/web/app/data-sync/execution/execution-detail.tsx",
  {
    "@yak-ops/yak-ui": { Button: "Button", Table: "Table" },
    "lucide-react": icons,
    "@/app/schedule/schedule-presentation": { formatScheduleTime: (value) => value },
    "@/service/data-sync/execution": { isTaskInstanceActive },
    "./execution-progress-bar": { ExecutionProgressBar: "ExecutionProgressBar" },
    "./execution-stages": { ExecutionStages: "ExecutionStages" },
    "../solution/presentation": { syncSummary },
    "./table-execution-results": { TableExecutionResults: "TableExecutionResults" },
    "./execution-detail.css": {},
  },
);
const snapshotInstance = {
  id: "snapshot-instance",
  taskId: "t1",
  productType: "DATABASE_REPLICATION",
  steps: ["FULL"],
  syncType: "OFFLINE",
  status: "FINISHED",
  taskVersion: 4,
  triggerType: "MANUAL",
};
function progressCard(record) {
  const detail = DataSyncExecutionDetailContent({ record });
  const card = elements(detail).find(
    (element) => typeof element.type === "function" && element.props.record === record,
  );
  return { detail, card: elements(card.type(card.props)) };
}
const finishedSnapshot = progressCard(snapshotInstance);
assert.equal(
  finishedSnapshot.card.find((element) => element.type === "h3").props.children,
  syncSummary("DATABASE_REPLICATION", ["FULL"]),
);
assert.equal(
  finishedSnapshot.card.find((element) => element.type === "ExecutionProgressBar").props.realtime,
  false,
);
assert.equal(dataSyncInstanceStatusMeta(snapshotInstance.status, false).label, "成功");
assert.ok(
  elements(finishedSnapshot.detail).some((element) => element.type === "TableExecutionResults"),
);
assert.ok(finishedSnapshot.card.some((element) => element.props.children?.includes?.(4)));
const reportedSnapshot = progressCard({
  ...snapshotInstance,
  executionReport: { version: 3, stages: [{ step: "FULL", status: "FINISHED" }] },
});
assert.ok(reportedSnapshot.card.some((element) => element.type === "ExecutionStages"));
assert.equal(
  reportedSnapshot.card.some((element) => element.type === "ExecutionProgressBar"),
  false,
);
console.log(
  "Replication product detail, bounded scheduling and historical execution presentation passed",
);

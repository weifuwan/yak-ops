import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const root = join(dirname(fileURLToPath(import.meta.url)), "../apps/web/app/data-sync/list");
const queries = readFileSync(join(root, "use-list-queries.ts"), "utf8");
const filters = readFileSync(join(root, "task-filters.tsx"), "utf8");
const editor = readFileSync(join(root, "../editor/page.tsx"), "utf8");
const detailActiveInstance = readFileSync(
  join(root, "../detail/use-active-task-instance.ts"),
  "utf8",
);
const executionActions = readFileSync(
  join(root, "../detail/use-execution-actions.ts"),
  "utf8",
);
const detailPage = readFileSync(join(root, "../detail/index.tsx"), "utf8");
const executionTabs = readFileSync(join(root, "../detail/execution-tabs.tsx"), "utf8");
const executionDetail = readFileSync(join(root, "../execution/execution-detail.tsx"), "utf8");
const taskSummary = readFileSync(join(root, "../detail/task-summary.tsx"), "utf8");
const executionLifecycle = readFileSync(
  join(root, "../../../service/data-sync/execution.ts"),
  "utf8",
);
const page = readFileSync(join(root, "page.tsx"), "utf8");
const lifecycle = readFileSync(join(root, "task-lifecycle.tsx"), "utf8");
const taskTable = readFileSync(join(root, "task-table.tsx"), "utf8");
const actions = readFileSync(join(root, "use-task-actions.ts"), "utf8");
const app = readFileSync(join(root, "../../App.tsx"), "utf8");
const workflow = readFileSync(
  join(root, "../../../service/data-sync/workflow.ts"),
  "utf8",
);

test("task list fetches task and workflow snapshots through one batch request", () => {
  assert.match(
    queries,
    /queryFn: \(\{ signal \}\) => listDataSyncTaskExecutionStates\(ids, signal\)/,
  );
  assert.match(queries, /enabled: Boolean\(scope\) && safeToAct && ids\.length > 0/);
  assert.match(queries, /const latestByTask = useMemo/);
  assert.match(queries, /const workflowsByTask = useMemo/);
  assert.match(queries, /item\.taskInstance && isTaskInstanceActive/);
  assert.doesNotMatch(queries, /useQueries|getLatestDataSyncWorkflow|refetchInterval/);
});

test("task list never polls or refreshes on focus or reconnect and exposes manual refresh", () => {
  assert.doesNotMatch(queries, /refetchInterval/);
  assert.match(queries, /refetchOnWindowFocus: false/);
  assert.match(queries, /refetchOnReconnect: false/);
  assert.match(filters, /onRefresh/);
  assert.match(filters, /刷新状态/);
  assert.match(page, /onRefresh=\{instances\.refresh\}/);
});

test("batch state failures and refetches disable runtime operations until a fresh snapshot is ready", () => {
  assert.match(queries, /const stateReadyByTask = new Map/);
  assert.match(queries, /safeToAct && query\.isSuccess && !query\.isFetching && stateByTask\.has\(record\.id\)/);
  assert.match(page, /!isStateReadyForTask\(value\.task\.id\)/);
  assert.match(taskTable, /disabled: stateReadyByTask\.get\(record\.id\) !== true \|\|/);
  assert.match(page, /执行状态获取失败，当前页任务操作暂不可用/);
});

test("published tasks require a successful state snapshot before unpublishing", () => {
  assert.match(lifecycle, /const canUnpublish = instanceStateReady && !workflowActive && !activeInstance/);
  assert.match(lifecycle, /disabled: published \? !canUnpublish : !canManage/);
});

test("missing workflow instance is a successful null result instead of undefined", () => {
  assert.match(
    workflow,
    /Promise<DataSyncWorkflowInstance \| null>/,
  );
  assert.match(workflow, /return page\.totalList\[0\] \?\? null;/);
  assert.doesNotMatch(
    workflow,
    /Promise<DataSyncWorkflowInstance \| undefined>/,
  );
});


test("data sync actions refresh only the resource family they change without aborting in-flight reads", () => {
  assert.match(actions, /command\.kind === "run" \|\| command\.kind === "stop"/);
  assert.match(actions, /queryKey: listQueryKeys\.instances\(scope\), type: "active"/);
  assert.match(actions, /queryKey: listQueryKeys\.tasks\(scope\), type: "active"/);
  assert.match(actions, /cancelRefetch: false/);
  assert.doesNotMatch(actions, /invalidateQueries/);
});

test("global queries do not refetch just because the browser regains focus", () => {
  assert.match(app, /refetchOnWindowFocus: false/);
});


test("editor derives its execution lock from the latest instance instead of an active-only query", () => {
  assert.match(
    editor,
    /listLatestDataSyncTaskInstances\(\[id!\], false, signal\)/,
  );
  assert.match(editor, /isTaskInstanceActive\(instance\.status\)/);
  assert.match(editor, /const activeInstance = activeQuery\.data\?\.find/);
  assert.doesNotMatch(
    editor,
    /listLatestDataSyncTaskInstances\(\[id!\], true/,
  );
});


test("detail and run actions evaluate active state after fetching the latest instance", () => {
  assert.match(
    detailActiveInstance,
    /listLatestDataSyncTaskInstances\(\[taskId!\], false, signal\)/,
  );
  assert.match(detailActiveInstance, /isTaskInstanceActive\(instance\.status\)/);
  assert.match(
    executionActions,
    /listLatestDataSyncTaskInstances\(\[taskId\], false\)/,
  );
  assert.match(executionActions, /latestInstances\.some\(\(instance\) => isTaskInstanceActive\(instance\.status\)\)/);
  assert.doesNotMatch(detailActiveInstance, /listLatestDataSyncTaskInstances\(\[taskId!\], true/);
  assert.doesNotMatch(executionActions, /listLatestDataSyncTaskInstances\(\[taskId\], true/);
});


test("data-sync detail exposes one task-level stop action and never cancels selected history", () => {
  assert.match(
    executionLifecycle,
    /export const getCancelableDataSyncWorkflowInstanceId/,
  );
  assert.match(
    executionLifecycle,
    /if \(workflow\) return canStopWorkflow\(workflow\.state\) \? workflow\.id : undefined;/,
  );
  assert.match(
    executionLifecycle,
    /canCancelTaskInstance\(activeInstance\.status\)/,
  );
  assert.match(
    detailPage,
    /getCancelableDataSyncWorkflowInstanceId\(workflow, activeInstance\)/,
  );
  assert.match(detailPage, /cancelWorkflowInstanceId \? \(/);
  assert.match(
    detailPage,
    /dataSyncInstanceStatusMeta\(activeInstance\.status, realtime\)\.label/,
  );
  assert.doesNotMatch(executionTabs, /canCancelTaskInstance|onCancel|canceling|停止/);
  assert.doesNotMatch(detailPage, /canceling=\{canceling\}|onCancel=/);
  assert.match(
    executionActions,
    /getCancelableDataSyncWorkflowInstanceId\(workflow, activeInstance\)/,
  );
  assert.doesNotMatch(executionActions, /selectedInstance/);
});


test("execution progress card removes duplicated task summary and preserves its height", () => {
  const start = executionDetail.indexOf("function ExecutionProgressCard");
  const end = executionDetail.indexOf("export function DataSyncExecutionDetailSkeleton", start);
  const progressCard = executionDetail.slice(start, end);
  assert.ok(start >= 0 && end > start);
  assert.doesNotMatch(progressCard, /scopeLabel|modeLabel|taskVersion|dataSyncTriggerText/);
  assert.match(progressCard, /min-h-28/);
  assert.match(progressCard, /place-items-center/);
});

test("switching instances uses a layout-matched skeleton in the execution status tab", () => {
  const statusPanel = executionTabs.match(
    /<TabsPanel value="status"[\s\S]*?<\/TabsPanel>/,
  )?.[0] ?? "";
  assert.match(executionDetail, /export function DataSyncExecutionDetailSkeleton/);
  assert.match(executionDetail, /aria-label="加载执行详情"/);
  assert.ok(executionDetail.includes("min-w-[1820px]"));
  assert.ok(statusPanel.includes("selectedLoading ? ("));
  assert.ok(statusPanel.includes("<DataSyncExecutionDetailSkeleton controls={controls} />"));
  assert.doesNotMatch(statusPanel, /Spinner/);
});


test("task detail sync plan removes datasource prefix and highlights scope without a dot separator", () => {
  assert.ok(!taskSummary.includes("MySQL ·"));
  assert.ok(taskSummary.includes("bg-[#344054] px-2 py-0.5 text-xs font-medium text-white"));
  assert.ok(taskSummary.includes("{scopeLabel}"));
  assert.ok(taskSummary.includes("<span>{modeLabel}</span>"));
  assert.ok(!taskSummary.includes("{scopeLabel} ·"));
});


test("task list avoids repeating an identical published version", () => {
  assert.match(taskTable, /function taskVersionText\(/);
  assert.match(
    taskTable,
    /if \(!publishedVersion\) return `V\$\{definitionVersion\} · 未发布`;/,
  );
  assert.match(
    taskTable,
    /if \(publishedVersion === definitionVersion\) return `V\$\{definitionVersion\}`;/,
  );
  assert.match(taskTable, /最新 V\$\{definitionVersion\} · 已发布 V\$\{publishedVersion\}/);
  assert.match(taskTable, /\{taskVersionText\(record\)\}/);
});

test("online status uses the requested green and a 10px status dot", () => {
  assert.match(lifecycle, /dotClassName: "bg-\[#26A24A\]"/);
  assert.match(lifecycle, /h-2\.5 w-2\.5 shrink-0 rounded-full/);
});

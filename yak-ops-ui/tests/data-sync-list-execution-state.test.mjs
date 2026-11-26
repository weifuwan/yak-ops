import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const root = join(dirname(fileURLToPath(import.meta.url)), "../apps/web/app/data-sync/list");
const queries = readFileSync(join(root, "use-list-queries.ts"), "utf8");
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
const executionLifecycle = readFileSync(
  join(root, "../../../service/data-sync/execution.ts"),
  "utf8",
);
const page = readFileSync(join(root, "page.tsx"), "utf8");
const lifecycle = readFileSync(join(root, "task-lifecycle.tsx"), "utf8");
const actions = readFileSync(join(root, "use-task-actions.ts"), "utf8");
const app = readFileSync(join(root, "../../App.tsx"), "utf8");
const workflow = readFileSync(
  join(root, "../../../service/data-sync/workflow.ts"),
  "utf8",
);

test("task list derives active instances from the single latest-instance request", () => {
  assert.match(
    queries,
    /queryFn: \(\{ signal \}\) => listLatestDataSyncTaskInstances\(ids, false, signal\)/,
  );
  assert.doesNotMatch(queries, /listLatestDataSyncTaskInstances\(ids, true/);
  assert.match(queries, /filter\(\(item\) => isTaskInstanceActive\(item\.status\)\)/);
});

test("workflow state failures are isolated to the affected task", () => {
  assert.match(queries, /const stateReadyByTask = new Map/);
  assert.match(queries, /workflowQueries\[index\]\?\.isSuccess === true/);
  assert.match(queries, /const stateErrorByTask = new Set/);
  assert.doesNotMatch(page, /instances\.ready/);
});

test("published tasks can be unpublished when state reads fail and backend validates running state", () => {
  assert.match(lifecycle, /const canUnpublish = !workflowActive && !activeInstance/);
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
  assert.ok(executionDetail.includes("min-w-[1180px]"));
  assert.ok(statusPanel.includes("selectedLoading ? ("));
  assert.ok(statusPanel.includes("<DataSyncExecutionDetailSkeleton controls={controls} />"));
  assert.doesNotMatch(statusPanel, /Spinner/);
});

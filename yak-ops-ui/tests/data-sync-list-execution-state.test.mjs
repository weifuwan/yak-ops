import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const root = join(dirname(fileURLToPath(import.meta.url)), "../apps/web/app/data-sync/list");
const queries = readFileSync(join(root, "use-list-queries.ts"), "utf8");
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

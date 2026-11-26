import test from "node:test";
import { readFileSync } from "node:fs";
import assert from "node:assert/strict";

const readSource = (path) =>
  readFileSync(new URL(path, import.meta.url), "utf8");

const workbench = readSource("../apps/web/app/data-development/workbench-context.tsx");
const resourcePanel = readSource("../apps/web/app/data-development/resource-panel.tsx");
const workflowCanvas = readSource("../apps/web/app/data-development/workflow-editor/canvas/workflow-canvas.tsx");
const taskEditor = readSource("../apps/web/app/data-development/task-editor/task-editor.tsx");

test("opening a task also keeps its parent workflow tab mounted", () => {
  assert.match(workbench, /workflow\?: WorkflowDefinitionRecord/);
  assert.match(
    workbench,
    /if \(!workflowTab && workflow\)[\s\S]*?toWorkflowTab\(workflow\)/,
  );
  assert.match(workbench, /setActiveTabId\(taskTabId\)/);
});

test("workflow store registration publishes and clears readiness safely", () => {
  assert.match(workbench, /const \[workflowStoreCodes, setWorkflowStoreCodes\]/);
  assert.match(workbench, /next\.add\(workflowCode\)/);
  assert.match(workbench, /workflowStoresRef\.current\.get\(workflowCode\) !== store/);
  assert.match(workbench, /next\.delete\(workflowCode\)/);
});

test("resource tree and canvas pass the parent workflow when opening a task", () => {
  const resourceCalls = resourcePanel.match(
    /openTask\(\s*item\.workflow\.code,[\s\S]*?item\.workflow,\s*\)/g,
  ) ?? [];
  assert.equal(resourceCalls.length, 2);
  assert.match(
    workflowCanvas,
    /openTask\(\s*nodeWorkflowCode,\s*latestTask,\s*node\.id,\s*workflowReleaseState,\s*dag\.workflowDefinition,\s*\)/,
  );
});

test("task editor waits for the parent workflow store before mounting editors", () => {
  assert.match(taskEditor, /if \(!workflowStoreCodes\.has\(props\.workflowCode\)\)/);
  assert.match(taskEditor, /正在初始化工作流画布/);
  assert.match(taskEditor, /return <TaskEditorContent \{\.\.\.props\} \/>/);
});

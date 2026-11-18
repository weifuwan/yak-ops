import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

import {
  buildShellTaskParams,
  parseShellTaskForm,
} from "../apps/web/app/data-development/task-editor/shell/adapter.ts";

const editorSource = readFileSync(
  new URL("../apps/web/app/data-development/task-editor/shell/index.tsx", import.meta.url),
  "utf8",
);
const scriptEditorSource = readFileSync(
  new URL("../apps/web/app/data-development/task-editor/shell/components/shell-script-editor.tsx", import.meta.url),
  "utf8",
);
const scriptPanelSource = readFileSync(
  new URL("../apps/web/app/data-development/task-editor/shell/components/shell-script-panel.tsx", import.meta.url),
  "utf8",
);
const configPanelSource = readFileSync(
  new URL("../apps/web/app/data-development/task-editor/shell/components/shell-config-panel.tsx", import.meta.url),
  "utf8",
);
const parametersPanelSource = readFileSync(
  new URL("../apps/web/app/data-development/task-editor/shell/components/shell-parameters-panel.tsx", import.meta.url),
  "utf8",
);
const resourcesPanelSource = readFileSync(
  new URL("../apps/web/app/data-development/task-editor/shell/components/shell-resources-panel.tsx", import.meta.url),
  "utf8",
);
const hookSource = readFileSync(
  new URL("../apps/web/app/data-development/task-editor/shell/use-shell-task-editor.ts", import.meta.url),
  "utf8",
);
const routerSource = readFileSync(
  new URL("../apps/web/app/data-development/task-editor/task-editor.tsx", import.meta.url),
  "utf8",
);
const nodeEditorFrameSource = readFileSync(
  new URL("../apps/web/app/data-development/task-editor/node-editor-frame.tsx", import.meta.url),
  "utf8",
);
const javaEditorSource = readFileSync(
  new URL("../apps/web/app/data-development/task-editor/java-task-editor.tsx", import.meta.url),
  "utf8",
);
const shellWorkspaceSource = readFileSync(
  new URL("../apps/web/app/data-development/task-editor/shell/components/shell-editor-workspace.tsx", import.meta.url),
  "utf8",
);
const panelResizerSource = readFileSync(
  new URL("../apps/web/app/data-development/layout/panel-resizer.tsx", import.meta.url),
  "utf8",
);
const panelHeightResizerSource = readFileSync(
  new URL("../apps/web/app/data-development/layout/panel-height-resizer.tsx", import.meta.url),
  "utf8",
);

test("Java and Shell share the right-aligned node configuration sidebar", () => {
  assert.match(editorSource, /<NodeEditorFrame/);
  assert.match(javaEditorSource, /<NodeEditorFrame/);

  const workspaceStart = nodeEditorFrameSource.indexOf(
    'className="flex min-h-0 flex-1 overflow-hidden"',
  );
  assert.notEqual(workspaceStart, -1);
  const workspaceLayout = nodeEditorFrameSource.slice(workspaceStart);
  const editorIndex = workspaceLayout.indexOf("<main");
  const asideIndex = workspaceLayout.indexOf("<aside");
  const resizerIndex = workspaceLayout.indexOf("<PanelResizer");
  const panelIndex = workspaceLayout.indexOf("<section");
  const navIndex = workspaceLayout.indexOf("<nav");

  assert.ok(editorIndex >= 0 && editorIndex < asideIndex);
  assert.ok(asideIndex < resizerIndex && resizerIndex < panelIndex);
  assert.ok(panelIndex < navIndex);
  assert.match(workspaceLayout, /resizeDirection="left"/);
  assert.match(workspaceLayout, /border-l/);
  assert.match(workspaceLayout, /right-0 z-\[1\] w-0\.5/);
});

test("Shell form reads the DolphinScheduler rawScript, resourceList and localParams contract", () => {
  const form = parseShellTaskForm(JSON.stringify({
    rawScript: "echo " + "$" + "{bizDate}",
    resourceList: [{ resourceName: "scripts/clean.sh" }],
    localParams: [{
      prop: "bizDate",
      direct: "IN",
      type: "VARCHAR",
      value: "2026-11-17",
    }],
  }));

  assert.equal(form.rawScript, "echo " + "$" + "{bizDate}");
  assert.deepEqual(form.resourceList, ["scripts/clean.sh"]);
  assert.deepEqual(form.localParams, [{
    prop: "bizDate",
    direct: "IN",
    type: "VARCHAR",
    value: "2026-11-17",
    sensitive: false,
  }]);
});

test("Shell form serializes only the existing backend fields and preserves other task params", () => {
  const original = {
    dependence: { relation: "AND" },
    conditionResult: { successNode: [""], failedNode: [""] },
    customExistingField: "preserve-me",
  };
  const form = parseShellTaskForm({
    rawScript: "echo " + "$" + "{bizDate}",
    resourceList: ["scripts/clean.sh"],
    localParams: [{
      prop: "bizDate",
      direct: "IN",
      type: "DATE",
      value: "2026-11-17",
      sensitive: false,
    }, {
      prop: " ",
      direct: "OUT",
      type: "VARCHAR",
      value: "",
      sensitive: false,
    }],
  });

  const params = buildShellTaskParams(form, original);

  assert.equal(params.rawScript, form.rawScript);
  assert.deepEqual(params.resourceList, [{ resourceName: "scripts/clean.sh" }]);
  assert.equal(params.localParams.length, 1);
  assert.equal(params.localParams[0].prop, "bizDate");
  assert.deepEqual(params.dependence, original.dependence);
  assert.deepEqual(params.conditionResult, original.conditionResult);
  assert.equal(params.customExistingField, "preserve-me");
  assert.equal("programArgs" in params, false);
  assert.equal("environmentVariables" in params, false);
  assert.equal("workingDirectory" in params, false);
});

test("Shell editor uses Monaco 0.57 public ESM entry points and registers the Shell language", () => {
  assert.match(scriptEditorSource, /from "monaco-editor\/editor"/);
  assert.match(scriptEditorSource, /monaco-editor\/features\/register\.all/);
  assert.match(scriptEditorSource, /monaco-editor\/languages\/definitions\/shell\/register/);
  assert.match(scriptEditorSource, /monaco-editor\/editor\/editor\.worker\?worker/);
  assert.match(scriptEditorSource, /getWorker: \(\) => new EditorWorker\(\)/);
  assert.match(scriptEditorSource, /editor\.create/);
  assert.ok(scriptEditorSource.includes("automaticLayout: true"));
  assert.doesNotMatch(scriptEditorSource, /monaco-editor\/editor\/(?:browser\/controller|contrib)\//);
  assert.doesNotMatch(scriptEditorSource, /monaco-editor\/basic-languages\//);
  assert.doesNotMatch(scriptEditorSource, /monaco-editor\/esm\/vs\//);
  assert.match(scriptEditorSource, /language: "shell"/);
});

test("Node configuration sidebar keeps pointer capture and keyboard resizing", () => {
  assert.match(panelResizerSource, /setPointerCapture/);
  assert.ok(panelResizerSource.includes("onLostPointerCapture={handlePointerEnd}"));
  assert.match(panelResizerSource, /pointer-events-auto/);
  assert.match(panelResizerSource, /ArrowLeft/);
  assert.match(panelResizerSource, /ArrowRight/);
});

test("Shell editor respects the available width when the right sidebar opens", () => {
  assert.ok(routerSource.includes("min-h-0 min-w-0 flex-1 flex-col"));
  assert.ok(nodeEditorFrameSource.includes("min-h-0 min-w-0 flex-1 flex-col"));
  assert.ok(nodeEditorFrameSource.includes("max-w-[calc(100vw-100px)] shrink-0"));
  assert.ok(shellWorkspaceSource.includes("min-h-0 min-w-0 flex-1 flex-col"));
  assert.ok(scriptPanelSource.includes("min-h-[250px] min-w-0 flex-1 flex-col"));
  assert.ok(scriptPanelSource.includes("min-h-0 min-w-0 flex-1"));
  assert.ok(scriptEditorSource.includes("min-h-[240px] min-w-0 w-full"));
});

test("Shell script and parameter panels support vertical resizing", () => {
  assert.match(shellWorkspaceSource, /new ResizeObserver/);
  assert.match(shellWorkspaceSource, /PanelHeightResizer/);
  assert.match(shellWorkspaceSource, /maxConfigPanelHeight/);
  assert.match(shellWorkspaceSource, /height={visibleConfigPanelHeight}/);
  assert.ok(configPanelSource.includes("style={{ height, flexBasis: height }}"));
  assert.match(panelHeightResizerSource, /aria-orientation="horizontal"/);
  assert.match(panelHeightResizerSource, /setPointerCapture/);
  assert.ok(panelHeightResizerSource.includes("onLostPointerCapture={handlePointerEnd}"));
  assert.match(panelHeightResizerSource, /ArrowUp/);
  assert.match(panelHeightResizerSource, /ArrowDown/);
});

test("Shell editor follows the directory-based task editor structure", () => {
  assert.match(routerSource, /import\("\.\/shell"\)/);
  assert.match(editorSource, /export function ShellTaskEditor/);
  assert.match(editorSource, /useShellTaskEditor/);
  assert.match(editorSource, /ShellEditorWorkspace/);
  assert.match(hookSource, /handleSave/);
  assert.match(hookSource, /handleRun/);
  assert.match(hookSource, /handleRefresh/);
});

test("Shell editor keeps named local-parameter and setValue syntax hints", () => {
  assert.match(parametersPanelSource, /paramName/);
  assert.match(parametersPanelSource, /setValue/);
  assert.match(configPanelSource, /ShellParametersPanel/);
  assert.match(configPanelSource, /ShellResourcesPanel/);
});

test("Shell config panel uses its declared activeTab props for navigation", () => {
  assert.equal(configPanelSource.includes("activeConfigTab"), false);
  assert.equal(configPanelSource.includes("setActiveConfigTab"), false);
  assert.equal(configPanelSource.includes('aria-pressed={activeTab === "parameters"}'), true);
  assert.equal(configPanelSource.includes('onClick={() => onActiveTabChange("parameters")}'), true);
  assert.equal(configPanelSource.includes('aria-pressed={activeTab === "resources"}'), true);
  assert.equal(configPanelSource.includes('onClick={() => onActiveTabChange("resources")}'), true);
});

test("Shell script panel uses its declared rawScript and onChange props", () => {
  assert.equal(scriptPanelSource.includes("form.rawScript"), false);
  assert.equal(scriptPanelSource.includes("updateField("), false);
  assert.equal(scriptPanelSource.includes("value={rawScript}"), true);
  assert.equal(scriptPanelSource.includes("onChange={onChange}"), true);
  assert.equal(scriptPanelSource.includes('onChange("")'), true);
});

test("Shell parameters panel uses declared props and shared parameter constants", () => {
  assert.equal(parametersPanelSource.includes("setForm("), false);
  assert.equal(parametersPanelSource.includes("shellParamTypeOptions"), false);
  assert.equal(parametersPanelSource.includes("updateField(\"localParams\""), true);
  assert.equal(parametersPanelSource.includes("createShellLocalParam()"), true);
  assert.equal(parametersPanelSource.includes("SHELL_PARAM_TYPE_OPTIONS"), true);
});

test("Shell resources panel uses declared loading, error and retry props", () => {
  assert.equal(resourcesPanelSource.includes("resourceQuery."), false);
  assert.equal(resourcesPanelSource.includes("resourceError"), true);
  assert.equal(resourcesPanelSource.includes("resourceLoading"), true);
  assert.equal(resourcesPanelSource.includes("onRetryResources()"), true);
});

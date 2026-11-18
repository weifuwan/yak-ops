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
  assert.doesNotMatch(scriptEditorSource, /monaco-editor\/editor\/(?:browser\/controller|contrib)\//);
  assert.doesNotMatch(scriptEditorSource, /monaco-editor\/basic-languages\//);
  assert.doesNotMatch(scriptEditorSource, /monaco-editor\/esm\/vs\//);
  assert.match(scriptEditorSource, /language: "shell"/);
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

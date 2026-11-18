import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

import { buildShellTaskParams, parseShellTaskForm } from "../apps/web/app/data-development/task-editor/shell/adapter.ts";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const shellEditor = read("../apps/web/app/data-development/task-editor/shell/index.tsx");
const shellHook = read("../apps/web/app/data-development/task-editor/shell/use-shell-task-editor.ts");
const shellWorkspace = read("../apps/web/app/data-development/task-editor/shell/components/shell-editor-workspace.tsx");
const scriptEditor = read("../apps/web/app/data-development/task-editor/shell/components/shell-script-editor.tsx");
const scriptPanel = read("../apps/web/app/data-development/task-editor/shell/components/shell-script-panel.tsx");
const javaEditor = read("../apps/web/app/data-development/task-editor/java-task-editor.tsx");
const frame = read("../apps/web/app/data-development/task-editor/node-editor-frame.tsx");
const commonPanel = read("../apps/web/app/data-development/task-editor/common/node-resource-params/panel.tsx");
const logicEditor = read("../apps/web/app/data-development/task-editor/logic/shared/logic-task-editor.tsx");
const genericEditor = read("../apps/web/app/data-development/task-editor/generic-task-editor.tsx");
const switchEditor = read("../apps/web/app/data-development/task-editor/logic/switch/index.tsx");
const panelResizer = read("../apps/web/app/data-development/layout/panel-resizer.tsx");

test("Shell serialization keeps DolphinScheduler resourceList and localParams contract", () => {
  const form = parseShellTaskForm({
    rawScript: "echo " + "$" + "{bizDate}",
    resourceList: [{ resourceName: "scripts/clean.sh" }],
    localParams: [{ prop: "bizDate", direct: "IN", type: "VARCHAR", value: "2026-11-17" }],
  });
  assert.equal(form.rawScript, "echo " + "$" + "{bizDate}");
  assert.deepEqual(form.resourceList, ["scripts/clean.sh"]);
  const original = { dependence: { relation: "AND" }, customExistingField: "keep-me" };
  const params = buildShellTaskParams(form, original);
  assert.deepEqual(params.resourceList, [{ resourceName: "scripts/clean.sh" }]);
  assert.equal(params.localParams[0].prop, "bizDate");
  assert.deepEqual(params.dependence, original.dependence);
  assert.equal(params.customExistingField, "keep-me");
});

test("Java, Shell, logic and generic editors use the shared resource/parameter sidebar", () => {
  assert.match(frame, /key: "resourceParams", label: "资源与参数"/);
  assert.match(frame, /visibleActivePanel === "resourceParams"/);
  assert.match(javaEditor, /NodeResourceParamsPanel/);
  assert.match(javaEditor, /resourceParams=\{/);
  assert.doesNotMatch(javaEditor, /id="java-resources"|id="java-local-params"/);
  assert.match(shellEditor, /NodeResourceParamsPanel/);
  assert.match(shellEditor, /resourceParams=\{/);
  assert.match(logicEditor, /NodeResourceParamsPanel/);
  assert.match(logicEditor, /resourceParams=\{resourceParams\}/);
  assert.match(genericEditor, /NodeResourceParamsPanel/);
  assert.match(genericEditor, /resourceParams=\{resourceParams\}/);
  assert.match(commonPanel, /title="依赖资源"/);
  assert.match(commonPanel, /title="本地参数"/);
});

test("Java-only Module Path option stays attached to the shared resource section", () => {
  assert.match(javaEditor, /使用 Java Module Path/);
  assert.match(javaEditor, /isModulePath: form\.isModulePath/);
  assert.match(javaEditor, /resourceExtra=/);
});

test("Shell workspace is dedicated to the Monaco script editor", () => {
  assert.match(shellWorkspace, /ShellScriptPanel/);
  assert.doesNotMatch(shellWorkspace, /ShellConfigPanel|PanelHeightResizer/);
  assert.doesNotMatch(shellHook, /activeConfigTab|updateLocalParam/);
  assert.match(shellEditor, /localParamsDescription/);
});

test("Switch variable suggestions use the current draft task params", () => {
  assert.match(switchEditor, /draftTaskParams/);
  assert.match(switchEditor, /onParamsChange=\{handleParamsChange\}/);
});

test("Shell editor uses Monaco public ESM entry points", () => {
  assert.match(scriptEditor, /from "monaco-editor\/editor"/);
  assert.match(scriptEditor, /monaco-editor\/features\/register\.all/);
  assert.match(scriptEditor, /monaco-editor\/languages\/definitions\/shell\/register/);
  assert.match(scriptEditor, /monaco-editor\/editor\/editor\.worker\?worker/);
  assert.match(scriptEditor, /getWorker: \(\) => new EditorWorker\(\)/);
  assert.match(scriptEditor, /editor\.create/);
  assert.match(scriptEditor, /language: "shell"/);
  assert.ok(scriptEditor.includes("automaticLayout: true"));
});

test("Node sidebar resizer keeps pointer capture and keyboard resizing", () => {
  assert.match(panelResizer, /setPointerCapture/);
  assert.match(panelResizer, /onLostPointerCapture={handlePointerEnd}/);
  assert.match(panelResizer, /ArrowLeft/);
  assert.match(panelResizer, /ArrowRight/);
});

test("Shell editor remains width-constrained inside the node workspace", () => {
  assert.ok(scriptPanel.includes("min-h-[250px] min-w-0 flex-1 flex-col"));
  assert.ok(scriptPanel.includes("min-h-0 min-w-0 flex-1"));
  assert.ok(scriptEditor.includes("min-h-[240px] min-w-0 w-full"));
});

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

import {
  buildShellTaskParams,
  parseShellTaskForm,
} from "../apps/web/app/data-development/task-editor/shell/adapter.ts";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const shellEditor = read("../apps/web/app/data-development/task-editor/shell/index.tsx");
const shellHook = read("../apps/web/app/data-development/task-editor/shell/use-shell-task-editor.ts");
const scriptEditor = read("../apps/web/app/data-development/task-editor/script/index.tsx");
const scriptHook = read("../apps/web/app/data-development/task-editor/script/use-script-task-editor.ts");
const scriptCodeEditor = read("../apps/web/app/data-development/task-editor/script/script-code-editor.tsx");
const scriptPanel = read("../apps/web/app/data-development/task-editor/script/script-editor-panel.tsx");
const taskEditor = read("../apps/web/app/data-development/task-editor/task-editor.tsx");
const frame = read("../apps/web/app/data-development/task-editor/node-editor-frame.tsx");
const commonPanel = read("../apps/web/app/data-development/task-editor/common/node-resource-params/panel.tsx");
const commonSettings = read("../apps/web/app/data-development/task-editor/task-common-settings-section.tsx");
const javaSettings = read("../apps/web/app/data-development/task-editor/java-run-config-panel.tsx");
const executionResourceFields = read("../apps/web/app/data-development/task-editor/execution-resource-fields.tsx");
const environmentService = read("../apps/web/service/environment/index.ts");
const sliderInput = read("../packages/yak-ui/src/slider/SliderInput.tsx");
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

test("Shell uses the shared script editor and shared execution hook", () => {
  assert.match(shellEditor, /ScriptTaskEditor/);
  assert.match(shellEditor, /taskType="SHELL"/);
  assert.match(shellHook, /useScriptTaskEditor/);
  assert.match(scriptEditor, /NodeEditorFrame/);
  assert.match(scriptEditor, /NodeResourceParamsPanel/);
  assert.match(scriptEditor, /TaskCommonSettingsSection/);
  assert.match(scriptHook, /startWorkflowInstance/);
  assert.match(scriptHook, /stopWorkflowInstance/);
  assert.match(scriptHook, /getWorkflowDefinition/);
  assert.match(scriptHook, /buildScriptTaskParams/);
  assert.match(commonSettings, /title="执行资源"/);
  assert.match(commonSettings, /title="执行策略"/);
  assert.match(commonSettings, /title="超时设置"/);
  assert.match(commonSettings, /title="资源限制"/);
});

test("Node execution resources use selectable worker groups and compatible environments", () => {
  for (const settings of [commonSettings, javaSettings]) {
    assert.match(settings, /ExecutionResourceFields/);
    assert.doesNotMatch(settings, /label="Worker Group"|label="Environment Code"/);
    assert.doesNotMatch(settings, /Environment Code 填/);
  }

  assert.match(executionResourceFields, /label="Worker 分组"/);
  assert.match(executionResourceFields, /label="执行环境"/);
  assert.match(executionResourceFields, /listWorkerGroupNames/);
  assert.match(executionResourceFields, /listAllEnvironments/);
  assert.match(executionResourceFields, /environment\.workerGroups/);
  assert.match(executionResourceFields, /isEnvironmentAvailableForWorkerGroup/);
  assert.match(executionResourceFields, /workerGroups\.length\s*>\s*0\s*&&\s*workerGroups\.includes\(workerGroup\)/);
  assert.match(executionResourceFields, /onChange\(nextWorkerGroup, nextEnvironmentCode\)/);
  assert.match(environmentService, /query-environment-list/);
});

test("Resource limits use the shared slider layout without narrowing numeric values", () => {
  for (const settings of [commonSettings, javaSettings]) {
    assert.match(settings, /label="CPU 配额"[\s\S]*?sliderMax={1000}[\s\S]*?unit="%"/);
    assert.match(settings, /label="最大内存"[\s\S]*?sliderMax={65536}[\s\S]*?unit="MB"/);
    assert.match(settings, /max={2147483647}/);
    assert.doesNotMatch(settings, /<Input[^>]*settings\.cpuQuota|<Input[^>]*settings\.memoryMax/);
  }

  assert.match(sliderInput, /sliderMax\?: number/);
  assert.match(sliderInput, /max={sliderUpperBound}/);
  assert.match(sliderInput, /max={max}/);
  assert.match(sliderInput, /onBlur=\{\(\) => setInputValue\(displayValue\)\}/);
});

test("Shared script editor registers both languages with their own indentation defaults", () => {
  assert.match(scriptCodeEditor, /monaco-editor\/editor/);
  assert.match(scriptCodeEditor, /monaco-editor\/features\/register\.all/);
  assert.match(scriptCodeEditor, /monaco-editor\/languages\/definitions\/shell\/register/);
  assert.match(scriptCodeEditor, /monaco-editor\/languages\/definitions\/python\/register/);
  assert.match(scriptCodeEditor, /language === "python" \? 4 : 2/);
  assert.match(scriptCodeEditor, /automaticLayout: true/);
  assert.ok(scriptCodeEditor.includes('className="nokey h-full min-h-[240px]'));
  assert.doesNotMatch(scriptPanel, /Ctrl \+ F 查找/);
  assert.doesNotMatch(scriptPanel, /清空脚本/);
  assert.doesNotMatch(scriptPanel, /FileCode2|Search/);
});

test("Shell and Python are routed to dedicated lazy-loaded editors", () => {
  assert.match(taskEditor, /const ShellTaskEditor = lazy/);
  assert.match(taskEditor, /const PythonTaskEditor = lazy/);
  assert.match(taskEditor, /isShellTask/);
  assert.match(taskEditor, /isPythonTask/);
});

test("Shared node sidebar keeps resource parameters and panel resizing", () => {
  assert.match(frame, /key: "resourceParams", label: "资源与参数"/);
  assert.match(frame, /visibleActivePanel === "resourceParams"/);
  assert.match(commonPanel, /title="依赖资源"/);
  assert.match(commonPanel, /title="本地参数"/);
  assert.match(panelResizer, /setPointerCapture/);
  assert.match(panelResizer, /onLostPointerCapture={handlePointerEnd}/);
  assert.match(panelResizer, /ArrowLeft/);
  assert.match(panelResizer, /ArrowRight/);
});

test("Node properties stay focused on user-facing fields", () => {
  assert.match(frame, /InfoRow label="节点名称"/);
  assert.match(frame, /InfoRow label="节点类型"/);
  assert.doesNotMatch(frame, /InfoRow label="节点编码"/);
  assert.doesNotMatch(frame, /InfoRow label="节点版本"/);
  assert.doesNotMatch(frame, /InfoRow label="所属工作流"/);
  assert.doesNotMatch(frame, /InfoRow label="修改人"/);
  assert.doesNotMatch(frame, /InfoRow label="更新时间"/);
  assert.doesNotMatch(frame, /border-b border-\[#f0f1f3\]/);
  assert.doesNotMatch(frame, /rounded-md border border-\[#e8ebef\]/);
});

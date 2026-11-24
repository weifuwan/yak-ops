import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

import {
  buildPythonTaskParams,
  parsePythonTaskForm,
} from "../apps/web/app/data-development/task-editor/python/adapter.ts";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const pythonEditor = read("../apps/web/app/data-development/task-editor/python/index.tsx");
const pythonTypes = read("../apps/web/app/data-development/task-editor/python/types.ts");
const taskEditor = read("../apps/web/app/data-development/task-editor/task-editor.tsx");
const scriptEditor = read("../apps/web/app/data-development/task-editor/script/index.tsx");
const scriptHook = read("../apps/web/app/data-development/task-editor/script/use-script-task-editor.ts");
const codeEditor = read("../apps/web/app/data-development/task-editor/script/script-code-editor.tsx");
const panel = read("../apps/web/app/data-development/task-editor/script/script-editor-panel.tsx");

test("Python task adapter preserves the backend taskParams contract", () => {
  const form = parsePythonTaskForm({
    rawScript: "print('hello')",
    resourceList: [{ resourceName: "/resources/python/common.py" }],
    localParams: [
      { prop: "bizDate", direct: "IN", type: "VARCHAR", value: "2026-11-24" },
      { prop: "rowCount", direct: "OUT", type: "INTEGER", value: "" },
    ],
  });

  assert.equal(form.rawScript, "print('hello')");
  assert.deepEqual(form.resourceList, ["/resources/python/common.py"]);
  assert.equal(form.localParams[0].direct, "IN");
  assert.equal(form.localParams[1].direct, "OUT");

  const original = { customExistingField: "keep-me" };
  const params = buildPythonTaskParams(form, original);
  assert.equal(params.rawScript, form.rawScript);
  assert.deepEqual(params.resourceList, [{ resourceName: "/resources/python/common.py" }]);
  assert.deepEqual(params.localParams.map((item) => item.prop), ["bizDate", "rowCount"]);
  assert.equal(params.customExistingField, "keep-me");
});

test("Python is registered as a dedicated lazy-loaded task editor", () => {
  assert.match(pythonEditor, /ScriptTaskEditor/);
  assert.match(pythonEditor, /taskType="PYTHON"/);
  assert.match(pythonEditor, /language="python"/);
  assert.match(pythonEditor, /scriptLabel="Python 脚本"/);
  assert.match(taskEditor, /const PythonTaskEditor = lazy/);
  assert.match(taskEditor, /isPythonTask/);
  assert.match(taskEditor, /正在加载 Python 编辑器/);
  assert.doesNotMatch(pythonTypes, /ShellTask/);
});

test("Python and Shell share the same editor, save, run, refresh and sidebar behavior", () => {
  assert.match(scriptEditor, /NodeEditorFrame/);
  assert.match(scriptEditor, /NodeResourceParamsPanel/);
  assert.match(scriptEditor, /TaskCommonSettingsSection/);
  assert.match(scriptHook, /parseScriptTaskForm/);
  assert.match(scriptHook, /buildScriptTaskParams/);
  assert.match(scriptHook, /startWorkflowInstance/);
  assert.match(scriptHook, /stopWorkflowInstance/);
  assert.match(scriptHook, /getWorkflowDefinition/);
  assert.match(scriptHook, /normalizeTaskCommonSettings/);
});

test("Python Monaco editor uses Python grammar and Python indentation", () => {
  assert.match(codeEditor, /monaco-editor\/languages\/definitions\/python\/register/);
  assert.match(codeEditor, /language === "python" \? 4 : 2/);
  assert.match(panel, /language === "python" \? "Python" : "Shell"/);
  assert.match(panel, /UTF-8 · LF/);
  assert.match(panel, /language === "python" \? "Python" : "Shell"/);
});

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

import {
  buildShellTaskParams,
  parseShellTaskForm,
} from "../apps/web/app/data-development/task-editor/shell-task-form.ts";

const editorSource = readFileSync(
  new URL("../apps/web/app/data-development/task-editor/shell-task-editor.tsx", import.meta.url),
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

test("Shell editor uses Monaco and is routed separately from the generic JSON editor", () => {
  assert.match(editorSource, /from "monaco-editor"/);
  assert.match(editorSource, /editor\.create/);
  assert.match(editorSource, /language: "shell"/);
  assert.match(editorSource, /localParams/);
  assert.match(editorSource, /resourceList/);
  assert.match(routerSource, /ShellTaskEditor/);
  assert.match(routerSource, /isShellTask/);
});

test("Shell editor exposes named local-parameter and setValue syntax hints", () => {
  assert.match(editorSource, /paramName/);
  assert.match(editorSource, /setValue/);
});

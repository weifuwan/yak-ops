import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const root = dirname(fileURLToPath(import.meta.url));
const read = (path) => readFileSync(join(root, path), "utf8");
const config = read("../apps/web/app/data-modeling/indicators/composite-indicator/config.ts");
const form = read("../apps/web/app/data-modeling/indicators/composite-indicator/form/composite-indicator-editor-fields.tsx");
const validation = read("../apps/web/app/data-modeling/indicators/composite-indicator/form/validation.ts");
const page = read("../apps/web/app/data-modeling/indicators/composite-indicator/index.tsx");
const treeHook = read("../apps/web/app/data-modeling/indicators/workbench/hooks/use-grouped-indicator-tree.ts");
const treeBuilder = read("../apps/web/app/data-modeling/indicators/workbench/utils/indicator-tree.ts");
const list = read("../apps/web/app/data-modeling/indicators/workbench/components/workspace/indicator-list.tsx");

test("composite indicator uses a dedicated editor with conditional validation", () => {
  assert.match(page, /renderFields=\{\(fieldProps\) => <CompositeIndicatorEditorFields/);
  assert.match(page, /validateBeforeSave=\{validateCompositeIndicator\}/);
  assert.match(page, /validateBeforeSubmit=\{validateCompositeIndicator\}/);
  assert.match(form, /RadioGroup/);
  assert.match(form, /computeMode === "1"/);
  assert.match(form, /COMPOSITE_RATIO_FORMULA/);
  assert.match(form, /输入 @ 可以引用其他派生指标或复合指标/);
});

test("switching calculation mode clears fields that do not belong to the selected mode", () => {
  assert.match(form, /computeMode: "1", computeExpr: ""/);
  assert.match(form, /computeMode: "0", deriveId: "", compareType: ""/);
  assert.match(validation, /computeMode === "0"/);
  assert.match(validation, /computeMode === "1"/);
  assert.match(validation, /请选择派生指标/);
  assert.match(validation, /请选择对比类型/);
});

test("warehouse layer selection controls public and application ownership fields", () => {
  assert.match(config, /key: "dwhId".*required: true.*optionsSource: "dwLayer"/s);
  assert.match(config, /excludeOptionValues: \["SOURCE_LAYER", "NONE_LAYER"\]/);
  assert.match(form, /selectedDwType === "PUBLIC_LAYER"/);
  assert.match(form, /selectedDwType === "APPLICATION_LAYER"/);
  assert.match(form, /domainId: path\?\.\[0\]/);
  assert.match(form, /busProcessId: path\?\.\[path\.length - 1\]/);
  assert.match(form, /subjectId: ""/);
  assert.match(validation, /公共层必须选择数据域及业务过程/);
  assert.match(validation, /应用层必须选择数据集市及主题域/);
});

test("changing the selected warehouse layer clears both ownership branches", () => {
  for (const field of ["busTypeId", "domainId", "busProcessId", "martId", "subjectId"]) {
    assert.match(form, new RegExp(field + ': ""'));
  }
});

test("composite indicator tree groups records using the selected concrete warehouse layer", () => {
  assert.match(page, /groupingMode="WAREHOUSE"/);
  assert.match(treeHook, /dwLayerApi\.all<ModelingRecord>\(projectCode, \{ dwType \}\)/);
  assert.match(treeHook, /layerIds\.has\(String\(record\.dwhId \|\| ""\)\)/);
  assert.match(treeHook, /\{ \.\.\.record, dwType \}/);
  assert.match(treeBuilder, /kind === "composeIndicator" \? "复合指标" : "派生指标"/);
});

test("composite list filters switch with the selected warehouse layer", () => {
  assert.match(config, /dwType: "PUBLIC_LAYER"/);
  assert.match(config, /dwType: "APPLICATION_LAYER"/);
  assert.match(list, /definition\.key !== "derivativeIndicator" && definition\.key !== "composeIndicator"/);
});

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";
import { validateCompositeIndicator } from "../apps/web/app/data-modeling/indicators/composite-indicator/form/validation.ts";

const root = dirname(fileURLToPath(import.meta.url));
const read = (path) => readFileSync(join(root, path), "utf8");
const config = read("../apps/web/app/data-modeling/indicators/composite-indicator/config.ts");
const form = read("../apps/web/app/data-modeling/indicators/composite-indicator/form/composite-indicator-editor-fields.tsx");
const validation = read("../apps/web/app/data-modeling/indicators/composite-indicator/form/validation.ts");
const page = read("../apps/web/app/data-modeling/indicators/composite-indicator/index.tsx");
const treeHook = read("../apps/web/app/data-modeling/indicators/workbench/hooks/use-grouped-indicator-tree.ts");
const treeBuilder = read("../apps/web/app/data-modeling/indicators/workbench/utils/indicator-tree.ts");
const list = read("../apps/web/app/data-modeling/indicators/workbench/components/workspace/indicator-list.tsx");

test("composite indicator uses a dedicated editor with conditional calculation validation", () => {
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

test("composite indicator selects only the stable public/application warehouse classification enum", () => {
  assert.match(config, /key: "dwType".*required: true.*options: COMPOSITE_DW_TYPE_OPTIONS/s);
  assert.match(config, /value: "PUBLIC_LAYER", label: "公共层"/);
  assert.match(config, /value: "APPLICATION_LAYER", label: "应用层"/);
  assert.match(form, /value=\{values\.dwType \|\| ""\}/);
  assert.match(form, /selectedDwType === "PUBLIC_LAYER"/);
  assert.match(form, /selectedDwType === "APPLICATION_LAYER"/);
  assert.doesNotMatch(config, /dwhId|dwLayer|dwModelType|DWS|ADS/);
  assert.doesNotMatch(form, /dwhId|dwLayer|dwModelType|selectedWarehouseLayer/);
});

test("changing warehouse classification clears both ownership branches", () => {
  assert.match(form, /dwType: next/);
  for (const field of ["busTypeId", "domainId", "busProcessId", "martId", "subjectId"]) {
    assert.match(form, new RegExp(field + ': ""'));
  }
});

test("changing business category preserves a still-valid business process and clears only an invalid selection", () => {
  assert.match(form, /onChange\("busTypeId", value\)/);
  assert.match(form, /processStillAvailable/);
  assert.match(form, /processQuery\.options\.some/);
  assert.match(form, /onChangeMany\(\{ domainId: "", busProcessId: "" \}\)/);
  assert.doesNotMatch(form, /onChangeMany\(\{ busTypeId: value, domainId: "", busProcessId: "" \}\)/);
});

test("composite ownership validation follows the selected classification without loading layer definitions", () => {
  const validPublic = {
    dwType: "PUBLIC_LAYER",
    enShortName: "composite_metric",
    cnName: "复合指标",
    director: "admin",
    computeMode: "0",
    computeExpr: "@sales + @orders",
    domainId: "domain-id",
    busProcessId: "process-id",
  };
  assert.equal(validateCompositeIndicator(validPublic), undefined);
  assert.equal(
    validateCompositeIndicator({ ...validPublic, domainId: "", busProcessId: "" }),
    "公共层必须选择数据域及业务过程",
  );

  const validApplication = {
    ...validPublic,
    dwType: "APPLICATION_LAYER",
    domainId: "",
    busProcessId: "",
    martId: "mart-id",
    subjectId: "subject-id",
  };
  assert.equal(validateCompositeIndicator(validApplication), undefined);
  assert.equal(
    validateCompositeIndicator({ ...validApplication, subjectId: "" }),
    "应用层必须选择数据集市及主题域",
  );
  assert.equal(validateCompositeIndicator({ ...validPublic, dwType: "" }), "请选择数仓分类");
  assert.equal(validateCompositeIndicator({ ...validPublic, dwType: "SOURCE_LAYER" }), "请选择数仓分类");
});

test("composite indicator tree scopes records directly by dwType", () => {
  assert.match(page, /groupingMode="WAREHOUSE"/);
  assert.match(treeHook, /const scopedRecords = records\.filter/);
  assert.match(treeHook, /type === dwType/);
  assert.doesNotMatch(treeHook, /dwLayerApi|dwhId|dwModelType/);
  assert.match(treeBuilder, /kind === "composeIndicator" \? "复合指标" : "派生指标"/);
});

test("composite list filters remain split between public and application ownership fields", () => {
  assert.match(config, /dwType: "PUBLIC_LAYER"/);
  assert.match(config, /dwType: "APPLICATION_LAYER"/);
  assert.match(list, /definition\.key !== "derivativeIndicator" && definition\.key !== "composeIndicator"/);
});

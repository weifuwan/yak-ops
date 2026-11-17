import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";
import { validateCompositeIndicator } from "../apps/web/app/data-modeling/indicators/composite-indicator/form/validation.ts";
import { fieldOptions } from "../apps/web/app/data-modeling/indicators/workbench/utils/indicator-form.ts";

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
  assert.match(config, /optionFilterField: "dwModelType"/);
  assert.match(config, /includeOptionValues: COMPOSITE_INDICATOR_DW_MODEL_TYPES/);
  assert.match(form, /optionFilterField: "dwModelType"/);
  assert.match(form, /includeOptionValues: COMPOSITE_INDICATOR_DW_MODEL_TYPES/);
  assert.match(form, /selectedLabel=\{selectedWarehouseLayerLabel\}/);
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

const validPublicValues = {
  dwhId: "public-layer",
  enShortName: "composite_metric",
  cnName: "复合指标",
  director: "admin",
  computeMode: "0",
  computeExpr: "@sales + @orders",
  domainId: "domain-id",
  busProcessId: "process-id",
};
const validationLayers = {
  dwLayer: [
    { id: "public-layer", dwType: "PUBLIC_LAYER", dwModelType: "SUMMARY" },
    { id: "application-layer", dwType: "APPLICATION_LAYER", dwModelType: "APPLICATION" },
    { id: "detail-layer", dwType: "PUBLIC_LAYER", dwModelType: "DETAIL" },
    { id: "dimension-layer", dwType: "PUBLIC_LAYER", dwModelType: "DIMENSION_DIM" },
    { id: "application-invalid-layer", dwType: "APPLICATION_LAYER", dwModelType: "SUMMARY" },
    { id: "source-layer", dwType: "SOURCE_LAYER", dwModelType: "SOURCE" },
    { id: "unassigned-layer", dwType: "NONE_LAYER", dwModelType: null },
  ],
};

test("composite ownership validation follows the selected warehouse layer type", () => {
  assert.equal(validateCompositeIndicator({ ...validPublicValues, busTypeId: "" }, validationLayers), undefined);
  assert.equal(
    validateCompositeIndicator({
      ...validPublicValues,
      domainId: "",
      busProcessId: "",
      martId: "mart-id",
      subjectId: "subject-id",
    }, validationLayers),
    "公共层必须选择数据域及业务过程",
  );

  const validApplicationValues = {
    ...validPublicValues,
    dwhId: "application-layer",
    domainId: "",
    busProcessId: "",
    martId: "mart-id",
    subjectId: "subject-id",
  };
  assert.equal(validateCompositeIndicator(validApplicationValues, validationLayers), undefined);
  assert.equal(
    validateCompositeIndicator({ ...validApplicationValues, subjectId: "" }, validationLayers),
    "应用层必须选择数据集市及主题域",
  );
});

test("composite ownership validation rejects unsupported layer and model combinations", () => {
  for (const dwhId of [
    "detail-layer",
    "dimension-layer",
    "application-invalid-layer",
    "source-layer",
    "unassigned-layer",
  ]) {
    assert.equal(
      validateCompositeIndicator({ ...validPublicValues, dwhId }, validationLayers),
      "复合指标仅支持公共层汇总数据层（DWS）或应用层应用数据层（ADS）",
    );
  }
  assert.equal(
    validateCompositeIndicator(validPublicValues, {}),
    "数仓分层选项尚未加载，请稍后重试",
  );
  assert.equal(
    validateCompositeIndicator({ ...validPublicValues, dwhId: "missing-layer" }, validationLayers),
    "所选数仓分层不存在，请重新选择",
  );
});

test("composite warehouse layer options include only DWS summary and ADS application models", () => {
  const options = fieldOptions({
    key: "dwhId",
    label: "数仓分层",
    optionsSource: "dwLayer",
    optionLabelField: "cnName",
    optionLabelPrefixField: "dwTypeName",
    optionFilterField: "dwModelType",
    includeOptionValues: ["SUMMARY", "APPLICATION"],
  }, {
    dwLayer: [
      { id: "dim", dwType: "PUBLIC_LAYER", dwModelType: "DIMENSION_DIM", dwTypeName: "公共层", cnName: "维度层", enShortName: "DIM" },
      { id: "dwd", dwType: "PUBLIC_LAYER", dwModelType: "DETAIL", dwTypeName: "公共层", cnName: "明细数据层", enShortName: "DWD" },
      { id: "dws", dwType: "PUBLIC_LAYER", dwModelType: "SUMMARY", dwTypeName: "公共层", cnName: "汇总数据层", enShortName: "DWS" },
      { id: "ads", dwType: "APPLICATION_LAYER", dwModelType: "APPLICATION", dwTypeName: "应用层", cnName: "应用数据层", enShortName: "ADS" },
      { id: "ods", dwType: "SOURCE_LAYER", dwModelType: "SOURCE", dwTypeName: "贴源层", cnName: "数据引入层", enShortName: "ODS" },
    ],
  });

  assert.deepEqual(options.map((option) => option.value), ["dws", "ads"]);
  assert.deepEqual(options.map((option) => option.label), [
    "公共层 汇总数据层 (DWS)",
    "应用层 应用数据层 (ADS)",
  ]);
});

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";
import ts from "typescript";

const root = dirname(fileURLToPath(import.meta.url));
const read = (path) => readFileSync(join(root, path), "utf8");
const config = read("../apps/web/app/data-modeling/indicators/modifier/config.ts");
const form = read("../apps/web/app/data-modeling/indicators/modifier/form/modifier-editor-fields.tsx");
const treeOptions = read("../apps/web/app/data-modeling/indicators/workbench/config/indicator-options.ts");
const treeHook = read("../apps/web/app/data-modeling/indicators/modifier/hooks/use-modifier-indicator-tree.ts");
const service = read("../../yak-ops-modeling/src/main/java/io/yak/ops/modeling/application/service/impl/ModifierServiceImpl.java");
const dto = read("../../yak-ops-modeling/src/main/java/io/yak/ops/modeling/api/dto/modifier/ModifierDTO.java");
const po = read("../../yak-ops-dao/src/main/java/io/yak/ops/dao/entity/ModifierPO.java");
const vo = read("../../yak-ops-modeling/src/main/java/io/yak/ops/modeling/api/vo/modifier/ModifierVO.java");
const resources = read("../apps/web/service/data-modeling/indicators/index.ts");
const optionResolver = read("../apps/web/service/data-modeling/indicators/options.ts");
const indicatorTreeSource = read("../apps/web/app/data-modeling/indicators/workbench/utils/indicator-tree.ts");
const executableTreeSource = indicatorTreeSource.replace(
  'import { recordName } from "./indicator-form";',
  `const recordName = (record) => {
    const candidates = [record.businessName, record.domainName, record.martName, record.processName, record.cnName, record.name, record.enShortName];
    const value = candidates.find((item) => item !== undefined && item !== null && String(item).trim() !== "");
    return value === undefined ? String(record.id || "未命名") : String(value);
  };`,
);
if (executableTreeSource === indicatorTreeSource) {
  throw new Error("Could not prepare the indicator tree module for the Node test runner");
}
const transpiledTree = ts.transpileModule(executableTreeSource, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
});
const { buildModifierTree } = await import(
  `data:text/javascript;base64,${Buffer.from(transpiledTree.outputText).toString("base64")}`
);

test("modifier form stores an optional warehouse classification enum, not a concrete layer ID", () => {
  assert.match(config, /key: "dwType".*label: "数仓分类".*options: MODIFIER_DW_TYPE_OPTIONS/);
  assert.match(config, /value: "PUBLIC_LAYER", label: "公共层"/);
  assert.match(config, /value: "APPLICATION_LAYER", label: "应用层"/);
  assert.doesNotMatch(config, /dwhId|dwLayer|数仓分层/);
  assert.match(form, /value=\{values\.dwType \|\| ""\}/);
  assert.match(form, /onChange\("dwType", value === "__NO_LAYER__" \? "" : value\)/);
  assert.doesNotMatch(form, /dwhId|dwLayer|currentOdsLayer|SOURCE_LAYER/);
});

test("modifier tree's unassigned filter queries NULL classifications", () => {
  assert.match(treeOptions, /value: "UNASSIGNED", label: "未选择"/);
  assert.match(treeHook, /dwTypeUnassigned: "true"/);
});

test("modifier backend validates and filters by DwTypeEnum", () => {
  assert.match(dto, /private DwTypeEnum dwType;/);
  assert.match(dto, /private Boolean dwTypeUnassigned;/);
  assert.doesNotMatch(dto, /dwhId/);
  assert.match(po, /private DwTypeEnum dwType;/);
  assert.doesNotMatch(po, /dwhId/);
  assert.match(vo, /private DwTypeEnum dwType;/);
  assert.doesNotMatch(vo, /dwhId/);
  assert.match(service, /query\.getDwTypeUnassigned\(\)/);
  assert.match(service, /wrapper\.eq\("dw_type", dwType\)/);
  assert.match(service, /dwType != DwTypeEnum\.PUBLIC_LAYER[\s\S]*dwType != DwTypeEnum\.APPLICATION_LAYER/);
  assert.doesNotMatch(service, /getDwhId|dwh_id|dwhId|DwLayerDao/);
});

test("indicator form option resources no longer load concrete warehouse-layer records", () => {
  assert.doesNotMatch(resources, /"dwLayer"/);
  assert.doesNotMatch(optionResolver, /dwLayerApi|case "dwLayer"/);
});


test("modifier data-domain tree keeps the real default domain separate from unassigned modifiers", () => {
  const defaultDomain = { id: "domain-default", cnName: "默认", enShortName: "default" };
  const tree = buildModifierTree(
    [defaultDomain],
    [
      { id: "assigned", cnName: "已关联修饰词", domainId: "domain-default" },
      { id: "unassigned", cnName: "未关联修饰词", domainId: "" },
      { id: "orphaned", cnName: "失效数据域关联", domainId: "missing-domain" },
    ],
    "DATA_DOMAIN",
  )[0];

  assert.deepEqual(
    tree.children.map(({ id, title, nodeType, count }) => ({ id, title, nodeType, count })),
    [
      { id: "unassigned-domain", title: "未关联数据域", nodeType: "group", count: 2 },
      { id: "domain-default", title: "默认", nodeType: "dataDomain", count: 1 },
    ],
  );
  assert.equal(tree.count, 3);
});

test("modifier data-domain tree does not render an empty unassigned group", () => {
  const tree = buildModifierTree(
    [{ id: "domain-default", cnName: "默认" }],
    [{ id: "assigned", cnName: "已关联修饰词", domainId: "domain-default" }],
    "DATA_DOMAIN",
  )[0];

  assert.deepEqual(tree.children.map(({ id, title }) => ({ id, title })), [
    { id: "domain-default", title: "默认" },
  ]);
});

test("modifier business-type tree labels unassigned records without impersonating a business category", () => {
  const tree = buildModifierTree(
    [{ id: "business-type-1", cnName: "交易业务" }],
    [
      { id: "assigned", cnName: "已关联修饰词", busTypeId: "business-type-1" },
      { id: "unassigned", cnName: "未关联修饰词", busTypeId: "" },
      { id: "orphaned", cnName: "失效业务分类关联", busTypeId: "missing-business-type" },
    ],
    "BUSINESS_TYPE",
  )[0];

  assert.deepEqual(
    tree.children.map(({ id, title, nodeType, count }) => ({ id, title, nodeType, count })),
    [
      { id: "unassigned-business-type", title: "未关联业务分类", nodeType: "group", count: 2 },
      { id: "business-type-1", title: "交易业务", nodeType: "businessType", count: 1 },
    ],
  );
  assert.equal(tree.count, 3);
});

test("modifier business-type tree does not render an empty unassigned group", () => {
  const tree = buildModifierTree(
    [{ id: "business-type-1", cnName: "交易业务" }],
    [{ id: "assigned", cnName: "已关联修饰词", busTypeId: "business-type-1" }],
    "BUSINESS_TYPE",
  )[0];

  assert.deepEqual(tree.children.map(({ id, title }) => ({ id, title })), [
    { id: "business-type-1", title: "交易业务" },
  ]);
});

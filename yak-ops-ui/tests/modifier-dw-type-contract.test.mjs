import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

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

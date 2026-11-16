import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const root = dirname(fileURLToPath(import.meta.url));
const read = (path) => readFileSync(join(root, path), "utf8");
const form = read("../apps/web/app/data-modeling/indicators/atom-indicator/form/atom-indicator-editor-fields.tsx");
const config = read("../apps/web/app/data-modeling/indicators/atom-indicator/config.ts");
const cascader = read("../packages/yak-ui/src/cascader/Cascader.tsx");
const unitHook = read("../apps/web/app/data-modeling/indicators/atom-indicator/hooks/use-measurement-unit-tree.ts");
const ownerHook = read("../apps/web/app/data-modeling/indicators/atom-indicator/hooks/use-atom-indicator-owner-options.ts");
const page = read("../apps/web/app/data-modeling/indicators/atom-indicator/index.tsx");
const editor = read("../apps/web/app/data-modeling/indicators/workbench/components/workspace/indicator-editor.tsx");

test("atom indicator uses its own form renderer inside the shared workbench", () => {
  assert.match(page, /renderFields=\{renderAtomIndicatorFields\}/);
  assert.match(page, /<AtomIndicatorEditorFields/);
  assert.match(form, /原子指标基本信息/);
  assert.doesNotMatch(form, /业务归属|数仓分层|DW_TYPE_OPTIONS/);
  assert.match(form, /计算逻辑/);
});

test("business process cascader stores both the domain and process IDs", () => {
  assert.match(form, /columnLabels=\{\["数据域", "业务过程"\]\}/);
  assert.match(form, /domainId:\s*path\?\.\[0\]/);
  assert.match(form, /busProcessId:\s*path\?\.\[path\.length - 1\]/);
  assert.match(config, /key: "domainId".*section: "基本信息"/s);
  assert.match(config, /key: "busProcessId".*section: "基本信息".*required: true/s);
});

test("measurement unit selector uses the standard directory tree rather than a flat option list", () => {
  assert.match(form, /<TreeSelect/);
  assert.match(unitHook, /standardDirectoryApi\.tree\(projectCode, "UNIT"\)/);
  assert.match(unitHook, /selectable: isUnit/);
  assert.doesNotMatch(config, /key: "unitId".*optionsSource/);
});

test("owner selector uses searchable user options with manual entry fallback", () => {
  assert.match(form, /useAtomIndicatorOwnerOptions/);
  assert.match(form, /searchable/);
  assert.match(ownerHook, /listUsers\(/);
  assert.match(form, /请输入负责人/);
});

test("Yak UI Cascader supports controlled values and clear selection", () => {
  assert.match(cascader, /onValueChange\?\.\(path, options\)/);
  assert.match(cascader, /clearValue/);
  assert.match(cascader, /columnLabels/);
});

test("business category and process follow business caliber in the basic information form", () => {
  const caliber = form.indexOf('<FormRow label="业务口径"');
  const businessType = form.indexOf('<FormRow label="业务分类"');
  const businessProcess = form.indexOf('<FormRow label="业务过程"');
  const owner = form.indexOf('<FormRow label="负责人"');
  assert.ok(caliber >= 0 && caliber < businessType);
  assert.ok(businessType < businessProcess && businessProcess < owner);
});

test("atomic indicators always save with the public warehouse layer", () => {
  assert.doesNotMatch(form, /label="数仓分层"/);
  assert.doesNotMatch(config, /key: "dwType", label: "数仓分层"/);
  assert.match(editor, /definition\.key === "atomIndicator"\) payload\.dwType = "PUBLIC_LAYER"/);
});

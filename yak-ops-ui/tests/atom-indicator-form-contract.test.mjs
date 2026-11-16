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

test("atom indicator uses its own form renderer inside the shared workbench", () => {
  assert.match(page, /renderFields=\{renderAtomIndicatorFields\}/);
  assert.match(page, /<AtomIndicatorEditorFields/);
  assert.match(form, /原子指标基本信息/);
  assert.match(form, /业务归属/);
  assert.match(form, /计算逻辑/);
});

test("business process cascader stores both the domain and process IDs", () => {
  assert.match(form, /columnLabels=\{\["数据域", "业务过程"\]\}/);
  assert.match(form, /domainId:\s*path\?\.\[0\]/);
  assert.match(form, /busProcessId:\s*path\?\.\[path\.length - 1\]/);
  assert.match(config, /key: "domainId".*section: "业务归属"/s);
  assert.match(config, /key: "busProcessId".*required: true/s);
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

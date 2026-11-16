import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const root = dirname(fileURLToPath(import.meta.url));
const read = (path) => readFileSync(join(root, path), "utf8");
const form = read("../apps/web/app/data-modeling/indicators/derivative-indicator/form/derivative-indicator-editor-fields.tsx");

test("derived-indicator warehouse layer select renders Chinese labels for enum values", () => {
  assert.ok(form.includes(`const DW_TYPE_SELECT_ITEMS = {
  __NONE__: "不选择",
  PUBLIC_LAYER: "公共层",
  APPLICATION_LAYER: "应用层",
} as const;`));
  assert.ok(form.includes("items={DW_TYPE_SELECT_ITEMS}"));
  assert.ok(form.includes('value={supportedDwType || "__NONE__"}'));
  assert.ok(form.includes('<SelectItem value="PUBLIC_LAYER"><SelectItemText>公共层</SelectItemText>'));
  assert.ok(form.includes('<SelectItem value="APPLICATION_LAYER"><SelectItemText>应用层</SelectItemText>'));
});

test("changing warehouse layer preserves enum values and existing ownership reset behavior", () => {
  assert.ok(form.includes('dwType: next === "__NONE__" ? "" : next,'));
  assert.ok(form.includes('onValueChange={(value) => changeDwType(String(value || "__NONE__"))}'));
  for (const field of ["busTypeId", "domainId", "busProcessId", "martId", "subjectId"]) {
    assert.ok(form.includes(`${field}: ""`), `${field} should still be cleared on layer changes`);
  }
});

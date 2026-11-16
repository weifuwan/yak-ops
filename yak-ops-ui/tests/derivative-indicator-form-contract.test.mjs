import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const root = dirname(fileURLToPath(import.meta.url));
const read = (path) => readFileSync(join(root, path), "utf8");
const form = read("../apps/web/app/data-modeling/indicators/derivative-indicator/form/derivative-indicator-editor-fields.tsx");

test("derived-indicator warehouse layer select renders Chinese labels for enum values", () => {
  assert.match(form, /const DW_TYPE_SELECT_ITEMS = \\{\\s*__NONE__: "不选择",\\s*PUBLIC_LAYER: "公共层",\\s*APPLICATION_LAYER: "应用层",\\s*\\} as const;/);
  assert.match(form, /<Select<string> size="small" items=\\{DW_TYPE_SELECT_ITEMS\\} value=\\{supportedDwType \\|\\| "__NONE__"\\}/);
  assert.match(form, /<SelectItem value="PUBLIC_LAYER"><SelectItemText>公共层<\\/SelectItemText>/);
  assert.match(form, /<SelectItem value="APPLICATION_LAYER"><SelectItemText>应用层<\\/SelectItemText>/);
});

test("changing warehouse layer preserves enum values and existing ownership reset behavior", () => {
  assert.match(form, /dwType: next === "__NONE__" \\? "" : next/);
  assert.match(form, /onValueChange=\\{\\(value\\) => changeDwType\\(String\\(value \\|\\| "__NONE__"\\)\\)\\}/);
  assert.match(form, /busTypeId: "",\\s*domainId: "",\\s*busProcessId: "",\\s*martId: "",\\s*subjectId: "",/);
});

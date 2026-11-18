import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

import {
  buildNodeResourceList,
  normalizeNodeLocalParams,
  normalizeNodeResourceList,
} from "../apps/web/app/data-development/task-editor/common/node-resource-params/adapter.ts";

test("resource list adapter supports object and string values", () => {
  assert.deepEqual(
    normalizeNodeResourceList([{ resourceName: "scripts/a.sh" }, "sql/test.sql", { resourceName: "" }, null]),
    ["scripts/a.sh", "sql/test.sql", ""],
  );
});

test("resource list adapter serializes trimmed names", () => {
  assert.deepEqual(
    buildNodeResourceList([" scripts/a.sh ", "", "sql/test.sql"]),
    [{ resourceName: "scripts/a.sh" }, { resourceName: "sql/test.sql" }],
  );
});

test("local parameter adapter preserves metadata and normalizes values", () => {
  assert.deepEqual(
    normalizeNodeLocalParams([{ prop: "bizDate", direct: "OUT", type: "DATE", value: null, sensitive: 1, extension: "keep-me" }]),
    [{ prop: "bizDate", direct: "OUT", type: "DATE", value: "", sensitive: true, extension: "keep-me" }],
  );
  assert.deepEqual(
    normalizeNodeLocalParams([{ prop: "id" }]),
    [{ prop: "id", direct: "IN", type: "VARCHAR", value: "", sensitive: false }],
  );
});

test("resource and parameter panel stays focused on controls without explanatory copy", () => {
  const panelSource = readFileSync(
    new URL("../apps/web/app/data-development/task-editor/common/node-resource-params/panel.tsx", import.meta.url),
    "utf8",
  );

  assert.doesNotMatch(panelSource, /选择资源管理中的文件/);
  assert.doesNotMatch(panelSource, /配置当前节点的输入、输出参数及敏感属性/);
  assert.doesNotMatch(panelSource, /添加 IN 参数供当前节点引用/);
  assert.doesNotMatch(panelSource, /localParamsDescription/);
});

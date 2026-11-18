import assert from "node:assert/strict";
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

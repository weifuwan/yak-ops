import assert from "node:assert/strict";
import { test } from "node:test";
import { formatMetricCount } from "../apps/web/service/data-sync/report.ts";

test("nullable metric values remain distinct from a measured zero", () => {
  assert.equal(formatMetricCount(null), "—");
  assert.equal(formatMetricCount(undefined), "—");
  assert.equal(formatMetricCount(0), "0");
  assert.equal(formatMetricCount(1234), "1,234");
});

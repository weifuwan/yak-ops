import assert from "node:assert/strict";
import { test } from "node:test";
import {
  parseTaskListFilters,
  updateTaskListFilters,
} from "../apps/web/app/data-sync/list/filters.ts";

test("overview links select the intended task type", () => {
  for (const scope of ["SINGLE_TABLE", "MULTI_TABLE", "DATABASE"]) {
    const filters = parseTaskListFilters(new URLSearchParams({ scope }));
    assert.equal(filters.scope, scope);
    assert.equal(filters.pageNo, 1);
  }
});

test("changing task type resets page and retains the existing search and status", () => {
  const existing = new URLSearchParams("page=4&status=ONLINE&keyword=orders");
  const next = updateTaskListFilters(existing, { scope: "MULTI_TABLE", pageNo: 1 });
  assert.equal(next.get("scope"), "MULTI_TABLE");
  assert.equal(next.get("page"), null);
  assert.equal(next.get("status"), "ONLINE");
  assert.equal(next.get("keyword"), "orders");
});

test("all task types omit scope parameter and invalid values cannot filter silently", () => {
  const invalid = parseTaskListFilters(new URLSearchParams("scope=unknown"));
  assert.equal(invalid.scope, "ALL");
  const next = updateTaskListFilters(new URLSearchParams("scope=DATABASE"), { scope: "ALL" });
  assert.equal(next.get("scope"), null);
});

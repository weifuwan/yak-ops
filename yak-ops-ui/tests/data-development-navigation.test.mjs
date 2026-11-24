import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const rail = read("../apps/web/app/data-development/layout/development-rail.tsx");
const panel = read("../apps/web/app/data-development/layout/development-panel.tsx");

test("data development rail only exposes implemented sections", () => {
  const sectionIds = [...rail.matchAll(/\{ id: "([^"]+)", label:/g)].map((match) => match[1]);

  assert.deepEqual(sectionIds, [
    "development",
    "manual",
    "backfill",
    "lineage",
    "task-instances",
    "parameters",
    "catalog",
    "resource",
    "history",
    "operation-check",
  ]);

  for (const unusedSection of [
    "component",
    "ai-processing",
    "review",
    "smoke-test",
    "code-management",
    "governance",
    "recycle-bin",
  ]) {
    assert.doesNotMatch(rail, new RegExp(`"${unusedSection}"`));
  }

  assert.doesNotMatch(panel, /该能力暂未接入当前后端/);
});

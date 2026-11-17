import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const root = dirname(fileURLToPath(import.meta.url));
const read = (path) => readFileSync(join(root, path), "utf8");
const overview = read("../apps/web/app/data-modeling/overview/index.tsx");

test("data modeling overview contains the four linked modeling workspaces", () => {
  for (const [title, path] of [
    ["数仓规划", "/data-modeling/planning/business-type"],
    ["维度建模", "/data-modeling/models/positive"],
    ["数据标准", "/data-modeling/standards/fields"],
    ["数据指标", "/data-modeling/indicators/compose"],
  ]) {
    assert.ok(overview.includes(title), "overview should show " + title);
    assert.ok(overview.includes(path), "overview should link to " + title);
  }
});

test("overview includes the introduction, asset statistics and latest publish areas", () => {
  for (const label of ["智能数据建模", "数据概览", "最新发布"]) {
    assert.ok(overview.includes(label), "overview should render " + label);
  }

  for (const metric of [
    "维度表模型",
    "明细表模型",
    "汇总表模型",
    "应用表模型",
    "原子指标",
    "派生指标 & 复合指标",
  ]) {
    assert.ok(overview.includes(metric), "overview should include " + metric);
  }
});

test("overview does not display fabricated metric counts or publish records", () => {
  assert.ok(overview.includes("统计接口待接入"));
  assert.ok(overview.includes("发布记录暂未接入"));
  assert.match(overview, /text-2xl font-semibold tabular-nums[\s\S]*?—/);
});

test("workspace bootstrap status and manual refresh remain available", () => {
  assert.ok(overview.includes("getModelingBootstrap"));
  assert.ok(overview.includes('queryKey: ["data-modeling", "bootstrap", currentWorkspace?.id]'));
  assert.ok(overview.includes("bootstrapQuery.refetch()"));
  assert.ok(overview.includes("服务连接正常"));
});

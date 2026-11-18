import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const root = dirname(fileURLToPath(import.meta.url));
const read = (path) => readFileSync(join(root, path), "utf8");
const overview = read("../apps/web/app/data-modeling/overview/index.tsx");

test("data modeling overview keeps the current landing-page structure", () => {
  for (const label of ["数据概览", "最新发布", "工作空间：", "全部"]) {
    assert.ok(overview.includes(label), "overview should render " + label);
  }

  assert.ok(
    overview.includes("DataModelingOverviewPage"),
    "overview should expose the page entry",
  );
  assert.ok(
    overview.includes("O1CN01d9iDld1SKva5mO2VE"),
    "overview should keep the landing hero",
  );
});

test("overview keeps all modeling statistic categories", () => {
  for (const title of [
    "维度表模型",
    "明细表模型",
    "汇总表模型",
    "应用表模型",
    "派生指标&复合指标",
    "合计",
  ]) {
    assert.ok(overview.includes(title), "overview should include " + title);
  }
});

test("overview does not display fabricated metric counts or publish records", () => {
  for (const fabricatedValue of ['num: "0"', 'num: "1"', 'num: "2"', 'num: "3"']) {
    assert.equal(
      overview.includes(fabricatedValue),
      false,
      "overview should not hard-code " + fabricatedValue,
    );
  }

  assert.ok(overview.includes('num: "—"'));
  assert.ok(overview.includes("统计接口待接入"));
  assert.ok(overview.includes("暂无数据"));
});

test("workspace filter remains a controlled Yak UI Select", () => {
  assert.match(overview, /<Select[\s\S]*?value=\{workspaceFilter\}/);
  assert.ok(overview.includes('value="all"'));
  assert.ok(overview.includes('setWorkspaceFilter(String(value ?? "all"))'));
});

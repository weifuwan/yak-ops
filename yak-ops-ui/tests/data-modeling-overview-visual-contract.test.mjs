import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const root = dirname(fileURLToPath(import.meta.url));
const read = (path) => readFileSync(join(root, path), "utf8");
const overview = read("../apps/web/app/data-modeling/overview/index.tsx");
const overviewApi = read("../apps/web/service/data-modeling/overview.ts");
const modelingController = read("../../yak-ops-api/src/main/java/io/yak/ops/api/controller/ModelingController.java");
const modelingService = read("../../yak-ops-modeling/src/main/java/io/yak/ops/modeling/application/ModelingModuleServiceImpl.java");

test("data modeling overview keeps the current landing-page structure", () => {
  for (const label of ["数据概览", "最新发布", "工作空间：", "全部"]) {
    assert.ok(overview.includes(label), "overview should render " + label);
  }

  assert.ok(
    overview.includes("DataModelingOverviewPage"),
    "overview should expose the page entry",
  );
  assert.equal(
    overview.includes("O1CN01d9iDld1SKva5mO2VE"),
    false,
    "overview should not depend on the legacy external hero image",
  );
  assert.ok(
    overview.includes('from "./components/PixelBlast"'),
    "overview should use the PixelBlast hero background",
  );
  for (const heroText of [
    "让数据模型",
    "更清晰、更统一",
    "从数仓规划、数据标准到维度建模与指标管理，统一沉淀数据资产。",
    "开始建模",
    "指标管理",
    "PixelArrowIcon",
    "border-dotted",
    "border-b-[5px]",
    'to="/kimball/models/positive"',
    'to="/kimball/indicators/atom"',
  ]) {
    assert.ok(overview.includes(heroText), "overview hero should include " + heroText);
  }
  for (const presetValue of [
    'variant="square"',
    'pixelSize={3}',
    'color="#A9ABB5"',
    'accentColor="#A9ABB5"',
    'patternScale={1.5}',
    'patternDensity={0.62}',
    'pixelSizeJitter={0.06}',
    'softness={0}',
    'smoothing={0}',
    'glow={0.08}',
    'twinkle={0}',
    'speed={0.25}',
    'drift={0}',
    'hoverEffect="none"',
    'rippleSpeed={0.3}',
    'rippleThickness={0.1}',
    'rippleIntensityScale={0.95}',
    'autoRipple={0}',
    'edgeFade={0.25}',
    'intro={false}',
    'lightMode',
  ]) {
    assert.ok(overview.includes(presetValue), "overview should keep " + presetValue);
  }
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

test("overview reads project-scoped real statistics instead of fabricated counts", () => {
  assert.ok(overview.includes("modelingOverviewApi.get(projectCode)"));
  assert.ok(overview.includes('queryKey: ["modeling", "overview", projectCode]'));
  assert.ok(overview.includes("models?.dimension"));
  assert.ok(overview.includes("models?.detail"));
  assert.ok(overview.includes("models?.summary"));
  assert.ok(overview.includes("models?.application"));
  assert.ok(overview.includes("indicators?.derived"));
  assert.ok(overview.includes("indicators?.composite"));
  assert.ok(overview.includes("overview?.totalAssets"));
  assert.equal(overview.includes("统计接口待接入"), false);
  assert.ok(overview.includes("latestPublished"));
  assert.ok(overview.includes("暂无数据"));
});

test("overview API is backed by one project-scoped aggregation endpoint", () => {
  assert.ok(overviewApi.includes('modelingBasePath(projectCode) + "/overview"'));
  assert.ok(modelingController.includes('@GetMapping("/overview")'));
  assert.ok(modelingController.includes("modelingModuleService.getOverview(projectCode)"));

  for (const type of ["DIMENSION", "DETAIL", "SUMMARY", "APPLICATION"]) {
    assert.ok(modelingService.includes("DimensionTableTypeEnum." + type));
  }
  for (const resource of ["atom-indicator", "derivative-indicator", "compose-indicator"]) {
    assert.ok(modelingService.includes('"' + resource + '"'));
  }
  assert.ok(modelingService.includes('.eq("submit", Boolean.TRUE)'));
  assert.ok(modelingService.includes("LATEST_PUBLISH_LIMIT = 5"));
});

test("workspace filter remains a controlled Yak UI Select", () => {
  assert.match(overview, /<Select[\s\S]*?value=\{workspaceFilter\}/);
  assert.ok(overview.includes('value="all"'));
  assert.ok(overview.includes('setWorkspaceFilter(String(value ?? "all"))'));
});

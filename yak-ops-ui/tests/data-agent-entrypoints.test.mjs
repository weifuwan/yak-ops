import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const page = read("../apps/web/app/data-agent/page.tsx");
const rail = read("../apps/web/app/data-development/layout/development-rail.tsx");
const layout = read("../apps/web/app/data-development/layout/data-development-layout.tsx");
const router = read("../apps/web/app/router/AppRouter.tsx");
const navigation = read("../apps/web/app/layout/navigation.ts");

test("Data Agent has a standalone full-width app page", () => {
  assert.match(page, /productPath="\/data-agent"/);
  assert.match(page, /你好，我是 Yak-OPS Data Agent/);
  assert.match(page, /理解数据、构建任务/);
  assert.match(page, /智能对话与工具调用能力正在建设中/);
});

test("the Data Development sparkle shortcut navigates to Data Agent", () => {
  assert.match(rail, /onClick=\{onOpenAgent\}/);
  assert.match(layout, /onOpenAgent=\{\(\) => navigate\("\/data-agent"\)\}/);
});

test("Data Agent is registered as an authenticated route and product-menu entry", () => {
  assert.match(router, /path="\/data-agent" element=\{<DataAgentPage \/>\}/);
  assert.match(router, /"\/data-agent"/);
  assert.match(navigation, /id: "data-agent"/);
  assert.match(navigation, /label: "Data Agent"/);
  assert.match(navigation, /label: "智能体"/);
  assert.match(navigation, /DATA_AGENT_PRODUCT/);
});

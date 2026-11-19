import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const page = read("../apps/web/app/data-agent/page.tsx");
const workspace = read("../apps/web/app/data-agent/components/DataAgentWorkspace.tsx");
const composer = read("../apps/web/app/data-agent/components/DataAgentPromptComposer.tsx");
const rail = read("../apps/web/app/data-development/layout/development-rail.tsx");
const layout = read("../apps/web/app/data-development/layout/data-development-layout.tsx");
const router = read("../apps/web/app/router/AppRouter.tsx");
const navigation = read("../apps/web/app/layout/navigation.ts");

test("Data Agent reuses Yakable's full-screen workspace structure", () => {
  assert.ok(page.includes('productPath="/data-agent"'));
  assert.match(page, /<DataAgentWorkspace \/>/);
  assert.match(workspace, /你好，我是 Yak-OPS Data Agent/);
  assert.match(workspace, /理解数据、构建任务/);
  assert.match(workspace, /最近对话/);
  assert.match(workspace, /新建对话/);
  assert.match(workspace, /前端预览模式/);
  assert.match(workspace, /暂无工作区文件/);
});

test("Data Agent conversations stay local and do not call backend services", () => {
  assert.match(workspace, /setConversations/);
  assert.match(workspace, /消息已在当前页面本地展示/);
  assert.match(workspace, /后端接口尚未接入/);
  assert.doesNotMatch(workspace, /ProjectService|SessionService|fetch\s*\(/);
  assert.doesNotMatch(composer, /fetch\s*\(/);
});

test("Data Agent prompt composer preserves Yakable-style input interactions", () => {
  assert.match(composer, /placeholderSuggestions/);
  assert.match(composer, /onCompositionStart/);
  assert.match(composer, /event\.shiftKey/);
  assert.match(composer, /data-agent-composer-surface/);
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

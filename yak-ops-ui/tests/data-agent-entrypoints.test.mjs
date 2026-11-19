import test from "node:test";
import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const page = read("../apps/web/app/data-agent/page.tsx");
const welcome = read("../apps/web/app/data-agent/components/DataAgentWelcome.tsx");
const workspace = read("../apps/web/app/data-agent/components/DataAgentWorkspace.tsx");
const sidebar = read("../apps/web/app/data-agent/components/DataAgentSidebar.tsx");
const composer = read("../apps/web/app/data-agent/components/DataAgentPromptComposer.tsx");
const globalStyles = read("../apps/web/app/styles/global.css");
const rail = read("../apps/web/app/data-development/layout/development-rail.tsx");
const layout = read("../apps/web/app/data-development/layout/data-development-layout.tsx");
const router = read("../apps/web/app/router/AppRouter.tsx");
const navigation = read("../apps/web/app/layout/navigation.ts");

test("Data Agent opens on a welcome screen and enters the existing workspace", () => {
  assert.ok(page.includes('productPath="/data-agent"'));
  assert.match(page, /<DataAgentWelcome onEnter=\{enterWorkspace\} \/>/);
  assert.match(page, /<DataAgentWorkspace initialPrompt=\{initialPrompt\} \/>/);
  assert.match(page, /setWorkspaceOpen\(true\)/);
  assert.match(welcome, /src="\/yakable_logo\.png"/);
  assert.match(welcome, /onClick=\{\(\) => onEnter\(\)\}/);
  assert.match(welcome, /onEnter\(entry\.prompt\)/);
  assert.match(welcome, /数据表 \/ 元数据/);
  assert.match(welcome, /SQL \/ 脚本/);
  assert.match(welcome, /任务运行日志/);
  assert.match(welcome, /直接描述需求/);
  assert.match(welcome, /animate-data-agent-mascot-float/);
  assert.doesNotMatch(workspace, /你好，我是 Yak-OPS Data Agent|理解数据、构建任务|Enter 发送 · Shift/);
  assert.ok(!workspace.includes('activeConversation?.title ?? "新对话"'));
  assert.ok(!workspace.includes("前端预览</span>"));
  assert.match(workspace, /bg-\[#f0f0f0\] p-2 max-md:p-0/);
  assert.match(workspace, /rounded-2xl border border-\[#e7e7e4\] bg-white/);
  assert.match(workspace, /<DataAgentSidebar/);
  assert.doesNotMatch(workspace, /前端预览模式|最近对话|新建对话/);
  assert.match(workspace, /暂无工作区文件/);
  assert.match(sidebar, /w-64/);
  assert.ok(sidebar.includes('src="/yakable_logo.png"'));
  assert.doesNotMatch(sidebar, /<Sparkles/);
  assert.match(sidebar, /New/);
  assert.match(sidebar, /Search/);
  assert.match(sidebar, /Projects/);
  assert.match(sidebar, /Connectors/);
  assert.match(sidebar, /Automations/);
  assert.match(sidebar, /Data Sync Hub/);
  assert.match(sidebar, /Upgrade to Pro/);
  assert.match(sidebar, /Unlock more features/);
  assert.match(sidebar, /useWorkspace/);
  assert.match(sidebar, /onSelectConversation/);
});

test("Data Agent welcome and composer styling use Tailwind utilities", () => {
  assert.doesNotMatch(page, /data-agent\.css/);
  assert.equal(
    existsSync(new URL("../apps/web/app/data-agent/data-agent.css", import.meta.url)),
    false,
  );
  assert.match(welcome, /animate-data-agent-message-in/);
  assert.match(welcome, /animate-data-agent-card-in/);
  assert.match(welcome, /max-\[640px\]:hidden/);
  assert.match(welcome, /\[@media\(max-height:680px\)\]:hidden/);
  assert.match(welcome, /motion-reduce:animate-none/);
  assert.doesNotMatch(welcome, /data-agent-welcome-|data-agent-mascot-(?:eye|shine)/);
  assert.match(composer, /group\/composer relative isolate w-full/);
  assert.match(composer, /group-focus-within\/composer:opacity-20/);
  assert.match(composer, /animate-data-agent-caret/);
  assert.doesNotMatch(composer, /data-agent-composer-[a-z-]+/);
  assert.match(globalStyles, /--animate-data-agent-message-in:/);
  assert.match(globalStyles, /--animate-data-agent-card-in:/);
  assert.match(globalStyles, /--animate-data-agent-mascot-float:/);
  assert.match(globalStyles, /--animate-data-agent-caret:/);
  assert.match(globalStyles, /@keyframes data-agent-message-in/);
});

test("welcome entrypoints seed the composer without claiming file upload support", () => {
  assert.match(workspace, /initialPrompt/);
  assert.match(workspace, /setInitialDraft\(""\)/);
  assert.match(composer, /initialValue\?: string/);
  assert.match(composer, /useState\(initialValue\)/);
  assert.doesNotMatch(welcome, /type="file"|上传成功|文件已导入/);
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
  assert.match(composer, /rounded-3xl bg-white/);
  assert.match(composer, /AI 助手/);
});

test("the Data Development sparkle shortcut navigates to Data Agent", () => {
  assert.match(rail, /onClick=\{onOpenAgent\}/);
  assert.match(layout, /onOpenAgent=\{\(\) => navigate\(\"\/data-agent\"\)\}/);
});

test("Data Agent is registered as an authenticated route and product-menu entry", () => {
  assert.match(router, /path=\"\/data-agent\" element=\{<DataAgentPage \/>/);
  assert.match(router, /\"\/data-agent\"/);
  assert.match(navigation, /id: \"data-agent\"/);
  assert.match(navigation, /label: \"Data Agent\"/);
  assert.match(navigation, /label: \"智能体\"/);
  assert.match(navigation, /DATA_AGENT_PRODUCT/);
});

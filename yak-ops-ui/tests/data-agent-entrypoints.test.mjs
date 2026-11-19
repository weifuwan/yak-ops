import test from "node:test";
import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const page = read("../apps/web/app/data-agent/page.tsx");
const welcome = read("../apps/web/app/data-agent/components/DataAgentWelcome.tsx");
const workspace = read("../apps/web/app/data-agent/components/DataAgentWorkspace.tsx");
const sidebar = read("../apps/web/app/data-agent/components/DataAgentSidebar.tsx");
const sidebarResizer = read("../apps/web/app/data-agent/components/DataAgentSidebarResizer.tsx");
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
  assert.match(welcome, /right-\[max\(28px,calc\(50%_-_300px\)\)\] bottom-8/);
  assert.match(welcome, /onPointerMove=\{handlePointerMove\}/);
  assert.match(welcome, /onPointerLeave=\{resetEyePosition\}/);
  assert.match(welcome, /getBoundingClientRect\(\)/);
  assert.match(welcome, /maxEyeOffset = 1\.8/);
  assert.match(welcome, /trackingRadius = 96/);
  assert.match(welcome, /translate3d\(/);
  assert.match(welcome, /ref=\{leftEyeRef\}/);
  assert.match(welcome, /ref=\{rightEyeRef\}/);
  assert.doesNotMatch(workspace, /你好，我是 Yak-OPS Data Agent|理解数据、构建任务|Enter 发送 · Shift/);
  assert.ok(!workspace.includes('activeConversation?.title ?? "新对话"'));
  assert.ok(!workspace.includes("前端预览</span>"));
  assert.match(workspace, /bg-\[#f0f0f0\] p-2 max-md:p-0/);
  assert.match(workspace, /rounded-2xl border border-\[#e7e7e4\] bg-white/);
  assert.match(workspace, /<DataAgentSidebar/);
  assert.doesNotMatch(workspace, /前端预览模式|最近对话|新建对话/);
  assert.match(sidebar, /w-full min-w-0 shrink-0 flex-col/);
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

test("the Data Agent welcome workspace keeps the centered prompt and shortcut layout without the unused side panel", () => {
  assert.match(workspace, /max-w-\[540px\]/);
  assert.match(workspace, /grid grid-cols-2 gap-2/);
  assert.match(workspace, /分析数据表/);
  assert.match(workspace, /生成 SQL/);
  assert.match(workspace, /排查任务异常/);
  assert.match(workspace, /梳理数据开发流程/);
  assert.match(workspace, /handleQuickAction/);
  assert.match(workspace, /aria-label=\{sidebarOpen \? "收起侧边栏" : "展开侧边栏"\}/);
  assert.doesNotMatch(workspace, /workspacePanelOpen|AgentWorkspacePanel|PanelRight|FolderOpen|工作区面板|暂无工作区文件/);
});

test("Data Agent sidebar uses a smooth drawer transition without unmounting", () => {
  assert.match(workspace, /const \[sidebarOpen, setSidebarOpen\] = useState\(true\)/);
  assert.match(workspace, /aria-hidden=\{!sidebarOpen\}/);
  assert.match(workspace, /element\.inert = !sidebarOpen/);
  assert.match(workspace, /transition-\[width\]/);
  assert.match(workspace, /duration-\[320ms\]/);
  assert.match(workspace, /ease-\[cubic-bezier\(0\.22,1,0\.36,1\)\]/);
  assert.match(workspace, /translate-x-0 opacity-100 pointer-events-auto/);
  assert.match(workspace, /-translate-x-3 opacity-0 pointer-events-none/);
  assert.match(workspace, /w-\[var\(--data-agent-sidebar-width\)\] max-md:!w-0/);
  assert.match(workspace, /sidebarWidth/);
  assert.match(workspace, /sidebarResizing/);
  assert.match(workspace, /\? "transition-none/);
  assert.match(workspace, /<DataAgentSidebarResizer/);
  assert.match(sidebar, /w-full min-w-0 shrink-0 flex-col/);
  assert.ok(workspace.includes("aria-hidden={!sidebarOpen}"));
  assert.ok(workspace.includes("<DataAgentSidebar"));
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
  assert.match(composer, /rounded-\[14px\] border border-\[#e0e4e3\]/);
  assert.match(composer, /animate-data-agent-caret/);
  assert.match(composer, /aria-label="向 Data Agent 发送消息"/);
  assert.match(composer, /附件上传暂未接入/);
  assert.doesNotMatch(composer, /data-agent-composer-[a-z-]+/);
  assert.match(globalStyles, /--animate-data-agent-message-in:/);
  assert.match(globalStyles, /--animate-data-agent-card-in:/);
  assert.match(globalStyles, /--animate-data-agent-mascot-float:/);
  assert.match(globalStyles, /--animate-data-agent-caret:/);
  assert.match(globalStyles, /@keyframes data-agent-message-in/);
});

test("welcome entrypoints seed the composer without claiming file upload support", () => {
  assert.match(workspace, /initialPrompt/);
  assert.match(workspace, /setInitialDraft\(\"\"\)/);
  assert.match(composer, /initialValue\?: string/);
  assert.match(composer, /useState\(initialValue\)/);
  assert.doesNotMatch(welcome, /type="file"|上传成功|文件已导入/);
});

test("Data Agent conversations use persisted agent sessions and live SSE responses", () => {
  assert.match(workspace, /createAgentSession/);
  assert.match(workspace, /listAgentSessions/);
  assert.match(workspace, /sendAgentMessage/);
  assert.match(workspace, /text_delta/);
  assert.match(workspace, /tool_start/);
  assert.match(workspace, /tool_result/);
  assert.match(workspace, /confirm_required/);
  assert.match(workspace, /确认执行/);
  assert.match(workspace, /拒绝/);
  assert.match(workspace, /历史消息暂不提供回放/);
  assert.match(composer, /isSubmitting\?: boolean/);
  assert.match(composer, /disabled=\{isSubmitting \|\| !value\.trim\(\)\}/);
});

test("the model selector uses the Yak-UI portal menu and opens below the trigger", () => {
  assert.match(workspace, /<DropdownMenu open=\{open\} onOpenChange=\{onOpenChange\}>/);
  assert.match(
    workspace,
    /<DropdownMenuContent[\s\S]*?side="bottom"[\s\S]*?align="end"[\s\S]*?sideOffset=\{8\}/,
  );
  assert.match(workspace, /role="menuitemradio"/);
  assert.match(workspace, /aria-checked=\{value === "DeepSeek"\}/);
  assert.doesNotMatch(workspace, /absolute bottom-full|bottom-full right-0/);
});

test("Data Agent prompt composer preserves Yakable-style input interactions", () => {
  assert.match(composer, /placeholderSuggestions/);
  assert.match(composer, /onCompositionStart/);
  assert.match(composer, /event\.shiftKey/);
  assert.match(composer, /rounded-\[14px\] border/);
  assert.match(composer, /aria-label=\{isSubmitting \? "正在发送消息" : "发送消息"\}/);
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

test("Data Agent sidebar has an accessible, bounded drag splitter", () => {
  assert.match(workspace, /const \[sidebarWidth, setSidebarWidth\] = useState\(256\)/);
  assert.match(workspace, /onResize=\{setSidebarWidth\}/);
  assert.match(workspace, /onDraggingChange=\{setSidebarResizing\}/);
  assert.match(sidebarResizer, /SIDEBAR_MIN_WIDTH = 220/);
  assert.match(sidebarResizer, /SIDEBAR_MAX_WIDTH = 440/);
  assert.match(sidebarResizer, /role="separator"/);
  assert.match(sidebarResizer, /aria-orientation="vertical"/);
  assert.match(sidebarResizer, /aria-valuenow=\{Math\.round\(width\)\}/);
  assert.match(sidebarResizer, /setPointerCapture/);
  assert.match(sidebarResizer, /hasPointerCapture/);
  assert.match(sidebarResizer, /col-resize/);
  assert.match(sidebarResizer, /ArrowLeft/);
  assert.match(sidebarResizer, /ArrowRight/);
  assert.match(sidebarResizer, /onLostPointerCapture/);
  assert.match(sidebarResizer, /translate-x-\[9px\]/);
  assert.match(sidebarResizer, /top-8 bottom-8/);
  assert.match(sidebarResizer, /h-6 w-2/);
  assert.match(sidebarResizer, /group-hover:opacity-100/);
  assert.match(sidebarResizer, /group-data-\[dragging=true\]:opacity-100/);
  assert.match(sidebarResizer, /document\.body\.style\.cursor = "col-resize"/);
  assert.match(sidebarResizer, /document\.body\.style\.userSelect = "none"/);
});

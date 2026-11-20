import test from "node:test";
import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const page = read("../apps/web/app/data-agent/page.tsx");
const welcome = read("../apps/web/app/data-agent/components/DataAgentWelcome.tsx");
const workspace = read("../apps/web/app/data-agent/components/DataAgentWorkspace.tsx");
const conversationHeader = read("../apps/web/app/data-agent/components/workspace/DataAgentConversationHeader.tsx");
const sessionWorkspace = read("../apps/web/app/data-agent/components/workspace/DataAgentSessionWorkspace.tsx");
const emptyState = read("../apps/web/app/data-agent/components/workspace/DataAgentEmptyState.tsx");
const emptyStateStyles = read("../apps/web/app/data-agent/components/workspace/data-agent-empty-state.css");
const quickActions = read("../apps/web/app/data-agent/components/workspace/quick-actions.ts");
const sidebar = read("../apps/web/app/data-agent/components/DataAgentSidebar.tsx");
const sidebarNavigation = read("../apps/web/app/data-agent/components/sidebar/DataAgentSidebarNavigation.tsx");
const conversationList = read("../apps/web/app/data-agent/components/sidebar/DataAgentConversationList.tsx");
const sidebarFooter = read("../apps/web/app/data-agent/components/sidebar/DataAgentSidebarFooter.tsx");
const sidebarResizer = read("../apps/web/app/data-agent/components/DataAgentSidebarResizer.tsx");
const detailsResizer = read("../apps/web/app/data-agent/components/workspace/conversation/AgentDetailsResizer.tsx");
const composer = read("../apps/web/app/data-agent/components/DataAgentPromptComposer.tsx");
const modelSelector = read("../apps/web/app/data-agent/components/workspace/ModelSelector.tsx");
const conversationHook = read("../apps/web/hooks/useDataAgentConversation.ts");
const loading = read("../apps/web/app/data-agent/components/workspace/DataAgentLoading.tsx");
const loadingStyles = read("../apps/web/app/data-agent/components/workspace/data-agent-loading.css");
const reducer = read("../apps/web/app/data-agent/model/conversation-reducer.ts");
const assistantMessage = read("../apps/web/app/data-agent/components/workspace/conversation/AssistantMessage.tsx");
const activityTimeline = read("../apps/web/app/data-agent/components/workspace/conversation/activity/AgentActivityTimeline.tsx");
const activityItem = read("../apps/web/app/data-agent/components/workspace/conversation/activity/AgentActivityItem.tsx");
const toolActivity = read("../apps/web/app/data-agent/components/workspace/conversation/activity/AgentToolActivity.tsx");
const conversationPanel = read("../apps/web/app/data-agent/components/workspace/ConversationPanel.tsx");
const detailsPanel = read("../apps/web/app/data-agent/components/workspace/conversation/AgentDetailsPanel.tsx");
const detailsTimeline = read("../apps/web/app/data-agent/components/workspace/conversation/AgentDetailsTimeline.tsx");


const messageItem = read("../apps/web/app/data-agent/components/workspace/conversation/MessageItem.tsx");
const messageActions = read("../apps/web/app/data-agent/components/workspace/conversation/MessageActions.tsx");
const confirmationCard = read("../apps/web/app/data-agent/components/workspace/conversation/ConfirmationCard.tsx");
const globalStyles = read("../apps/web/app/styles/global.css");
const rail = read("../apps/web/app/data-development/layout/development-rail.tsx");
const layout = read("../apps/web/app/data-development/layout/data-development-layout.tsx");
const router = read("../apps/web/app/router/AppRouter.tsx");
const navigation = read("../apps/web/app/layout/navigation.ts");

test("Data Agent opens on a welcome screen and enters the existing workspace", () => {
  assert.ok(page.includes('productPath="/data-agent"'));
  assert.match(page, /location\.pathname === "\/data-agent"/);
  assert.match(page, /<DataAgentWelcome/);
  assert.match(page, /navigate\("\/data-agent\/new", \{ state: \{ initialPrompt: prompt \} \}\)/);
  assert.match(page, /sessionId=\{sessionId \?\? null\}/);
  assert.match(page, /onSessionCreated=\{handleSessionCreated\}/);
  assert.match(page, /onNewConversation=\{handleNewConversation\}/);
  assert.match(page, /onSelectConversation=\{handleSelectConversation\}/);
  assert.match(page, /\/data-agent\/sessions\//);
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
  assert.ok(workspace.includes('activeConversation?.title ?? "新对话"'));
  assert.ok(!workspace.includes("前端预览</span>"));
  assert.match(workspace, /bg-\[#f0f0f0\] p-2 max-md:p-0/);
  assert.match(workspace, /rounded-2xl border border-\[#e7e7e4\] bg-white/);
  assert.match(workspace, /<DataAgentEmptyState/);
  assert.match(workspace, /<DataAgentSessionWorkspace/);
  assert.match(workspace, /<DataAgentSidebar/);
  assert.doesNotMatch(workspace, /前端预览模式|最近对话/);
  assert.match(sidebar, /w-full min-w-0 shrink-0 flex-col/);
  assert.ok(sidebar.includes('src="/yakable_logo.png"'));
  assert.match(sidebar, /useWorkspace/);
  assert.match(sidebar, /onSelectConversation/);
  assert.match(sidebarNavigation, /New/);
  assert.match(sidebarNavigation, /Search/);
  assert.doesNotMatch(sidebarNavigation, /Projects|projectsExpanded|LayoutGrid/);
  assert.match(sidebarNavigation, /Connectors/);
  assert.match(sidebarNavigation, /Automations/);
  assert.doesNotMatch(conversationList, /Projects|Data Sync Hub|projectsExpanded/);
  assert.match(conversationList, /Recent chats/);
  assert.match(conversationList, /No sessions yet/);
  assert.match(sidebarFooter, /Upgrade to Pro/);
  assert.match(sidebarFooter, /Unlock more features/);
});

test("Data Agent restores the original empty workspace and uses a shared split header for conversations", () => {
  const emptyBranchIndex = workspace.indexOf("{!hasMessages ? (");
  const emptyStateIndex = workspace.indexOf("<DataAgentEmptyState", emptyBranchIndex);
  const sessionWorkspaceIndex = workspace.indexOf("<DataAgentSessionWorkspace");
  const conversationBranch = workspace.slice(sessionWorkspaceIndex);

  assert.ok(emptyBranchIndex >= 0, "empty workspace should have its own layout branch");
  assert.ok(emptyStateIndex > emptyBranchIndex, "empty state should render in the original workspace branch");
  assert.ok(sessionWorkspaceIndex > emptyStateIndex, "session workspace should only render after the empty state branch");
  assert.match(workspace.slice(emptyBranchIndex, sessionWorkspaceIndex), /bg-\[#f0f0f0\] p-2 max-md:p-0/);
  assert.match(workspace.slice(emptyBranchIndex, sessionWorkspaceIndex), /rounded-2xl border border-\[#e7e7e4\] bg-white/);
  assert.match(conversationBranch, /title=\{activeConversation\?\.title \?\? "新对话"\}/);
  assert.match(conversationBranch, /messages=\{messages\}/);
  assert.match(conversationBranch, /trailingActions=\{modelSelector\}/);
  assert.doesNotMatch(conversationBranch, /p-2 max-md:p-0|rounded-2xl border border-\[#e7e7e4\]/);

  assert.match(sessionWorkspace, /<DataAgentConversationHeader/);
  assert.match(sessionWorkspace, /<ConversationPanel/);
  assert.match(sessionWorkspace, /<AgentDetailsPanel/);
  assert.match(conversationHeader, /h-12 shrink-0 items-center bg-white/);
  assert.doesNotMatch(conversationHeader, /border-b/);
  assert.match(conversationHeader, /aria-label=\{sidebarOpen \? "收起侧边栏" : "展开侧边栏"\}/);
  assert.match(conversationHeader, /onToggleSidebar/);
  assert.match(conversationHeader, /isReconnecting \?[\s\S]*?"正在重新连接"/);
  assert.match(conversationHeader, /detailsOpen/);
  assert.match(conversationHeader, /Timeline/);
  assert.match(conversationHeader, /Changes/);
  assert.match(conversationHeader, /onToggleDetails/);
  assert.match(conversationHeader, /cursor-pointer/);

  assert.match(page, /location\.pathname === "\/data-agent"/);
  assert.match(page, /\/data-agent\/sessions\//);
  assert.match(welcome, /rounded-\[14px\] border border-\[#dfe4e4\] bg-white/);
});

test("Data Agent sidebar keeps the Recent chats section visible when there are no sessions", () => {
  assert.match(conversationList, /<section aria-label="Recent conversations">/);
  assert.match(conversationList, /Recent chats/);
  assert.match(conversationList, /groups.length > 0 \?/);
  assert.match(conversationList, /No sessions yet/);
  assert.doesNotMatch(conversationList, /projectsExpanded|Data Sync Hub/);
});

test("the welcome workspace keeps its centered prompt and shortcut layout", () => {
  assert.match(emptyState, /max-w-\[540px\]/);
  assert.match(emptyState, /grid grid-cols-2 gap-2/);
  assert.match(quickActions, /分析数据结构/);
  assert.match(quickActions, /生成 SQL 查询/);
  assert.match(quickActions, /诊断任务异常/);
  assert.match(quickActions, /规划数据开发流程/);
  assert.ok(quickActions.includes('iconClass: "bg-[#e77a5b] text-white"'));
  assert.ok(quickActions.includes('iconClass: "bg-[#5e8fe2] text-white"'));
  assert.ok(quickActions.includes('iconClass: "bg-[#d79a38] text-white"'));
  assert.ok(quickActions.includes('iconClass: "bg-[#59a67a] text-white"'));
  assert.match(workspace, /handleQuickAction/);
  assert.match(conversationHeader, /aria-label=\{sidebarOpen \? "收起侧边栏" : "展开侧边栏"\}/);
  assert.doesNotMatch(workspace, /workspacePanelOpen|AgentWorkspacePanel|PanelRight|FolderOpen|工作区面板|暂无工作区文件/);
});

test("Data Agent empty state renders scoped, outward-only ambient waves", () => {
  assert.match(emptyState, /import "\.\/data-agent-empty-state\.css";/);
  assert.match(emptyState, /data-agent-empty-state relative isolate/);
  assert.match(emptyState, /data-agent-empty-state__wave/);
  assert.match(emptyState, /data-agent-empty-state__content/);
  assert.match(emptyStateStyles, /--yak-ops-data-agent-wave-duration:\s*5\.6s/);
  assert.match(emptyStateStyles, /animation-delay:\s*calc\(var\(--yak-ops-data-agent-wave-duration\) \/ -2\)/);
  assert.match(emptyStateStyles, /@keyframes yak-ops-data-agent-wave-out/);
  assert.match(emptyStateStyles, /72%/);
  assert.match(emptyStateStyles, /prefers-reduced-motion:\s*reduce/);
  assert.doesNotMatch(emptyStateStyles, /main\s*>|:root/);
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
  assert.match(workspace, /setInitialDraft\(""\)/);
  assert.match(composer, /initialValue\?: string/);
  assert.match(composer, /useState\(initialValue\)/);
  assert.doesNotMatch(welcome, /type="file"|上传成功|文件已导入/);
});

test("conversation orchestration is separated from the workspace component", () => {
  assert.match(workspace, /useDataAgentConversation/);
  assert.match(workspace, /<DataAgentSessionWorkspace/);
  assert.match(sessionWorkspace, /<ConversationPanel/);
  assert.match(workspace, /<DataAgentEmptyState/);
  assert.doesNotMatch(workspace, /sendAgentMessage|createAgentSession|listAgentSessions|text_delta|tool_start|confirm_required|patchAssistantMessage/);
  assert.match(conversationHook, /createAgentSession/);
  assert.match(conversationHook, /listAgentSessions/);
  assert.match(conversationHook, /sendAgentMessage/);
  assert.match(conversationHook, /onConfirmRequired/);
  assert.match(conversationHook, /AbortController/);
  assert.match(reducer, /case "TEXT_DELTA"/);
  assert.match(reducer, /case "TOOL_STARTED"/);
  assert.match(reducer, /case "CONFIRMATION_REQUIRED"/);
  assert.match(reducer, /case "MESSAGE_COMPLETED"/);
  assert.match(assistantMessage, /DataAgentMarkdown/);
  assert.match(confirmationCard, /确认执行/);
  assert.match(confirmationCard, /拒绝/);
  assert.match(emptyState, /已恢复服务端历史消息/);
  assert.match(composer, /isSubmitting\?: boolean/);
  assert.match(composer, /onStop\?: \(\) => void/);
  assert.match(composer, /disabled=\{isSubmitting \? !onStop : !value\.trim\(\)\}/);
  assert.match(composer, /停止当前任务/);
  assert.match(conversationHook, /resumeAgentStream/);
  assert.match(conversationHook, /getAgentSessionMessages/);
  assert.match(conversationHook, /interruptAgentSession/);
});

test("assistant message actions are isolated in a reusable footer before the timestamp", () => {
  assert.match(messageItem, /<MessageActions/);
  assert.doesNotMatch(messageItem, /<time/);
  assert.match(messageActions, /formatConversationTime\(message\.createdAt\)/);
  assert.match(messageActions, /<time/);
  assert.match(messageActions, /<RotateCcw/);
  assert.match(messageActions, /<ThumbsUp/);
  assert.match(messageActions, /<ThumbsDown/);
  assert.match(messageActions, /<Copy/);
  assert.match(messageActions, /<Check/);
  assert.match(messageActions, /messageCopied/);
  assert.match(messageActions, /copyIconTransitionClassName/);
  assert.match(messageActions, /duration-300 ease-\[cubic-bezier/);
  assert.match(messageActions, /}, 1500\)/);
  assert.match(messageActions, /window\.clearTimeout/);
  assert.match(messageActions, /<MoreHorizontal/);
  assert.match(messageActions, /navigator\.clipboard\?\.writeText/);
  assert.match(messageActions, /DropdownMenuContent/);
  assert.match(messageActions, /disabled=\{!onRegenerate\}/);
  assert.match(messageActions, /onFeedback\?\.\(message\.id, next\)/);
});

test("the model selector uses the Yak-UI portal menu and opens below the trigger", () => {
  assert.match(modelSelector, /<DropdownMenu open=\{open\} onOpenChange=\{onOpenChange\}>/);
  assert.match(
    modelSelector,
    /<DropdownMenuContent[\s\S]*?side="bottom"[\s\S]*?align="end"[\s\S]*?sideOffset=\{8\}/,
  );
  assert.match(modelSelector, /role="menuitemradio"/);
  assert.match(modelSelector, /aria-checked=\{value === "DeepSeek"\}/);
  assert.doesNotMatch(modelSelector, /absolute bottom-full|bottom-full right-0/);
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
  assert.match(layout, /onOpenAgent=\{\(\) => navigate\("\/data-agent"\)\}/);
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

test("Data Agent restores a URL-addressed session on refresh and shows a full-screen shimmer skeleton", () => {
  assert.match(page, /useParams/);
  assert.match(page, /sessionId=\{sessionId \?\? null\}/);
  assert.match(page, /\/data-agent\/sessions\//);

  assert.match(conversationHook, /restoredIdentityKey/);
  assert.match(conversationHook, /isLoading:[\s\S]*?isRestoringSession/);
  assert.match(conversationHook, /getAgentSessionStatus/);
  assert.match(conversationHook, /getAgentSessionMessages/);
  assert.match(workspace, /isLoadingOverlayVisible \? \([\s\S]*?<DataAgentLoading/);
  assert.match(workspace, /setLoadingOverlayExiting\(true\)/);
  assert.match(workspace, /data-agent-workspace-enter/);
  assert.match(workspace, /showLoadingOverlay/);
  assert.match(workspace, /window\.setTimeout\(\(\) => setShowLoadingOverlay\(false\), 200\)/);
  assert.match(workspace, /element\.inert = isLoadingOverlayVisible/);

  assert.match(loading, /fixed inset-0 z-\[100\]/);
  assert.match(workspace, /exiting=\{loadingOverlayExiting && !isLoading\}/);
  assert.match(loading, /aria-hidden=\{exiting\}/);
  assert.match(loading, /transition-opacity duration-200/);
  assert.match(loading, /role="status"/);
  assert.match(loading, /aria-busy="true"/);
  assert.match(loading, /max-w-\[494px\]/);
  assert.match(loading, /grid grid-cols-2 gap-2/);
  assert.match(loadingStyles, /@keyframes data-agent-skeleton-shimmer/);
  assert.match(loadingStyles, /1\.55s ease-in-out infinite/);
  assert.match(loadingStyles, /prefers-reduced-motion: reduce/);
  assert.match(loadingStyles, /@keyframes data-agent-workspace-enter/);
});

test("Data Agent workspace menu groups its label with menu items", () => {
  const groupStart = sidebar.indexOf("<DropdownMenuGroup>");
  const labelStart = sidebar.indexOf("<DropdownMenuLabel");
  const labelEnd = sidebar.indexOf("</DropdownMenuLabel>");
  const groupEnd = sidebar.indexOf("</DropdownMenuGroup>");
  const workspaceItems = sidebar.indexOf("workspaces.map", groupStart);

  assert.ok(groupStart >= 0, "workspace menu should render a DropdownMenuGroup");
  assert.ok(groupStart < labelStart, "menu group should start before its label");
  assert.ok(labelStart < labelEnd, "menu label should be present");
  assert.ok(labelEnd < workspaceItems, "workspace items should follow the label");
  assert.ok(workspaceItems < groupEnd, "workspace items should remain inside the group");
});


test("Data Agent renders assistant activity inline and keeps execution details in the shared side panel", () => {
  assert.match(assistantMessage, /AgentActivityTimeline/);
  assert.match(assistantMessage, /activities\.some\(\(activity\) => activity\.kind !== "text"\)/);
  assert.doesNotMatch(assistantMessage, /usage=\{message\.usage\}|replyEnds=\{message\.replyEnds\}|isError=/);
  assert.match(activityTimeline, /AgentActivityItem/);
  assert.match(activityTimeline, /isStreaming/);
  assert.doesNotMatch(activityTimeline, /执行过程|aria-expanded=\{expanded\}|Token 用量|ChevronDown|rounded-xl border|bg-\[#fbfcfb\]/);
  assert.match(activityItem, /DataAgentMarkdown/);
  assert.match(activityItem, /AgentToolActivity/);
  assert.doesNotMatch(toolActivity, /AgentActivityDetails/);
  assert.match(toolActivity, /<button/);
  assert.match(toolActivity, /ChevronRight/);
  assert.match(toolActivity, /aria-controls="agent-details-panel"/);
  assert.match(toolActivity, /aria-expanded=\{selected\}/);
  assert.match(toolActivity, /hover:bg-\[#e5e5e5\]/);
  assert.match(detailsTimeline, /hover:bg-\[#f4f5f5\]/);
  assert.match(detailsTimeline, /active:bg-\[#eceeee\]/);
  assert.doesNotMatch(detailsTimeline, /hover:bg-\[#e5e5e5\]/);
  assert.doesNotMatch(toolActivity, /hover:bg-\[#f4f6f5\]/);
  assert.doesNotMatch(detailsTimeline, /hover:bg-\[#f8f9f8\]/);
  assert.match(conversationPanel, /<MessageList/);
  assert.match(conversationPanel, /selectedMessageId/);
  assert.match(conversationPanel, /selectedActivityId/);
  assert.match(conversationPanel, /onSelectActivity/);
  assert.doesNotMatch(conversationPanel, /AgentDetailsPanel|agent-details-panel|AgentDetailsResizer/);
  assert.match(sessionWorkspace, /AgentDetailsPanel/);
  assert.match(sessionWorkspace, /id="agent-details-panel"/);
  assert.match(sessionWorkspace, /handleSelectActivity/);
  assert.match(sessionWorkspace, /duration-300/);
  assert.match(sessionWorkspace, /max-lg:absolute max-lg:inset-0/);
  assert.match(detailsPanel, /AgentDetailsTimeline/);
  assert.doesNotMatch(detailsPanel, /AgentDetailsSummary|执行概览|Token 用量|inputTokens|outputTokens|cacheTokens/);
  assert.ok(sessionWorkspace.includes("metricsMessage={selectedMessage}"));
  assert.ok(conversationHeader.includes("getMessageMetrics"));
  assert.ok(conversationHeader.includes("formatDuration"));
  assert.ok(conversationHeader.includes("totalTokens = inputTokens + outputTokens + cacheTokens"));
  assert.ok(conversationHeader.includes("<Coins size={12}"));
  assert.ok(conversationHeader.includes("Token {totalTokens.toLocaleString()}"));
  assert.doesNotMatch(detailsPanel, /aria-label="关闭执行详情"|AgentActivityDetails|Agent 执行过程/);
  assert.match(detailsTimeline, /Thought/);
  assert.match(detailsTimeline, /SquareTerminal/);
  assert.match(detailsTimeline, /pl-8/);
  assert.match(detailsTimeline, /left-\[15px\]/);
  assert.match(detailsTimeline, /w-\[2px\]/);
  assert.match(detailsTimeline, /bottom-0 left-\[15px\] top-0/);
  assert.match(detailsTimeline, /bg-\[#e7e9e8\]/);
  assert.match(detailsTimeline, /function TimelineNode/);
  assert.match(detailsTimeline, /size=\{12\}/);
  assert.match(detailsTimeline, /text-\[#9fa6a3\]/);
  assert.doesNotMatch(detailsTimeline, /rounded-full bg-\[#c9cfcc\]/);
  assert.match(detailsTimeline, /aria-expanded=\{expanded\}/);
  assert.match(detailsTimeline, /DataAgentMarkdown/);
  assert.match(detailsTimeline, /输入参数/);
  assert.match(detailsTimeline, /执行结果/);
  assert.match(detailsTimeline, /selectedActivityId/);
  assert.match(detailsTimeline, /expandedActivityIds/);
  assert.match(detailsTimeline, /new Set\(selectedActivityId \? \[selectedActivityId\] : \[\]\)/);
  assert.match(detailsTimeline, /activities\.filter/);
  assert.match(detailsTimeline, /expandAll/);
  assert.match(detailsTimeline, /TOOL_LABELS/);
  assert.match(conversationHook, /case "thinking_delta":[\s\S]*Raw model reasoning is intentionally not persisted or rendered/);
  assert.match(conversationHook, /case "tool_call_delta"/);
  assert.match(conversationHook, /case "tool_result_delta"/);
  assert.match(conversationHook, /case "usage"/);
  assert.match(conversationHook, /case "reply_end"/);
  assert.match(reducer, /case "PROGRESS_UPDATED"/);
  assert.match(reducer, /case "USAGE_RECORDED"/);
  assert.match(reducer, /case "REPLY_ENDED"/);
});


test("tool activity is a single-line action that opens the message execution details", () => {
  assert.match(activityTimeline, /onSelectActivity/);
  assert.match(activityItem, /selectedActivityId/);
  assert.match(activityItem, /onSelect=\{onSelectActivity\}/);
  assert.match(toolActivity, /group-hover:translate-x-0 group-hover:opacity-100/);
  assert.match(toolActivity, /查看 .* 执行详情/);
  assert.match(conversationPanel, /selectedMessage/);
  assert.match(conversationPanel, /selectedActivity/);
  assert.match(sessionWorkspace, /event\.key === "Escape"/);
  assert.match(detailsPanel, /message\.activities/);
  assert.match(detailsPanel, /AgentDetailsTimeline/);
  assert.match(detailsTimeline, /activity\.kind === "text"/);
});


test("Data Agent execution details panel has an accessible, bounded drag splitter", () => {
  assert.match(sessionWorkspace, /const \[detailsWidth, setDetailsWidth\] = useState\(420\)/);
  assert.match(sessionWorkspace, /const \[detailsMaxWidth, setDetailsMaxWidth\] = useState\(DETAILS_MAX_WIDTH\)/);
  assert.match(sessionWorkspace, /new ResizeObserver\(updateWidth\)/);
  assert.match(sessionWorkspace, /Math\.floor\(availableWidth \* 0\.55\)/);
  assert.match(sessionWorkspace, /Math\.round\(availableWidth \* 0\.4\)/);
  assert.match(sessionWorkspace, /<AgentDetailsResizer/);
  assert.match(sessionWorkspace, /onResize=\{setDetailsWidth\}/);
  assert.match(sessionWorkspace, /onDraggingChange=\{setDetailsResizing\}/);
  assert.match(sessionWorkspace, /detailsResizing/);
  assert.match(sessionWorkspace, /style=\{\{ width: detailsOpen \? String\(detailsWidth\) \+ "px" : "0px" \}\}/);
  assert.match(sessionWorkspace, /max-lg:!w-full/);

  assert.match(detailsResizer, /DETAILS_MIN_WIDTH = 320/);
  assert.match(detailsResizer, /role="separator"/);
  assert.match(detailsResizer, /aria-label="调整 Agent 详情面板宽度"/);
  assert.match(detailsResizer, /aria-orientation="vertical"/);
  assert.match(detailsResizer, /aria-valuemin=\{DETAILS_MIN_WIDTH\}/);
  assert.match(detailsResizer, /aria-valuemax=\{maxWidth\}/);
  assert.match(detailsResizer, /aria-valuenow=\{Math\.round\(width\)\}/);
  assert.match(detailsResizer, /setPointerCapture/);
  assert.match(detailsResizer, /hasPointerCapture/);
  assert.match(detailsResizer, /onLostPointerCapture/);
  assert.match(detailsResizer, /startWidthRef\.current - \(event\.clientX - startXRef\.current\)/);
  assert.match(detailsResizer, /ArrowLeft/);
  assert.match(detailsResizer, /ArrowRight/);
  assert.match(detailsResizer, /document\.body\.style\.cursor = "col-resize"/);
  assert.match(detailsResizer, /document\.body\.style\.userSelect = "none"/);
});



test("Data Agent clickable controls use pointer cursors and drag splitters keep resize cursors", () => {
  assert.match(detailsTimeline, /cursor-pointer items-start/);
  assert.match(toolActivity, /cursor-pointer items-center/);
  assert.match(modelSelector, /min-h-\[56px\] cursor-pointer/);
  assert.match(sidebarNavigation, /group flex h-8 w-full cursor-pointer/);
  assert.match(sidebarNavigation, /h-8 w-full cursor-pointer items-center/);
  assert.match(sessionWorkspace, /cursor-pointer/);
  assert.match(messageActions, /cursor-pointer/);
  assert.match(confirmationCard, /cursor-pointer/);
  assert.match(composer, /cursor-pointer/);
  assert.match(welcome, /cursor-pointer/);
  assert.match(emptyState, /cursor-pointer/);
  assert.match(conversationHeader, /cursor-pointer/);
  assert.match(detailsResizer, /cursor-col-resize/);
  assert.match(sidebarResizer, /cursor-col-resize/);
  assert.match(composer, /disabled:cursor-not-allowed/);
});

test("Data Agent session workspace uses a shared header and a 60/40 split with a responsive details pane", () => {
  assert.match(workspace, /<DataAgentSessionWorkspace/);
  assert.match(sessionWorkspace, /<DataAgentConversationHeader/);
  assert.match(sessionWorkspace, /const \[detailsOpen, setDetailsOpen\] = useState\(false\)/);
  assert.match(sessionWorkspace, /Math\.round\(availableWidth \* 0\.4\)/);
  assert.match(sessionWorkspace, /DETAILS_MAX_WIDTH = 640/);
  assert.match(sessionWorkspace, /DetailsEmptyState/);
  assert.match(sessionWorkspace, /const \[detailsExpandAll, setDetailsExpandAll\] = useState\(false\)/);
  assert.match(sessionWorkspace, /container\.scrollTo\(\{ top: container\.scrollHeight, behavior: "smooth" \}\)/);
  assert.match(sessionWorkspace, /onToggleExpandAll=\{\(\) => setDetailsExpandAll/);
  assert.match(sessionWorkspace, /expandAll=\{detailsExpandAll\}/);
  assert.match(sessionWorkspace, /max-lg:absolute max-lg:inset-0 max-lg:z-20/);
  assert.match(conversationHeader, /h-12/);
  assert.doesNotMatch(conversationHeader, /border-l|border-b/);
  assert.match(conversationHeader, /Timeline/);
  assert.match(conversationHeader, /Changes/);
  assert.match(conversationHeader, /aria-expanded=\{detailsOpen\}/);
  assert.match(conversationHeader, /aria-label="打开执行详情"/);
  assert.match(conversationHeader, /aria-label="收起执行详情"/);
  assert.match(conversationHeader, /<PanelRightClose size=\{16\}/);
  assert.match(conversationHeader, /style=\{\{ right: String\(detailsWidth \+ 12\)/);
  assert.doesNotMatch(conversationHeader, /aria-label="关闭执行详情"|<X size=\{16\}/);
  assert.match(conversationHeader, /relative mr-2 hidden h-full shrink-0 items-center justify-between gap-2 border-y/);
  assert.match(conversationHeader, /Back to latest/);
  assert.doesNotMatch(conversationHeader, /PREVIEW_DURATION|PREVIEW_SCORE|Cloud/);
  assert.match(conversationHeader, /metricsMessage: AgentMessage \\| null/);
  assert.match(conversationHeader, /pointer-events-none absolute inset-0[\s\S]*?Details/);
  assert.match(conversationHeader, /aria-label=\{expandAll \? "Collapse all" : "Expand all"\}/);
  assert.match(conversationHeader, /role="tablist"/);
  assert.match(conversationHeader, /role="tab"/);
  assert.match(conversationHeader, /onBackToLatest/);
  assert.match(conversationHeader, /onToggleExpandAll/);
  assert.match(sessionWorkspace, /p-2 opacity-100 max-lg:absolute/);
  assert.match(sessionWorkspace, /rounded-\[14px\] border border-\[#e5e5e5\] bg-white/);
  assert.match(sessionWorkspace, /<PanelRightClose size=\{15\}/);
  assert.match(sessionWorkspace, /aria-label="收起执行详情"/);
  assert.doesNotMatch(sessionWorkspace, /border-l border-\[#ecefed\]/);
  assert.match(conversationPanel, /<MessageList/);
  assert.match(conversationPanel, /DataAgentPromptComposer/);
  assert.doesNotMatch(conversationPanel, /AgentDetailsPanel|AgentDetailsResizer/);
});

test("Data Agent details timeline uses a centered continuous 2px rail without dot markers", () => {
  assert.match(detailsTimeline, /relative min-w-0 pl-8/);
  assert.match(detailsTimeline, /left-\[15px\] top-0 w-\[2px\] bg-\[#e7e9e8\]/);
  assert.match(detailsTimeline, /absolute -left-8 top-\[10px\] flex h-5 w-8 items-center justify-center/);
  assert.match(detailsTimeline, /SquareTerminal[\s\S]*?size=\{12\}/);
  assert.doesNotMatch(detailsTimeline, /size-1\.5 rounded-full|size-1\.5 rounded-full bg-/);
});


test("completed activity statuses use icon-only indicators and readable typography", () => {
  const thinkingIndicator = read("../apps/web/app/data-agent/components/workspace/conversation/activity/AgentThinkingIndicator.tsx");
  const toolCallCard = read("../apps/web/app/data-agent/components/workspace/conversation/ToolCallCard.tsx");

  assert.match(thinkingIndicator, /activity\.status === "completed" \? null : activityStatusLabel\(activity\.status\)/);
  assert.match(thinkingIndicator, /text-sm font-medium/);
  assert.match(thinkingIndicator, /text-xs tabular-nums/);
  assert.match(toolActivity, /status === "completed" \? null : toolStatusLabel\(status\)/);
  assert.match(toolActivity, /text-sm font-medium/);
  assert.match(toolActivity, /text-xs tabular-nums/);
  assert.match(toolCallCard, /toolCall\.status === "completed" \? null : toolStatusLabel\(toolCall\.status\)/);
  assert.match(toolCallCard, /text-sm font-medium/);
  assert.match(toolCallCard, /text-xs text-\[#8b9693\]/);
});

test("Data Agent session actions use a right-opening Yak-UI menu outside the scroll container", () => {
  assert.match(conversationList, /<DropdownMenu\\s/);
  assert.match(conversationList, /<DropdownMenuTrigger/);
  assert.match(conversationList, /<DropdownMenuContent[\\s\\S]*?side="right"/);
  assert.match(conversationList, /align="start"/);
  assert.match(conversationList, /sideOffset=\\{8\\}/);
  assert.match(conversationList, /<DropdownMenuSeparator/);
  assert.match(conversationList, /tone="danger"/);
  assert.match(conversationList, /shadow-\\[0_8px_24px_rgba\\(0,0,0,0\\.12\\)\\]/);
  assert.doesNotMatch(conversationList, /absolute right-1 top-8 z-30/);
  assert.doesNotMatch(conversationList, /role="menuitem"/);
});


import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

const contextTypes = read("../apps/web/service/data-agent/types.ts");
const stream = read("../apps/web/service/data-agent/stream.ts");
const hook = read("../apps/web/hooks/useDataAgentConversation.ts");
const workspace = read("../apps/web/app/data-agent/components/DataAgentWorkspace.tsx");
const sidebar = read("../apps/web/app/data-agent/components/DataAgentSidebar.tsx");
const conversationList = read("../apps/web/app/data-agent/components/sidebar/DataAgentConversationList.tsx");
const contextUsage = read("../apps/web/app/data-agent/components/workspace/DataAgentContextUsage.tsx");

test("Data Agent context usage uses the PR #1906 context contract and live token updates", () => {
  assert.ok(contextTypes.includes("interface AgentSessionContext"));
  assert.ok(contextTypes.includes("usedTokens: number"));
  assert.ok(contextTypes.includes("limitTokens: number"));
  assert.ok(contextTypes.includes("averageCacheHitRate: number"));
  assert.ok(contextTypes.includes("breakdown: AgentSessionContextBreakdown[]"));
  assert.ok(stream.includes("contextTokens: numericValue(payload.contextTokens)"));
  assert.ok(hook.includes("getAgentSessionStatus(targetSessionId, requestIdentity, signal)"));
  assert.ok(hook.includes("loadSessionContext"));
  assert.ok(hook.includes("event.contextTokens"));
  assert.ok(workspace.includes("loadSessionContext(activeConversationId, controller.signal)"));
  assert.ok(workspace.includes("activeSessionContextTokens"));
});

test("Context usage indicator sits beside the model selector, not in the session list", () => {
  assert.ok(workspace.includes("<DataAgentContextUsage"));
  assert.ok(workspace.includes("{modelSelector}"));
  assert.ok(workspace.includes("trailingActions={composerActions}"));
  assert.ok(!workspace.includes("onLoadSessionContext={loadSessionContext}"));
  assert.ok(!sidebar.includes("onLoadSessionContext"));
  assert.ok(!conversationList.includes("DataAgentSessionContextCard"));
  assert.ok(!conversationList.includes("onMouseEnter="));
  assert.ok(!conversationList.includes("onFocusCapture="));
  assert.ok(!conversationList.includes("contextCardPosition"));
  assert.ok(!conversationList.includes("loadContextPreview"));
  assert.ok(!conversationList.includes("createPortal"));
  assert.ok(conversationList.includes("onSelectConversation(conversation.id)"));
});

test("Context usage indicator has a segmented progress ring, hover summary and click details", () => {
  assert.ok(contextUsage.includes("conic-gradient"));
  assert.ok(contextUsage.includes("onMouseEnter={showTooltip}"));
  assert.ok(contextUsage.includes("onClick={toggleDetails}"));
  assert.ok(contextUsage.includes('role="tooltip"'));
  assert.ok(contextUsage.includes('role="dialog"'));
  assert.ok(contextUsage.includes("上下文已使用"));
  assert.ok(contextUsage.includes("bg-white"));
  assert.ok(contextUsage.includes("平均缓存命中率"));
});

test("Context categories use the reference palette while keeping the backend breakdown keys", () => {
  assert.ok(contextUsage.includes('systemPrompt: "#625BFF"'));
  assert.ok(contextUsage.includes('tools: "#10B981"'));
  assert.ok(contextUsage.includes('messages: "#F59E0B"'));
  assert.ok(contextUsage.includes('skills: "#EC4899"'));
  assert.ok(contextUsage.includes("item.label"));
  assert.ok(contextUsage.includes("item.ratio"));
});

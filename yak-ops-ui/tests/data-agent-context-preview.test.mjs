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
  assert.match(contextTypes, /interface AgentSessionContext/);
  assert.match(contextTypes, /usedTokens: number/);
  assert.match(contextTypes, /limitTokens: number/);
  assert.match(contextTypes, /averageCacheHitRate: number/);
  assert.match(contextTypes, /breakdown: AgentSessionContextBreakdown\\[\\]/);
  assert.match(stream, /contextTokens: numericValue\\(payload\\.contextTokens\\)/);
  assert.match(hook, /getAgentSessionStatus\\(targetSessionId, requestIdentity, signal\\)/);
  assert.match(hook, /loadSessionContext/);
  assert.match(hook, /event\\.contextTokens/);
  assert.match(workspace, /loadSessionContext\\(activeConversationId, controller\\.signal\\)/);
  assert.match(workspace, /activeSessionContextTokens/);
});

test("Context usage indicator sits beside the model selector, not in the session list", () => {
  assert.match(workspace, /<DataAgentContextUsage/);
  assert.match(workspace, /\\{modelSelector\\}/);
  assert.match(workspace, /trailingActions=\\{composerActions\\}/);
  assert.doesNotMatch(workspace, /onLoadSessionContext=\\{loadSessionContext\\}/);
  assert.doesNotMatch(sidebar, /onLoadSessionContext/);
  assert.doesNotMatch(conversationList, /DataAgentSessionContextCard|onMouseEnter=|onFocusCapture=/);
  assert.doesNotMatch(conversationList, /contextCardPosition|loadContextPreview|createPortal/);
  assert.match(conversationList, /onSelectConversation\\(conversation\\.id\\)/);
});

test("Context usage indicator has a segmented progress ring, hover summary and click details", () => {
  assert.match(contextUsage, /conic-gradient/);
  assert.match(contextUsage, /onMouseEnter=\\{showTooltip\\}/);
  assert.match(contextUsage, /onClick=\\{toggleDetails\\}/);
  assert.match(contextUsage, /role="tooltip"/);
  assert.match(contextUsage, /role="dialog"/);
  assert.match(contextUsage, /上下文已使用/);
  assert.match(contextUsage, /bg-white/);
  assert.match(contextUsage, /平均缓存命中率/);
});

test("Context categories use the reference palette while keeping the backend breakdown keys", () => {
  assert.match(contextUsage, /systemPrompt: "#625BFF"/);
  assert.match(contextUsage, /tools: "#10B981"/);
  assert.match(contextUsage, /messages: "#F59E0B"/);
  assert.match(contextUsage, /skills: "#EC4899"/);
  assert.match(contextUsage, /item\\.label/);
  assert.match(contextUsage, /item\\.ratio/);
});

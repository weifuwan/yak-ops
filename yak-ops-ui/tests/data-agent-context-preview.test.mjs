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
const contextCard = read("../apps/web/app/data-agent/components/sidebar/DataAgentSessionContextCard.tsx");

test("Data Agent session context preview uses the PR #1906 context contract", () => {
  assert.match(contextTypes, /interface AgentSessionContext/);
  assert.match(contextTypes, /usedTokens: number/);
  assert.match(contextTypes, /limitTokens: number/);
  assert.match(contextTypes, /averageCacheHitRate: number/);
  assert.match(contextTypes, /breakdown: AgentSessionContextBreakdown\[\]/);
  assert.match(stream, /contextTokens: numericValue\(payload\.contextTokens\)/);
  assert.match(hook, /getAgentSessionStatus\(targetSessionId, requestIdentity, signal\)/);
  assert.match(hook, /loadSessionContext/);
  assert.match(hook, /event\.contextTokens/);
});

test("Data Agent conversation rows open a right-side context card on hover without changing selection", () => {
  assert.match(workspace, /onLoadSessionContext=\{loadSessionContext\}/);
  assert.match(sidebar, /onLoadSessionContext/);
  assert.match(conversationList, /onMouseEnter=\{\(event\) => openContextPreview/);
  assert.match(conversationList, /createPortal\(/);
  assert.match(conversationList, /onSelectConversation\(conversation\.id\)/);
  assert.match(conversationList, /side="right"/);
  assert.match(conversationList, /contextRequestRef\.current\?\.abort\(\)/);
});

test("Context card shows capacity, category breakdown and average cache hit rate", () => {
  assert.match(contextCard, /上下文容量/);
  assert.match(contextCard, /context\.usedTokens/);
  assert.match(contextCard, /context\.limitTokens/);
  assert.match(contextCard, /context\.breakdown\.map/);
  assert.match(contextCard, /平均缓存命中率/);
  assert.match(contextCard, /context\.averageCacheHitRate/);
  assert.match(contextCard, /bg-\[#242424\]/);
});

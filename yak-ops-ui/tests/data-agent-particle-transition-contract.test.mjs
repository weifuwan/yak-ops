import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (path) =>
  readFileSync(new URL(path, import.meta.url), "utf8").replace(/\r\n/g, "\n");

const page = read("../apps/web/app/data-agent/page.tsx");
const workspace = read("../apps/web/app/data-agent/components/DataAgentWorkspace.tsx");
const promptComposer = read("../apps/web/app/data-agent/components/DataAgentPromptComposer.tsx");
const emptyState = read("../apps/web/app/data-agent/components/workspace/DataAgentEmptyState.tsx");
const conversationPanel = read("../apps/web/app/data-agent/components/workspace/ConversationPanel.tsx");
const sessionWorkspace = read("../apps/web/app/data-agent/components/workspace/DataAgentSessionWorkspace.tsx");
const conversationHook = read("../apps/web/hooks/useDataAgentConversation.ts");
const particle = read("../apps/web/app/data-agent/components/MagicPatternsParticleTransition.tsx");
const particleStyles = read("../apps/web/app/data-agent/components/magic-patterns-particle-transition.css");
const loginPanel = read("../apps/web/app/login/LoginPanel.tsx");
const loginPage = read("../apps/web/app/login/index.tsx");

test("particle transition is enabled for a fresh new conversation, not existing sessions", () => {
  assert.match(page, /enableInitialParticleTransition=\{!sessionId\}/);
  assert.doesNotMatch(page, /playInitialParticleTransition/);
  assert.match(workspace, /enableInitialParticleTransition\?: boolean/);
  assert.match(workspace, /useState\(enableInitialParticleTransition\)/);
  assert.match(workspace, /\{showInitialParticleTransition \? \(/);
  assert.match(workspace, /<MagicPatternsParticleTransition/);
  assert.match(workspace, /fromRef=\{particleSourceRef\}/);
  assert.match(workspace, /toRef=\{particleTargetRef\}/);
  assert.match(workspace, /ref=\{particleTargetRef\}/);
  assert.match(particleStyles, /position: fixed/);
  assert.match(particleStyles, /pointer-events: none/);
});
test("only the welcome composer provides a source for the particle transition", () => {
  assert.match(promptComposer, /transitionSourceRef\?: RefObject<HTMLDivElement>/);
  assert.match(promptComposer, /<div ref=\{transitionSourceRef\} className="group\/composer/);
  assert.match(emptyState, /transitionSourceRef\?: RefObject<HTMLDivElement>/);
  assert.match(emptyState, /transitionSourceRef=\{transitionSourceRef\}/);
  assert.doesNotMatch(conversationPanel, /transitionSourceRef/);
  assert.doesNotMatch(sessionWorkspace, /transitionSourceRef/);
  assert.equal((workspace.match(/transitionSourceRef=\{\s*showInitialParticleTransition \? particleSourceRef : undefined\s*\}/g) ?? []).length, 1);
});
test("the particle animation runs once after the first welcome prompt is accepted", () => {
  const submitStart = workspace.indexOf("function handlePromptSubmit");
  const submitEnd = workspace.indexOf("function handleQuickAction", submitStart);
  const submitHandler = workspace.slice(submitStart, submitEnd);
  const acceptedIndex = conversationHook.indexOf("onAccepted?.()");
  const identityCheckIndex = conversationHook.indexOf("if (!requestIdentity)");
  const sessionCheckIndex = conversationHook.indexOf("if (sendingRef.current && !resolvedSessionId)");
  const sendStateIndex = conversationHook.indexOf("activeSendCountRef.current += 1", acceptedIndex);

  assert.match(submitHandler, /sendMessage\(content, \(\) =>/);
  assert.match(submitHandler, /initialParticleTransitionPendingRef\.current/);
  assert.match(submitHandler, /if \(!shouldPlayInitialParticleTransition\) return/);
  assert.match(submitHandler, /particleTransitionRef\.current\?\.play\(\)/);
  assert.match(submitHandler, /setShowInitialParticleTransition\(false\)/);
  assert.ok(identityCheckIndex >= 0 && identityCheckIndex < acceptedIndex);
  assert.ok(sessionCheckIndex > identityCheckIndex && sessionCheckIndex < acceptedIndex);
  assert.ok(acceptedIndex < sendStateIndex);
  assert.match(conversationHook, /onAccepted\?: \(\) => void/);
  assert.match(conversationHook, /Visual effects are optional and must never block message delivery/);
});

test("conversation panel shows a subtle top blur only after scrolling away from the top", () => {
  assert.match(conversationPanel, /const \[showTopFade, setShowTopFade\] = useState\(false\)/);
  assert.match(conversationPanel, /setShowTopFade\(container\.scrollTop > 4\)/);
  assert.match(conversationPanel, /data-testid="conversation-top-fade"/);
  assert.match(conversationPanel, /h-\[72px\]/);
  assert.match(conversationPanel, /transition-opacity duration-200/);
  assert.match(conversationPanel, /backdropFilter: "blur\(8px\)"/);
  assert.match(conversationPanel, /WebkitBackdropFilter: "blur\(8px\)"/);
  assert.match(conversationPanel, /pointer-events-none/);
  assert.match(conversationPanel, /maskImage:/);
  assert.match(conversationPanel, /WebkitMaskImage:/);
});

test("creating a new conversation rearms the transition while selecting an existing session disables it", () => {
  const newStart = workspace.indexOf("function handleNewConversation");
  const selectStart = workspace.indexOf("function handleSelectConversation", newStart);
  const submitStart = workspace.indexOf("function handlePromptSubmit", selectStart);
  const newHandler = workspace.slice(newStart, selectStart);
  const selectHandler = workspace.slice(selectStart, submitStart);

  assert.match(newHandler, /particleTransitionRef\.current\?\.cancel\(\)/);
  assert.match(newHandler, /initialParticleTransitionPendingRef\.current = true/);
  assert.match(newHandler, /setShowInitialParticleTransition\(true\)/);

  assert.match(selectHandler, /particleTransitionRef\.current\?\.cancel\(\)/);
  assert.match(selectHandler, /initialParticleTransitionPendingRef\.current = false/);
  assert.match(selectHandler, /setShowInitialParticleTransition\(false\)/);
});
test("the original Magic Patterns Canvas choreography and mask sweep are retained", () => {
  assert.match(particle, /PARTICLE_COUNT = 220/);
  assert.match(particle, /PARTICLE_BURST_DURATION_MS = 1300/);
  assert.match(particle, /PARTICLE_FLIGHT_MIN_MS = 600/);
  assert.match(particle, /PARTICLE_FLIGHT_MAX_MS = 950/);
  assert.ok(
    particle.includes('[ "#7cd27f"') ||
      particle.includes('["#7cd27f", "#51b6f8", "#a390cb", "#f3a6d8", "#ffd27a"]'),
  );
  assert.match(particle, /linear-gradient\(90deg, transparent 0%, transparent 42%, #000 52%, #000 100%\)/);
  assert.match(particle, /Math\.acos\(1 - 2 \* birthPosition\)/);
  assert.match(particle, /function cubicBezierPoint/);
  assert.match(particle, /context\.ellipse\(/);
  assert.match(particle, /rgba\(0, 0, 0, 0\.24\)/);
});

test("particle coordinates are captured before the first-message composer unmounts", () => {
  assert.match(particle, /const sourceBounds = getSourceBounds\(/);
  assert.match(particle, /drawParticle\(context, particle, elapsed, sourceBounds, destination\)/);
  assert.match(particle, /removeProperty\("-webkit-mask-"/);
  assert.match(particle, /removeProperty\("mask-"/);
  assert.match(particle, /requestAnimationFrame/);
  assert.match(particle, /prefers-reduced-motion: reduce/);
  assert.match(particle, /useEffect\(\(\) => \(\) => cancel\(\)/);
});

test("the login page no longer contains the particle preview or particle transition wiring", () => {
  assert.doesNotMatch(loginPanel, /MagicPatternsParticleTransition|测试动画|handleParticlePreview|previewSourceRef/);
  assert.doesNotMatch(loginPage, /particleTargetRef|login-particle-destination|MagicPatternsParticleTransition/);
});

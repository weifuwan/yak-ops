import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const read = (path) =>
  readFileSync(new URL(path, import.meta.url), "utf8").replace(/\r\n/g, "\n");

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

test("Magic Patterns particle transition is mounted once at the Data Agent workspace level", () => {
  assert.match(workspace, /<MagicPatternsParticleTransition/);
  assert.match(workspace, /fromRef=\{particleSourceRef\}/);
  assert.match(workspace, /toRef=\{particleTargetRef\}/);
  assert.match(workspace, /ref=\{particleTargetRef\}/);
  assert.match(workspace, /relative flex min-h-0 min-w-0 flex-1 overflow-hidden/);
  assert.match(particleStyles, /position: fixed/);
  assert.match(particleStyles, /pointer-events: none/);
});

test("both the welcome composer and ongoing conversation composer provide the animation source", () => {
  assert.match(promptComposer, /transitionSourceRef\?: RefObject<HTMLDivElement>/);
  assert.match(promptComposer, /<div ref=\{transitionSourceRef\} className="group\/composer/);
  assert.match(emptyState, /transitionSourceRef: RefObject<HTMLDivElement>/);
  assert.match(emptyState, /transitionSourceRef=\{transitionSourceRef\}/);
  assert.match(conversationPanel, /transitionSourceRef: RefObject<HTMLDivElement>/);
  assert.match(conversationPanel, /transitionSourceRef=\{transitionSourceRef\}/);
  assert.match(sessionWorkspace, /transitionSourceRef: RefObject<HTMLDivElement>/);
  assert.match(sessionWorkspace, /transitionSourceRef=\{transitionSourceRef\}/);
  assert.equal((workspace.match(/transitionSourceRef=\{particleSourceRef\}/g) ?? []).length, 2);
});

test("the particle animation starts only after the conversation hook accepts the prompt", () => {
  const submitStart = workspace.indexOf("function handlePromptSubmit");
  const submitEnd = workspace.indexOf("function handleQuickAction", submitStart);
  const submitHandler = workspace.slice(submitStart, submitEnd);
  const acceptedIndex = conversationHook.indexOf("onAccepted?.()");
  const identityCheckIndex = conversationHook.indexOf("if (!requestIdentity)");
  const sessionCheckIndex = conversationHook.indexOf("if (sendingRef.current && !resolvedSessionId)");
  const sendStateIndex = conversationHook.indexOf("activeSendCountRef.current += 1", acceptedIndex);

  assert.match(submitHandler, /sendMessage\(content, \(\) =>/);
  assert.match(submitHandler, /particleTransitionRef\.current\?\.play\(\)/);
  assert.ok(identityCheckIndex >= 0 && identityCheckIndex < acceptedIndex);
  assert.ok(sessionCheckIndex > identityCheckIndex && sessionCheckIndex < acceptedIndex);
  assert.ok(acceptedIndex < sendStateIndex);
  assert.match(conversationHook, /onAccepted\?: \(\) => void/);
  assert.match(conversationHook, /Visual effects are optional and must never block message delivery/);
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

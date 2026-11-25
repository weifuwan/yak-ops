import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const loginPanel = readFileSync(
  new URL("../apps/web/app/login/LoginPanel.tsx", import.meta.url),
  "utf8",
);
const loginPage = readFileSync(
  new URL("../apps/web/app/login/index.tsx", import.meta.url),
  "utf8",
);
const transition = readFileSync(
  new URL("../apps/web/app/login/MagicPatternsParticleTransition.tsx", import.meta.url),
  "utf8",
);
const transitionStyles = readFileSync(
  new URL("../apps/web/app/login/magic-patterns-particle-transition.css", import.meta.url),
  "utf8",
);

test("particle choreography preserves Magic Patterns source parameters", () => {
  assert.match(transition, /PARTICLE_COUNT = 220/);
  assert.match(transition, /PARTICLE_BURST_DURATION_MS = 1300/);
  assert.match(transition, /PARTICLE_FLIGHT_MIN_MS = 600/);
  assert.match(transition, /PARTICLE_FLIGHT_MAX_MS = 950/);
  assert.match(transition, /"#7cd27f", "#51b6f8", "#a390cb", "#f3a6d8", "#ffd27a"/);
  assert.match(transition, /"linear-gradient\(90deg, transparent 0%, transparent 42%, #000 52%, #000 100%\)"/);
  assert.match(transition, /rgba\(0, 0, 0, 0\.24\)/);
  assert.match(transition, /context\.ellipse\(/);
  assert.match(transition, /Math\.acos\(1 - 2 \* birthPosition\)/);
  assert.match(transition, /Math\.pow\(-2 \* progress \+ 2, 3\) \/ 2/);
});

test("the test button launches the particle flow without submitting login", () => {
  const loginHandlerStart = loginPanel.indexOf("const handleAccountLogin");
  const loginHandlerEnd = loginPanel.indexOf("\n  return (", loginHandlerStart);
  const loginHandler = loginPanel.slice(loginHandlerStart, loginHandlerEnd);

  assert.match(loginPanel, /const handleParticlePreview = \(\) =>/);
  assert.match(loginPanel, /onClick=\{handleParticlePreview\}/);
  assert.match(loginPanel, /type="button"/);
  assert.match(loginPanel, /测试动画/);
  assert.match(
    loginPanel,
    /<MagicPatternsParticleTransition[\s\S]*fromRef=\{previewSourceRef\}[\s\S]*toRef=\{particleTargetRef\}/,
  );
  assert.match(loginPanel, /login-form-particle-preview-source mt-2/);
  assert.doesNotMatch(loginHandler, /particleTransitionRef\.current\?\.play\(\)/);
  assert.match(loginHandler, /await login\(\{/);
});

test("the particle destination follows the existing right-side login scene", () => {
  assert.match(loginPage, /className="relative hidden min-h-screen overflow-hidden md:block"/);
  assert.match(loginPage, /ref=\{particleTargetRef\}/);
  assert.match(loginPage, /login-particle-destination/);
  assert.match(loginPage, /particleTargetRef=\{particleTargetRef\}/);
  assert.match(transitionStyles, /position: fixed/);
  assert.match(transitionStyles, /z-index: 110/);
  assert.match(transitionStyles, /pointer-events: none/);
  assert.match(transitionStyles, /width: min\(380px, 100%\)/);
});

test("reduced motion and component cleanup are respected", () => {
  assert.match(transition, /prefers-reduced-motion: reduce/);
  assert.match(transition, /useEffect\(\(\) => \(\) => cancel\(\)/);
  assert.match(transition, /removeProperty\("-webkit-mask-"/);
  assert.match(transition, /removeProperty\("mask-"/);
});

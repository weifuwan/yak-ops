import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";

const loginPanel = readFileSync(
  new URL("../apps/web/app/login/LoginPanel.tsx", import.meta.url),
  "utf8",
);
const auroraComponent = readFileSync(
  new URL("../apps/web/app/login/YakAurora.tsx", import.meta.url),
  "utf8",
);
const auroraStyles = readFileSync(
  new URL("../apps/web/app/login/yak-aurora.css", import.meta.url),
  "utf8",
);

test("YakAurora renders the aurora through Canvas and pulls it in with WebGL2", () => {
  assert.match(auroraComponent, /getContext\("2d"\)/);
  assert.match(auroraComponent, /getContext\("webgl2"/);
  assert.match(auroraComponent, /textureGrad/);
  assert.match(auroraComponent, /requestAnimationFrame/);
});

test("YakAurora respects reduced motion and releases active animation work", () => {
  assert.match(auroraComponent, /prefers-reduced-motion/);
  assert.match(auroraComponent, /removeEventListener\("resize"/);
  assert.match(auroraComponent, /removeEventListener\("visibilitychange"/);
  assert.match(auroraComponent, /useEffect\(\(\) => \(\) => cancel\(\)/);
});

test("a valid login submission pulls the aurora into the login button", () => {
  const validationIndex = loginPanel.indexOf("if (Object.keys(nextErrors).length > 0)");
  const animationIndex = loginPanel.indexOf("auroraRef.current?.play()");
  assert.ok(validationIndex >= 0);
  assert.ok(animationIndex > validationIndex);
  assert.match(loginPanel, /<YakAurora ref=\{auroraRef\} targetRef=\{submitTargetRef\} \/>/);
  const submitTargetPattern = /ref=\{submitTargetRef\} className="login-form-submit-target(?: mt-6)?"/;
  assert.match(loginPanel, submitTargetPattern);
});

test("login failures cancel the visual transition without replacing auth handling", () => {
  assert.match(loginPanel, /auroraRef.current\?\.cancel\(\)/);
  assert.match(loginPanel, /await login\(\{/);
  assert.match(auroraStyles, /pointer-events: none/);
  assert.match(auroraStyles, /prefers-reduced-motion/);
});

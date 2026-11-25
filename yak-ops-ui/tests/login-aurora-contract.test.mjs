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

test("the test button previews the aurora without submitting the login form", () => {
  const loginHandlerStart = loginPanel.indexOf("const handleAccountLogin");
  const loginHandlerEnd = loginPanel.indexOf("\n  return (", loginHandlerStart);
  const loginHandler = loginPanel.slice(loginHandlerStart, loginHandlerEnd);

  assert.match(loginPanel, /const handleAuroraPreview = \(\) =>/);
  assert.match(loginPanel, /onClick=\{handleAuroraPreview\}/);
  assert.match(loginPanel, /type="button"/);
  assert.match(loginPanel, /测试动画/);
  assert.match(
    loginPanel,
    /<YakAurora ref=\{auroraRef\} targetRef=\{previewTargetRef\} \/>/,
  );
  assert.match(loginPanel, /login-form-aurora-test-target mt-2/);
  assert.ok(loginPanel.indexOf('className="login-form-submit"') < loginPanel.indexOf("测试动画"));
  assert.doesNotMatch(loginHandler, /auroraRef\.current\?\.play\(\)/);
});

test("login failures do not cancel or otherwise control the preview animation", () => {
  const loginHandlerStart = loginPanel.indexOf("const handleAccountLogin");
  const loginHandlerEnd = loginPanel.indexOf("\n  return (", loginHandlerStart);
  const loginHandler = loginPanel.slice(loginHandlerStart, loginHandlerEnd);

  assert.match(loginHandler, /await login\(\{/);
  assert.doesNotMatch(loginHandler, /auroraRef\.current\?\.cancel\(\)/);
  assert.match(auroraStyles, /pointer-events: none/);
  assert.match(auroraStyles, /login-form-aurora-test-target/);
  assert.match(auroraStyles, /prefers-reduced-motion/);
});

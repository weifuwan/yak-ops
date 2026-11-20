import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const read = (path) =>
  readFileSync(new URL(path, import.meta.url), "utf8").replace(/\r\n/g, "\n");
const router = read("../apps/web/app/router/AppRouter.tsx");
const welcome = read("../apps/web/app/data-agent/components/DataAgentWelcome.tsx");
const preview = read("../apps/web/app/data-agent/particle-preview/page.tsx");
const particle = read("../apps/web/app/data-agent/components/ParticleTransition.tsx");

test("Particle Lab is an authenticated standalone Data Agent route", () => {
  assert.match(router, /import \{ ParticlePreviewPage \} from "@\/app\/data-agent\/particle-preview\/page"/);
  assert.match(router, /"\/data-agent\/particle-preview"/);
  assert.match(
    router,
    /path="\/data-agent\/particle-preview"[\s\S]*?element=\{<ParticlePreviewPage \/>\}/,
  );
  assert.ok(
    router.includes(
      '\n        </Route>\n\n        <Route\n          path="/data-agent/particle-preview"',
    ),
    "Particle Lab should not depend on the workspace creation gate",
  );
  assert.match(welcome, /to="\/data-agent\/particle-preview"/);
  assert.match(welcome, /粒子动画预览/);
});

test("Particle Lab previews the reusable canvas component without mounting the Data Agent workspace", () => {
  assert.match(preview, /import \{ ParticleTransition \} from "\.\.\/components\/ParticleTransition"/);
  assert.match(preview, /<AppShell productLabel="Particle Lab" productPath="\/data-agent">/);
  assert.match(preview, /<ParticleTransition/);
  assert.match(preview, /key=\{playbackId\}/);
  assert.match(preview, /active=\{isPlaying\}/);
  assert.match(preview, /onComplete=\{\(\) => setIsPlaying\(false\)\}/);
  assert.match(preview, /function replay\(\)/);
  assert.match(preview, /setPlaybackId\(\(current\) => current \+ 1\)/);
  assert.match(preview, /setIsPlaying\(false\)/);
  assert.match(preview, /particleCount=\{particleCount\}/);
  assert.match(preview, /duration=\{duration\}/);
  assert.match(preview, /direction=\{direction\}/);
  assert.match(preview, /origin=\{\{ x: originX \/ 100, y: originY \/ 100 \}\}/);
  assert.doesNotMatch(preview, /DataAgentWorkspace|useDataAgentConversation/);
  assert.match(preview, /aria-pressed=\{direction === "left"\}/);
  assert.match(preview, /aria-pressed=\{direction === "right"\}/);
  assert.match(preview, /type="range"/);
  assert.match(preview, /id="particle-count"/);
  assert.match(preview, /id="particle-duration"/);
  assert.match(preview, /id="particle-origin-x"/);
  assert.match(preview, /id="particle-origin-y"/);
  assert.match(preview, /to="\/data-agent"/);
  assert.match(particle, /requestAnimationFrame/);
});


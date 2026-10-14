import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const read = (path) => readFileSync(new URL("../" + path, import.meta.url), "utf8");
const appTokens = read("apps/web/themes/tokens.css");
const sharedTokens = read("packages/yak-ui/src/styles.css");
const appEntry = read("apps/web/main.tsx");

assert.match(appTokens, /--yak-color-primary:\s*#[0-9a-fA-F]{6};/);
assert.equal(
  [...appTokens.matchAll(/--yak-color-primary\s*:/g)].length,
  1,
  "Product theme has one primary color configuration",
);
assert.match(sharedTokens, /--yak-color-primary:\s*#0033ff;/);
assert.match(
  sharedTokens,
  /--yak-color-primary-subtle:\s*color-mix\(in srgb, var\(--yak-color-primary\) 10%, #fff\);/,
);
assert.match(
  appEntry,
  /import "\.\/app\/styles\/global\.css";\s*import "\.\/themes\/index\.css";/,
  "Product theme is imported after the Yak UI defaults",
);

for (const name of [
  "primary-hover",
  "primary-active",
  "primary-subtle",
  "primary-ring",
  "primary-halo",
]) {
  assert.match(
    sharedTokens,
    new RegExp("--yak-color-" + name + "\\s*:\\s*color-mix\\("),
    name + " derives from the primary color",
  );
}

for (const [component, color] of Object.entries({
  "button-primary-bg": "primary",
  "button-primary-bg-hover": "primary-hover",
  "button-primary-bg-active": "primary-active",
  "button-focus-ring": "primary-ring",
  "input-border-focus": "primary",
  "input-focus-ring": "primary-ring",
  "checkbox-bg-checked": "primary",
  "checkbox-focus-ring": "primary-ring",
  "select-indicator": "primary",
  "switch-bg-checked": "primary",
  "switch-bg-checked-hover": "primary-hover",
  "tab-text-active": "primary",
  "tab-active": "primary",
  "table-focus-ring": "primary-ring",
  "table-sorter-active": "primary",
  "table-filter-active": "primary",
  "pagination-active-border": "primary",
  "pagination-active-text": "primary",
  "toast-info": "primary",
  "toast-info-halo": "primary-halo",
})) {
  assert(
    sharedTokens.includes("--yak-components-" + component + ": var(--yak-color-" + color + ");"),
    component + " must use the shared color token",
  );
}

const sidebar = read("apps/web/app/layout/ProductSidebar.tsx");
const topBar = read("apps/web/app/layout/TopBar.tsx");
const detail = read("apps/web/app/data-sync/task-detail.tsx");
const execution = read("apps/web/app/data-sync/execution-detail.css");
const cron = read("packages/yak-ui/src/cron-scheduler/CronSchedulerPicker.tsx");

assert.match(sidebar, /border-\[var\(--yak-color-primary\)\]/);
assert.match(sidebar, /bg-\[var\(--yak-color-primary-subtle\)\]/);
assert.match(topBar, /text-\[var\(--yak-color-primary\)\]/);
assert.match(detail, /bg-\[var\(--yak-color-primary-subtle\)\]/);
assert.match(execution, /\[data-status="RUNNING"\]\s*\{\s*color: var\(--yak-color-primary\)/);
assert.match(
  execution,
  /\.sync-execution-progress-fill\s*\{[^}]*background: var\(--yak-color-primary\)/,
);
assert.match(cron, /bg-\[var\(--yak-color-primary-subtle\)\]/);
assert.doesNotMatch(execution, /--yak-components-tab-active:/);
assert.doesNotMatch(detail, /sync-execution-tabs/);

// Brand accents derive from the theme; status, vendor, and illustration colors remain independent.
const legacyAccent = /#(?:1544d1|1645d1|dbe3fb|f2f5ff|335dff|2548d6|8798ff|38bde0)\b/i;
for (const [path, content] of [
  ["ProductSidebar.tsx", sidebar],
  ["TopBar.tsx", topBar],
  ["task-detail.tsx", detail],
  ["execution-detail.css", execution],
]) {
  assert.doesNotMatch(content, legacyAccent, path + " retains a fixed brand blue");
}
assert.doesNotMatch(read("apps/web/themes/light.css"), /--yak-color-primary\s*:/);
assert.doesNotMatch(read("apps/web/themes/dark.css"), /--yak-color-primary\s*:/);
console.log("Primary color token ownership and usage checks passed.");

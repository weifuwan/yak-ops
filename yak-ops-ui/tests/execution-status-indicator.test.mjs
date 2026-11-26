import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const root = join(dirname(fileURLToPath(import.meta.url)), "../apps/web/app");
const indicator = readFileSync(join(root, "components/execution-status-indicator.tsx"), "utf8");
const realtimeInstances = readFileSync(
  join(root, "operations/data-sync/instances-page.tsx"),
  "utf8",
);
const executionDetail = readFileSync(
  join(root, "data-sync/execution/execution-detail.tsx"),
  "utf8",
);

const taskStatuses = [
  "SUBMITTED_SUCCESS",
  "DISPATCH",
  "RUNNING_EXECUTION",
  "DELAY_EXECUTION",
  "NEED_FAULT_TOLERANCE",
  "SUCCESS",
  "FORCED_SUCCESS",
  "FAILURE",
  "PAUSE",
  "KILL",
];

const workflowStatuses = [
  "SUBMITTED_SUCCESS",
  "RUNNING_EXECUTION",
  "READY_PAUSE",
  "PAUSE",
  "READY_STOP",
  "STOP",
  "FAILURE",
  "SUCCESS",
  "SERIAL_WAIT",
  "FAILOVER",
];

test("shared execution status indicator covers every task and workflow enum value", () => {
  for (const status of new Set([...taskStatuses, ...workflowStatuses])) {
    assert.match(
      indicator,
      new RegExp(`\\b${status}:\\s*\\{`),
      `missing visual mapping for ${status}`,
    );
  }
});

test("execution status visuals use simple inline geometric SVG shapes", () => {
  assert.match(indicator, /<svg[\s\S]*viewBox="0 0 16 16"/);
  assert.match(indicator, /<circle/);
  assert.match(indicator, /<path/);
  assert.match(indicator, /<rect/);
  assert.match(indicator, /stroke="currentColor"/);
  assert.match(indicator, /fill="currentColor"/);
});

test("realtime instance table uses the shared status indicator and no local status palette", () => {
  assert.match(
    realtimeInstances,
    /<ExecutionStatusIndicator status=\{r\.status\} label=\{instanceStatusText\(r\.status\)\}/,
  );
  assert.doesNotMatch(realtimeInstances, /INSTANCE_STATUS_STYLES|function InstanceStatus/);
});

test("data-sync execution detail reuses the shared status glyph", () => {
  assert.match(executionDetail, /import \{ ExecutionStatusIndicator \} from "@\/app\/components\/execution-status-indicator"/);
  assert.match(executionDetail, /<ExecutionStatusIndicator[\s\S]*showLabel=\{false\}/);
  assert.doesNotMatch(executionDetail, /const STATUS_ICONS/);
});

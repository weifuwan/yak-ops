import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), "../..");
const appRoot = join(repoRoot, "yak-ops-ui/apps/web/app");
const indicator = readFileSync(join(appRoot, "components/execution-status-indicator.tsx"), "utf8");
const realtimeInstances = readFileSync(
  join(appRoot, "operations/data-sync/instances-page.tsx"),
  "utf8",
);
const executionDetail = readFileSync(
  join(appRoot, "data-sync/execution/execution-detail.tsx"),
  "utf8",
);
const taskStatusEnum = readFileSync(
  join(
    repoRoot,
    "yak-ops-task-plugin/yak-ops-task-api/src/main/java/io/yak/ops/plugin/task/api/enums/TaskExecutionStatus.java",
  ),
  "utf8",
);
const workflowStatusEnum = readFileSync(
  join(repoRoot, "yak-ops-common/src/main/java/io/yak/ops/common/enums/WorkflowExecutionStatus.java"),
  "utf8",
);

const enumValues = (source) =>
  [...source.matchAll(/^    ([A-Z][A-Z0-9_]*)\\(/gm)].map((match) => match[1]);

test("shared execution status indicator covers every backend task and workflow enum value", () => {
  const backendStatuses = new Set([
    ...enumValues(taskStatusEnum),
    ...enumValues(workflowStatusEnum),
  ]);

  assert.ok(backendStatuses.size > 0, "backend execution enums should be readable");
  for (const status of backendStatuses) {
    assert.match(
      indicator,
      new RegExp(`\\\\b${status}:\\\\s*\\\\{`),
      `missing visual mapping for backend status ${status}`,
    );
  }
});

test("execution status visuals use simple inline geometric SVG shapes", () => {
  assert.match(indicator, /<svg[\\s\\S]*viewBox="0 0 16 16"/);
  assert.match(indicator, /<circle/);
  assert.match(indicator, /<path/);
  assert.match(indicator, /<rect/);
  assert.match(indicator, /stroke="currentColor"/);
  assert.match(indicator, /fill="currentColor"/);
});

test("realtime instance table uses the shared status indicator and no local status palette", () => {
  assert.match(
    realtimeInstances,
    /<ExecutionStatusIndicator status=\\{r\\.status\\} label=\\{instanceStatusText\\(r\\.status\\)\\}/,
  );
  assert.doesNotMatch(realtimeInstances, /INSTANCE_STATUS_STYLES|function InstanceStatus/);
});

test("data-sync execution detail reuses the shared status glyph", () => {
  assert.match(executionDetail, /import \\{ ExecutionStatusIndicator \\} from "@\\/app\\/components\\/execution-status-indicator"/);
  assert.match(executionDetail, /<ExecutionStatusIndicator[\\s\\S]*showLabel=\\{false\\}/);
  assert.doesNotMatch(executionDetail, /const STATUS_ICONS/);
});

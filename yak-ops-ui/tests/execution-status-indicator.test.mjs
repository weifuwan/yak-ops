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
const yakUiStyles = readFileSync(
  join(repoRoot, "yak-ops-ui/packages/yak-ui/src/styles.css"),
  "utf8",
);

const enumValues = (source) =>
  source
    .split("\n")
    .map((line) => line.trim().match(/^([A-Z][A-Z0-9_]*)\(/)?.[1])
    .filter(Boolean);

test("shared execution status indicator covers every backend task and workflow enum value", () => {
  const backendStatuses = new Set([
    ...enumValues(taskStatusEnum),
    ...enumValues(workflowStatusEnum),
  ]);

  assert.ok(backendStatuses.size > 0, "backend execution enums should be readable");
  for (const status of backendStatuses) {
    assert.ok(
      indicator.includes(status + ": {"),
      "missing visual mapping for backend status " + status,
    );
  }
});

test("failure status icon uses the defined red danger token", () => {
  const failureMapping = indicator.slice(
    indicator.indexOf("FAILURE: {"),
    indicator.indexOf("PAUSE: {"),
  );

  assert.ok(failureMapping.includes('shape: "triangle-alert"'));
  assert.ok(
    failureMapping.includes('colorClassName: "text-[var(--yak-components-danger-text)]"'),
  );
  assert.ok(yakUiStyles.includes("--yak-components-danger-text: #d92d20;"));
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
  assert.ok(
    realtimeInstances.includes(
      '<ExecutionStatusIndicator status={r.status} label={instanceStatusText(r.status)} />',
    ),
  );
  assert.doesNotMatch(realtimeInstances, /INSTANCE_STATUS_STYLES|function InstanceStatus/);
});

test("data-sync execution detail reuses the shared status glyph", () => {
  assert.ok(
    executionDetail.includes(
      'import { ExecutionStatusIndicator } from "@/app/components/execution-status-indicator";',
    ),
  );
  assert.ok(executionDetail.includes("<ExecutionStatusIndicator"));
  assert.ok(executionDetail.includes("showLabel={false}"));
  assert.doesNotMatch(executionDetail, /const STATUS_ICONS/);
});

test("operations instance list uses a switch to control automatic polling", () => {
  assert.match(realtimeInstances, /import \{ ExecutionStatusIndicator \} from "@\/app\/components\/execution-status-indicator"/);
  assert.match(realtimeInstances, /Switch,/);
  assert.match(realtimeInstances, /const \[autoRefresh, setAutoRefresh\] = useState\(true\)/);
  assert.match(realtimeInstances, /refetchInterval: autoRefresh \? refreshSeconds \* 1000 : false/);
  assert.match(realtimeInstances, /<Switch[\s\S]*checked=\{autoRefresh\}[\s\S]*onCheckedChange=\{setAutoRefresh\}/);
  assert.match(realtimeInstances, /自动刷新/);
  assert.doesNotMatch(realtimeInstances, /自动刷新 · 每 \$\{refreshSeconds\} 秒/);
});

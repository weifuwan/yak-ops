import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

import { getExecutionProgress } from "../apps/web/app/data-sync/execution-progress.ts";

assert.deepEqual(getExecutionProgress("FINISHED"), { mode: "success", value: 100 });
assert.deepEqual(getExecutionProgress("FAILED"), { mode: "failure", value: 0 });
assert.deepEqual(getExecutionProgress("RUNNING"), { mode: "running", value: null });
assert.deepEqual(getExecutionProgress("RUNNING", 37), { mode: "progress", value: 37 });
assert.deepEqual(getExecutionProgress("RUNNING", 37, true), { mode: "running", value: null });
assert.deepEqual(getExecutionProgress("FAILED", 37), { mode: "failure", value: 37 });
assert.deepEqual(getExecutionProgress("LAUNCHING"), { mode: "idle", value: 0 });
assert.deepEqual(getExecutionProgress("SUBMITTED"), { mode: "idle", value: 0 });
for (const status of ["CANCELLING", "RECONCILING", "CANCELED"]) {
  assert.deepEqual(getExecutionProgress(status), { mode: "idle", value: null });
}

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const detail = read("../apps/web/app/data-sync/execution-detail.tsx");
const progressBar = read("../apps/web/app/data-sync/execution-progress-bar.tsx");
const taskList = read("../apps/web/app/data-sync/index.tsx");
const router = read("../apps/web/app/router/AppRouter.tsx");
const page = read("../apps/web/app/data-sync/task-detail.tsx");
const css = read("../apps/web/app/data-sync/execution-detail.css");

for (const source of [detail, page]) {
  assert.doesNotMatch(source, /DataSyncExecutionConfigContent|本次运行配置|配置快照/);
}
assert.doesNotMatch(page, /DataSyncTaskStatusBadge|写入方式|调试模式/);
assert.doesNotMatch(detail, /SectionCard|<Badge|失败代码|查看运行日志/);
assert.doesNotMatch(detail, /结构迁移|全量同步|增量同步/);
assert.match(progressBar, /role="progressbar"/);
assert.match(progressBar, /aria-valuenow=\{progress.value \?\? undefined\}/);
assert.match(detail, /<ExecutionProgressBar/);
assert.match(progressBar, /sync-execution-progress-fill/);
for (const list of [taskList]) {
  assert.match(list, /title: "执行概况"/);
  assert.match(list, /latestByTask\.get\(record\.id\)/);
  assert.doesNotMatch(list, /syncEndpointText/);
}
assert.match(detail, /sourceTable: tablePath\(\s*snapshot.source.database/);
assert.match(detail, /targetTable: tablePath\(\s*snapshot.target.database/);
assert.match(detail, /record.readRows/);
assert.match(detail, /record.writeRows/);
assert.doesNotMatch(detail, /readRows\s*\|\|\s*0|writeRows\s*\|\|\s*0/);
assert.match(css, /@media \(prefers-reduced-motion: reduce\)/);
assert.match(css, /animation: none/);
assert.match(page, /!autoRefresh \|\| \(!hasActiveInstance && !hasEnabledSchedule\)/);
assert.match(page, /hasEnabledSchedule,/);
assert.match(page, /window.clearInterval\(timer\)/);
assert.match(page, /request !== detailRequest.current/);
assert.match(page, /<ExecutionStatusIcon\s+status=\{instance.status\}/);
assert.match(page, /key=\{selectedInstance.id\}/);
assert.match(page, /<TaskInstanceLogPanel/);
assert.equal((page.match(/<TabsTab\s/g) || []).length, 2);

assert.match(router, /path="\/data-sync"/);
assert.match(router, /path="\/data-sync\/:id\/edit"/);
assert.match(router, /path="\/data-sync\/:id\/detail"/);
assert.doesNotMatch(router, /from "@\/app\/(offline-sync|realtime-sync)"/);
assert.match(page, /selectedInstance.syncType === "REALTIME"/);
console.log("Unified Data Sync execution progress and presentation checks passed");

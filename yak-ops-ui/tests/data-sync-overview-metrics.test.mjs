import assert from "node:assert/strict";
import { test } from "node:test";
import {
  activePeriod,
  executionMetricValue,
  executionTrend,
  rangeForPeriod,
  sumExecutionCounts,
  validRange,
} from "../apps/web/app/data-integration/overview/metrics.ts";

const today = new Date(2026, 10, 8);

test("preset periods end yesterday and keep the selected number of days", () => {
  assert.deepEqual(rangeForPeriod("yesterday", today), {
    startDate: "2026-11-07",
    endDate: "2026-11-07",
  });
  assert.deepEqual(rangeForPeriod("7d", today), {
    startDate: "2026-11-01",
    endDate: "2026-11-07",
  });
  assert.equal(activePeriod(rangeForPeriod("30d", today), today), "30d");
  assert.ok(validRange(rangeForPeriod("30d", today), today));
  assert.equal(validRange({ startDate: "2026-10-01", endDate: "2026-11-08" }, today), false);
  assert.equal(validRange({ startDate: "2026-11-07", endDate: "2026-11-01" }, today), false);
});

const stats = {
  startDate: "2026-11-06",
  endDate: "2026-11-07",
  days: [
    {
      date: "2026-11-06", syncType: "OFFLINE",
      executionCount: 3, succeededCount: 2, failedCount: 1,
      stoppedCount: 0, runningCount: 0, queuedCount: 0, pausedCount: 0,
    },
    {
      date: "2026-11-07", syncType: "REALTIME",
      executionCount: 2, succeededCount: 0, failedCount: 0,
      stoppedCount: 1, runningCount: 1, queuedCount: 0, pausedCount: 0,
    },
  ],
};

test("metric strip and trend summarize the selected execution type", () => {
  const all = sumExecutionCounts(stats.days, "ALL");
  assert.equal(all.executionCount, 5);
  assert.equal(all.succeededCount, 2);
  assert.equal(executionMetricValue(all, "successRate"), 40);
  const realtime = sumExecutionCounts(stats.days, "REALTIME");
  assert.equal(realtime.executionCount, 2);
  assert.equal(realtime.stoppedCount, 1);
  assert.equal(executionTrend(stats, "OFFLINE")[1].counts.executionCount, 0);
  assert.equal(executionTrend(stats, "ALL").length, 2);
});

import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

import {
  buildActivityTimeline,
  getActivityGroupCategory,
} from "../apps/web/app/data-agent/components/workspace/conversation/activity/activity-presenter.ts";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");

function activity({
  id,
  kind = "tool",
  toolName,
  phase,
  status = "completed",
}) {
  return {
    id,
    kind,
    title: id,
    status,
    ...(toolName ? { toolName } : {}),
    ...(phase ? { phase } : {}),
  };
}

test("adjacent tool calls are grouped by Exploring, Changed and Ran without reordering", () => {
  const entries = buildActivityTimeline([
    activity({ id: "list", toolName: "list_sync_tasks" }),
    activity({ id: "detail", toolName: "get_sync_task" }),
    activity({ id: "create", toolName: "create_sync_task" }),
    activity({ id: "publish", toolName: "publish_sync_task" }),
    activity({ id: "run", toolName: "run_analysis_script" }),
  ]);

  assert.deepEqual(
    entries.map((entry) =>
      entry.type === "group" ? entry.group.category : entry.activity.kind,
    ),
    ["exploring", "changed", "ran"],
  );
  assert.deepEqual(
    entries.map((entry) =>
      entry.type === "group"
        ? entry.group.activities.map((item) => item.id)
        : [entry.activity.id],
    ),
    [["list", "detail"], ["create", "publish"], ["run"]],
  );
});

test("text and thought blocks remain standalone and prevent unrelated groups from merging", () => {
  const entries = buildActivityTimeline([
    activity({ id: "read-1", toolName: "read_workspace_file" }),
    activity({ id: "text-1", kind: "text" }),
    activity({ id: "read-2", toolName: "list_workspace_files" }),
    activity({ id: "thought-1", kind: "thinking" }),
    activity({ id: "run-1", toolName: "run_analysis_script" }),
  ]);

  assert.deepEqual(
    entries.map((entry) =>
      entry.type === "group" ? entry.group.category : entry.activity.kind,
    ),
    ["exploring", "text", "exploring", "thinking", "ran"],
  );
  assert.equal(entries[0].type, "group");
  assert.equal(entries[2].type, "group");
  assert.notEqual(entries[0].id, entries[2].id);
});

test("progress phases share the same categories as matching tool actions", () => {
  assert.equal(
    getActivityGroupCategory(activity({ id: "analysis", kind: "progress", phase: "analysis" })),
    "exploring",
  );
  assert.equal(
    getActivityGroupCategory(activity({ id: "publish", kind: "progress", phase: "publish_schema" })),
    "changed",
  );
  assert.equal(
    getActivityGroupCategory(activity({ id: "execute", kind: "progress", phase: "execute_sql" })),
    "ran",
  );
  assert.equal(
    getActivityGroupCategory(activity({ id: "queue", kind: "progress", phase: "queue:waiting" })),
    "other",
  );
});

test("unknown tools stay in a neutral Activity group and attention states are preserved", () => {
  const entries = buildActivityTimeline([
    activity({ id: "unknown-1", toolName: "custom_future_tool" }),
    activity({ id: "unknown-2", toolName: "another_future_tool", status: "awaiting-confirmation" }),
  ]);

  assert.equal(entries.length, 1);
  assert.equal(entries[0].type, "group");
  assert.equal(entries[0].group.category, "other");
  assert.equal(entries[0].group.requiresAttention, true);
});

test("timeline uses expandable groups and keeps activity details selection wired", () => {
  const timeline = read("../apps/web/app/data-agent/components/workspace/conversation/activity/AgentActivityTimeline.tsx");
  const group = read("../apps/web/app/data-agent/components/workspace/conversation/activity/AgentActivityGroup.tsx");
  const toolActivity = read("../apps/web/app/data-agent/components/workspace/conversation/activity/AgentToolActivity.tsx");
  const thought = read("../apps/web/app/data-agent/components/workspace/conversation/activity/AgentThought.tsx");
  const detailsTimeline = read("../apps/web/app/data-agent/components/workspace/conversation/AgentDetailsTimeline.tsx");

  assert.match(timeline, /buildActivityTimeline\(activities\)/);
  assert.match(timeline, /isStreaming \|\| group\.hasRunning \|\| group\.requiresAttention/);
  assert.match(timeline, /containsSelectedActivity/);
  assert.match(timeline, /containsSelectedActivity \|\| group\.requiresAttention/);
  assert.match(group, /aria-expanded=\{expanded\}/);
  assert.match(group, /Worked for /);
  assert.match(group, /onSelectActivity/);
  assert.match(group, /disabled=\{!expanded\}/);
  assert.match(toolActivity, /disabled=\{disabled\}/);
  assert.match(toolActivity, /onSelect\?\.\(activity\.id\)/);
  assert.match(thought, /Thinking/);
  assert.match(thought, /Thought/);
  assert.match(thought, /window\.setInterval/);
  assert.match(detailsTimeline, /AgentThought key=\{activity\.id\} activity=\{activity\} compact/);
});

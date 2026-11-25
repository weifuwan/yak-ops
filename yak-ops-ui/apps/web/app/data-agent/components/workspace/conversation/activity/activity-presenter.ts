import type { AgentActivity } from "../../../../model/types";

export type ActivityGroupCategory = "exploring" | "changed" | "ran" | "other";

export type AgentActivityGroup = {
  id: string;
  category: ActivityGroupCategory;
  activities: AgentActivity[];
  hasRunning: boolean;
  requiresAttention: boolean;
};

export type AgentActivityTimelineEntry =
  | { type: "activity"; id: string; activity: AgentActivity }
  | { type: "group"; id: string; group: AgentActivityGroup };

const TOOL_CATEGORIES: Record<string, ActivityGroupCategory> = {
  list_sync_tasks: "exploring",
  get_sync_task: "exploring",
  preview_sync_task: "exploring",
  get_sync_overview: "exploring",
  list_sync_instances: "exploring",
  get_sync_instance_detail: "exploring",
  query_task_log: "exploring",
  download_task_log: "exploring",
  read_workspace_file: "exploring",
  list_workspace_files: "exploring",
  create_sync_task: "changed",
  update_sync_task: "changed",
  publish_sync_task: "changed",
  unpublish_sync_task: "changed",
  delete_sync_task: "changed",
  stop_task_instance: "changed",
  force_success_task_instance: "changed",
  run_analysis_script: "ran",
};

const ATTENTION_STATUSES = new Set([
  "error",
  "denied",
  "rejected",
  "interrupted",
  "awaiting-confirmation",
]);

function progressCategory(phase?: string): ActivityGroupCategory {
  const value = (phase ?? "").trim().toLowerCase();

  const exploringPhase = new RegExp(
    "^(analysis|plan|planning|search|research|inspect|read|query|discover|context)(?:[:_-]|$)",
  );
  const changedPhase = new RegExp(
    "^(create|update|edit|write|publish|delete|save|change|modify)(?:[:_-]|$)",
  );
  const ranPhase = new RegExp(
    "^(run|execute|test|validate|build|sync|dispatch|trigger|process)(?:[:_-]|$)",
  );

  if (exploringPhase.test(value)) {
    return "exploring";
  }
  if (changedPhase.test(value)) {
    return "changed";
  }
  if (ranPhase.test(value)) {
    return "ran";
  }
  return "other";
}

export function getActivityGroupCategory(
  activity: AgentActivity,
): ActivityGroupCategory {
  if (activity.kind === "tool") {
    return TOOL_CATEGORIES[activity.toolName ?? ""] ?? "other";
  }
  if (activity.kind === "progress") return progressCategory(activity.phase);
  return "other";
}

function requiresAttention(activity: AgentActivity) {
  return ATTENTION_STATUSES.has(activity.status);
}

export function buildActivityTimeline(
  activities: AgentActivity[],
): AgentActivityTimelineEntry[] {
  const entries: AgentActivityTimelineEntry[] = [];

  for (const activity of activities) {
    if (activity.kind === "text" || activity.kind === "thinking") {
      entries.push({
        type: "activity",
        id: activity.id,
        activity,
      });
      continue;
    }

    const category = getActivityGroupCategory(activity);
    const previous = entries[entries.length - 1];

    if (previous?.type === "group" && previous.group.category === category) {
      previous.group.activities.push(activity);
      previous.group.hasRunning ||= activity.status === "running";
      previous.group.requiresAttention ||= requiresAttention(activity);
      continue;
    }

    const group: AgentActivityGroup = {
      id: category + ":" + activity.id,
      category,
      activities: [activity],
      hasRunning: activity.status === "running",
      requiresAttention: requiresAttention(activity),
    };
    entries.push({ type: "group", id: group.id, group });
  }

  return entries;
}

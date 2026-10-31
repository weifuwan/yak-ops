import type { DataSyncDraft } from "./types";

const PREFIX = "yak.data-sync.draft:";

/** Session storage is tab-local; identity and Workspace are explicit in every draft key. */
export const taskDraftKey = (userId: string, workspaceId: string) =>
  PREFIX + encodeURIComponent(userId) + ":" + encodeURIComponent(workspaceId);

export function readTaskDraft(key: string): DataSyncDraft | undefined {
  try {
    const content = window.sessionStorage.getItem(key);
    if (!content) return undefined;
    const value = JSON.parse(content) as { version?: number; draft?: DataSyncDraft };
    const draft = value.draft;
    if (
      value.version !== 2 ||
      !draft ||
      typeof draft.name !== "string" ||
      typeof draft.sourceConnector !== "string" ||
      typeof draft.sinkConnector !== "string" ||
      typeof draft.sourceProducesChangelog !== "boolean" ||
      !["MYSQL", "POSTGRE_SQL", "ORACLE"].includes(draft.sourceType) ||
      !["MYSQL", "POSTGRE_SQL", "ORACLE"].includes(draft.targetType) ||
      ![
        "SINGLE_TABLE_OFFLINE",
        "SINGLE_TABLE_REALTIME",
        "DATABASE_OFFLINE",
        "DATABASE_REALTIME",
      ].includes(draft.solutionType) ||
      !Array.isArray(draft.steps)
    )
      return undefined;
    return draft;
  } catch {
    return undefined;
  }
}

export function writeTaskDraft(key: string, draft: DataSyncDraft) {
  try {
    window.sessionStorage.setItem(key, JSON.stringify({ version: 2, draft }));
  } catch {
    // The in-memory editor remains usable when session storage is unavailable.
  }
}

export function clearTaskDraft(key: string) {
  try {
    window.sessionStorage.removeItem(key);
  } catch {
    /* Storage may be disabled. */
  }
}

/** Clear sensitive workflow context on explicit logout or Workspace changes. No credentials are stored. */
export function clearTaskDrafts() {
  try {
    for (let i = window.sessionStorage.length - 1; i >= 0; i -= 1) {
      const key = window.sessionStorage.key(i);
      if (key?.startsWith(PREFIX)) window.sessionStorage.removeItem(key);
    }
  } catch {
    /* Storage may be disabled. */
  }
}

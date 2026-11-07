import type { PipelineDefinition } from "./native-pipeline";

export interface NativeSyncDraft {
  name: string;
  remark: string;
  sourceType: string;
  targetType: string;
  definition: PipelineDefinition;
}

const PREFIX = "yak.data-sync.native-draft:";

export const nativeDraftKey = (userId: string) => PREFIX + encodeURIComponent(userId);

export function readNativeDraft(key: string): NativeSyncDraft | undefined {
  try {
    const stored = window.sessionStorage.getItem(key);
    if (!stored) return undefined;
    const value = JSON.parse(stored);
    if (value?.version !== 1 || !value.draft || value.draft.sourceType !== "MYSQL" ||
        value.draft.targetType !== "MYSQL" || !value.draft.definition) return undefined;
    return value.draft as NativeSyncDraft;
  } catch {
    return undefined;
  }
}

export function writeNativeDraft(key: string, draft: NativeSyncDraft) {
  try {
    window.sessionStorage.setItem(key, JSON.stringify({ version: 1, draft }));
  } catch {
    // Editing remains possible without session storage.
  }
}

export function clearNativeDraft(key: string) {
  try {
    window.sessionStorage.removeItem(key);
  } catch {
    // Storage may be disabled.
  }
}

export function clearNativeDrafts() {
  try {
    for (let i = window.sessionStorage.length - 1; i >= 0; i--) {
      const key = window.sessionStorage.key(i);
      if (key?.startsWith(PREFIX)) window.sessionStorage.removeItem(key);
    }
  } catch {
    // Storage may be disabled.
  }
}

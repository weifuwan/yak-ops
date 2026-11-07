import { readPipelineDefinition } from "./native-pipeline";
import type { TaskDefinitionVersion } from "./types";

/** Keys are semantic, object property order is not. Array order is preserved. */
export function pipelineFingerprint(value: unknown): string {
  if (Array.isArray(value)) return "[" + value.map(pipelineFingerprint).join(",") + "]";
  if (value && typeof value === "object") {
    return "{" + Object.entries(value)
      .filter(([, item]) => item !== undefined)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([key, item]) => JSON.stringify(key) + ":" + pipelineFingerprint(item))
      .join(",") + "}";
  }
  return JSON.stringify(value) ?? "";
}

export interface DefinitionChange {
  field: string;
  before: string;
  after: string;
}

function flatten(value: unknown, prefix: string, result: Map<string, unknown>) {
  if (value !== null && typeof value === "object" && !Array.isArray(value)) {
    for (const [key, item] of Object.entries(value))
      flatten(item, prefix ? prefix + " / " + key : key, result);
  } else {
    result.set(prefix, value);
  }
}

/** Compare persisted Pipeline definitions directly, without an older editor DTO. */
export function compareTaskDefinitionVersions(
  before: TaskDefinitionVersion,
  after: TaskDefinitionVersion,
): DefinitionChange[] {
  const left = new Map<string, unknown>();
  const right = new Map<string, unknown>();
  flatten(readPipelineDefinition(before.parameters), "", left);
  flatten(readPipelineDefinition(after.parameters), "", right);
  const display = (value: unknown) =>
    value === undefined ? "—" : typeof value === "string" ? value : JSON.stringify(value, null, 2);
  return [...new Set([...left.keys(), ...right.keys()])]
    .sort()
    .filter((key) => pipelineFingerprint(left.get(key)) !== pipelineFingerprint(right.get(key)))
    .map((field) => ({ field, before: display(left.get(field)), after: display(right.get(field)) }));
}

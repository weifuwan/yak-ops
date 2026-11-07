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

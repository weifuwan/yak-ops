import type { NodeLocalParam } from "./types";

export function normalizeNodeLocalParams(value: unknown): NodeLocalParam[] {
  if (!Array.isArray(value)) return [];
  return value.flatMap((item) => {
    if (!item || typeof item !== "object" || Array.isArray(item)) return [];
    const param = item as Record<string, unknown>;
    const normalized: NodeLocalParam = {
      ...param,
      prop: String(param.prop ?? ""),
      direct: param.direct === "OUT" ? "OUT" : "IN",
      type: String(param.type ?? "VARCHAR"),
      value: param.value === null || param.value === undefined ? "" : String(param.value),
      sensitive: Boolean(param.sensitive),
    };
    return [normalized];
  });
}

export function normalizeNodeResourceList(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.flatMap((item) => {
    if (typeof item === "string") return [item];
    if (!item || typeof item !== "object" || Array.isArray(item)) return [];
    const resourceName = (item as Record<string, unknown>).resourceName;
    return typeof resourceName === "string" ? [resourceName] : [];
  });
}

export function buildNodeResourceList(resourceList: string[]) {
  return resourceList.map((item) => item.trim()).filter(Boolean).map((resourceName) => ({ resourceName }));
}

import type { NodeLocalParam } from "./types";

export const NODE_LOCAL_PARAM_TYPE_OPTIONS = [
  { value: "VARCHAR", label: "VARCHAR" },
  { value: "INTEGER", label: "INTEGER" },
  { value: "LONG", label: "LONG" },
  { value: "FLOAT", label: "FLOAT" },
  { value: "DOUBLE", label: "DOUBLE" },
  { value: "DATE", label: "DATE" },
  { value: "TIME", label: "TIME" },
  { value: "TIMESTAMP", label: "TIMESTAMP" },
  { value: "BOOLEAN", label: "BOOLEAN" },
  { value: "LIST", label: "LIST" },
  { value: "FILE", label: "FILE" },
] as const;

export function createNodeLocalParam(): NodeLocalParam {
  return { prop: "", direct: "IN", type: "VARCHAR", value: "", sensitive: false };
}

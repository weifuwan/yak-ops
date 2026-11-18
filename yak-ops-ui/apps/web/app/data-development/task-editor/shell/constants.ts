import type { ShellLocalParam } from "./types";

export const SHELL_PARAM_TYPE_OPTIONS = [
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

export function createShellLocalParam(): ShellLocalParam {
  return {
    prop: "",
    direct: "IN",
    type: "VARCHAR",
    value: "",
    sensitive: false,
  };
}

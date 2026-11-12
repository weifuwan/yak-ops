export type ResourceFileIconName =
  | "sql"
  | "python"
  | "shell"
  | "batch"
  | "config"
  | "hql"
  | "javascript"
  | "java"
  | "json"
  | "yaml"
  | "xml"
  | "text"
  | "log";

export interface ResourceFileTypeConfig {
  label: string;
  icon: ResourceFileIconName;
  color: string;
  background: string;
}

/**
 * File suffixes supported by the resource editor.
 * Display icons and the create-file selector both read from this registry.
 */
export const RESOURCE_FILE_TYPE_CONFIG: Record<string, ResourceFileTypeConfig> = {
  sql: { label: "SQL", icon: "sql", color: "#2563EB", background: "#EFF6FF" },
  py: { label: "Python", icon: "python", color: "#3776AB", background: "#EEF5FF" },
  sh: { label: "Shell", icon: "shell", color: "#15803D", background: "#ECFDF3" },
  bat: { label: "Batch", icon: "batch", color: "#475569", background: "#F1F5F9" },
  cfg: { label: "CFG", icon: "config", color: "#7C3AED", background: "#F5F3FF" },
  hql: { label: "HQL", icon: "hql", color: "#0F766E", background: "#ECFDF8" },
  js: { label: "JavaScript", icon: "javascript", color: "#A16207", background: "#FEFCE8" },
  java: { label: "Java", icon: "java", color: "#C2410C", background: "#FFF7ED" },
  json: { label: "JSON", icon: "json", color: "#0F766E", background: "#F0FDFA" },
  yaml: { label: "YAML", icon: "yaml", color: "#7C3AED", background: "#F5F3FF" },
  yml: { label: "YML", icon: "yaml", color: "#7C3AED", background: "#F5F3FF" },
  xml: { label: "XML", icon: "xml", color: "#0369A1", background: "#F0F9FF" },
  properties: { label: "Properties", icon: "config", color: "#7C3AED", background: "#F5F3FF" },
  txt: { label: "TXT", icon: "text", color: "#475569", background: "#F1F5F9" },
  log: { label: "Log", icon: "log", color: "#475569", background: "#F1F5F9" },
  conf: { label: "CONF", icon: "config", color: "#7C3AED", background: "#F5F3FF" },
  ini: { label: "INI", icon: "config", color: "#7C3AED", background: "#F5F3FF" },
};

const RESOURCE_FILE_TYPE_ALIASES: Record<string, string> = {
  python: "py",
  bash: "sh",
  zsh: "sh",
  cmd: "bat",
  mjs: "js",
  cjs: "js",
  jsonc: "json",
};

export const RESOURCE_FILE_TYPE_OPTIONS = [
  { value: "sql", label: "SQL" },
  { value: "py", label: "Python" },
  { value: "sh", label: "Shell" },
  { value: "bat", label: "Batch" },
  { value: "cfg", label: "CFG" },
  { value: "hql", label: "HQL" },
  { value: "js", label: "JavaScript" },
  { value: "java", label: "Java" },
  { value: "json", label: "JSON" },
  { value: "yaml", label: "YAML" },
  { value: "yml", label: "YML" },
  { value: "xml", label: "XML" },
  { value: "properties", label: "Properties" },
  { value: "txt", label: "TXT" },
  { value: "log", label: "Log" },
  { value: "conf", label: "CONF" },
  { value: "ini", label: "INI" },
] as const;

export const RESOURCE_EDIT_SUFFIXES: ReadonlySet<string> = new Set(
  RESOURCE_FILE_TYPE_OPTIONS.map((option) => option.value),
);

export const getResourceFileTypeConfig = (
  extension: string,
): ResourceFileTypeConfig | undefined => {
  const normalized = extension.replace(/^\./, "").toLowerCase();
  const key = RESOURCE_FILE_TYPE_ALIASES[normalized] ?? normalized;
  return RESOURCE_FILE_TYPE_CONFIG[key];
};

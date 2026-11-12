import type {
  ResourceItemRecord,
  WorkflowResourceNode,
} from "@/service/resources";

import type { ResourceTypeFilter } from "./types";
import { RESOURCE_EDIT_SUFFIXES } from "./file-types";

const normalizeResourcePath = (path: string) =>
  path.replace(/[\\/]+/g, "\\").replace(/\\+$/, "").toLowerCase();

/**
 * Keep the current resource directory when it still belongs to the latest
 * backend-provided storage base directory; otherwise reset to the new base.
 */
export const resolveCurrentResourceDirectory = (
  currentDir: string,
  baseDir: string,
) => {
  if (!baseDir) return currentDir;
  if (!currentDir) return baseDir;

  const normalizedBase = normalizeResourcePath(baseDir);
  const normalizedCurrent = normalizeResourcePath(currentDir);
  const isWithinBase =
    normalizedCurrent === normalizedBase ||
    normalizedCurrent.startsWith(normalizedBase + "\\");

  return isWithinBase ? currentDir : baseDir;
};

export const isDirectoryNode = (node: WorkflowResourceNode) =>
  Boolean(
    node.isDirctory ??
      node.dirctory ??
      node.isDirectory ??
      (node.children && node.children.length > 0),
  );

export const isDirectoryItem = (item: ResourceItemRecord) =>
  Boolean(item.isDirectory ?? item.directory ?? item.isDirctory);

export const getName = (item: ResourceItemRecord) => {
  const pathParts = item.fullName.split(/[\\/]/).filter(Boolean);
  return item.fileName || item.alias || pathParts[pathParts.length - 1] || "未命名资源";
};

export const getExtension = (name: string) => {
  const dot = name.lastIndexOf(".");
  return dot > 0 && dot < name.length - 1 ? name.slice(dot + 1).toLowerCase() : "";
};

/**
 * Uses the file suffixes supported by the resource content editor.
 * Files outside this list remain downloadable, but are not opened in the text editor.
 */
export const canEditResource = (item: ResourceItemRecord) => {
  if (isDirectoryItem(item)) return false;
  const name = getName(item);
  const dot = name.lastIndexOf(".");
  const suffix = dot > 0 && dot < name.length - 1 ? name.slice(dot + 1) : "";
  return RESOURCE_EDIT_SUFFIXES.has(suffix.toLowerCase());
};

export const getResourceCategory = (name: string): ResourceTypeFilter => {
  const extension = getExtension(name);
  if (extension === "sql") return "SQL";
  if (extension === "jar") return "JAR";
  if (["py", "python"].includes(extension)) return "PYTHON";
  if (["sh", "bash", "zsh"].includes(extension)) return "SHELL";
  if (["png", "jpg", "jpeg", "gif", "webp", "svg", "bmp"].includes(extension)) return "IMAGE";
  if (["md", "txt", "log", "pdf", "doc", "docx", "properties", "conf", "json", "xml", "yml", "yaml"].includes(extension)) return "DOCUMENT";
  if (extension) return "FILE";
  return "OTHER";
};

export const formatSize = (size?: number) => {
  if (size === undefined || size <= 0) return "—";
  if (size < 1024) return size + " B";
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + " KB";
  if (size < 1024 * 1024 * 1024) return (size / (1024 * 1024)).toFixed(1) + " MB";
  return (size / (1024 * 1024 * 1024)).toFixed(1) + " GB";
};

export const getDateParts = (value?: string | number | Date) => {
  if (!value) return { date: "—", time: "" };
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) return { date: "—", time: "" };
  return {
    date: date.toLocaleDateString("zh-CN", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }),
    time: date.toLocaleTimeString("zh-CN", {
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    }),
  };
};

export const findDirectoryPath = (
  nodes: WorkflowResourceNode[],
  target: string,
  ancestors: WorkflowResourceNode[] = [],
): WorkflowResourceNode[] | null => {
  for (const node of nodes) {
    if (!isDirectoryNode(node)) continue;
    const next = [...ancestors, node];
    if (node.fullName === target) return next;
    const nested = findDirectoryPath(node.children ?? [], target, next);
    if (nested) return nested;
  }
  return null;
};

export const matchesTypeFilter = (
  item: ResourceItemRecord,
  filter: ResourceTypeFilter,
) => {
  if (filter === "ALL") return true;
  if (filter === "FOLDER") return isDirectoryItem(item);
  if (filter === "FILE") return !isDirectoryItem(item);
  if (isDirectoryItem(item)) return false;
  const category = getResourceCategory(getName(item));
  return filter === "OTHER" ? category === "OTHER" : category === filter;
};

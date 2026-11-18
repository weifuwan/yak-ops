import type { TreeSelectOption } from "@yak-ops/yak-ui";
import type { WorkflowResourceNode } from "@/service/resources";

import { ResourceTreeIcon } from "../../../../resources/components/resource-file-icon";

export function getResourceNodeName(node: WorkflowResourceNode) {
  if (node.name) return node.name;
  const parts = (node.fullName || node.currentDir || "").split(/[\\/]/).filter(Boolean);
  return parts[parts.length - 1] || "未命名资源";
}

export function isResourceDirectory(node: WorkflowResourceNode) {
  return Boolean(node.isDirctory ?? node.dirctory ?? node.isDirectory ?? (node.children && node.children.length > 0));
}

export function buildResourceTreeOptions(nodes: WorkflowResourceNode[], parentPath = ""): TreeSelectOption[] {
  return nodes.map((node, index) => {
    const label = getResourceNodeName(node);
    const value = node.fullName || node.currentDir || [parentPath, label, index].filter(Boolean).join("/");
    const directory = isResourceDirectory(node);
    return {
      label,
      value,
      selectable: !directory,
      searchText: [node.fullName, node.currentDir].filter(Boolean).join(" "),
      icon: <ResourceTreeIcon name={label} directory={directory} size={16} />,
      children: buildResourceTreeOptions(node.children ?? [], value),
    };
  });
}

export function flattenResourceFiles(nodes: WorkflowResourceNode[]): string[] {
  const paths: string[] = [];
  const visit = (node: WorkflowResourceNode) => {
    const children = node.children ?? [];
    if (children.length > 0 || isResourceDirectory(node)) {
      children.forEach(visit);
      return;
    }
    if (node.fullName) paths.push(node.fullName);
  };
  nodes.forEach(visit);
  return [...new Set(paths)].sort((a, b) => a.localeCompare(b));
}

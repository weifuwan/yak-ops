import type { TreeSelectOption } from "@yak-ops/yak-ui";
import type { WorkflowResourceNode } from "@/service/resources";

import { ResourceTreeIcon } from "../../../resources/components/resource-file-icon";

export function getResourceNodeName(node: WorkflowResourceNode) {
  if (node.name) return node.name;

  const pathParts = (node.fullName || node.currentDir || "")
    .split(/[\\/]/)
    .filter(Boolean);

  return pathParts[pathParts.length - 1] || "未命名资源";
}

export function isResourceDirectory(node: WorkflowResourceNode) {
  return Boolean(
    node.isDirctory ??
      node.dirctory ??
      node.isDirectory ??
      (node.children && node.children.length > 0),
  );
}

export function buildResourceTreeOptions(
  nodes: WorkflowResourceNode[],
  parentPath = "",
): TreeSelectOption[] {
  return nodes.map((node, index) => {
    const label = getResourceNodeName(node);
    const value =
      node.fullName ||
      node.currentDir ||
      [parentPath, label, index].filter(Boolean).join("/");
    const directory = isResourceDirectory(node);
    const children = node.children ?? [];

    return {
      label,
      value,
      selectable: !directory,
      searchText: [node.fullName, node.currentDir].filter(Boolean).join(" "),
      icon: <ResourceTreeIcon name={label} directory={directory} size={16} />,
      children: buildResourceTreeOptions(children, value),
    };
  });
}

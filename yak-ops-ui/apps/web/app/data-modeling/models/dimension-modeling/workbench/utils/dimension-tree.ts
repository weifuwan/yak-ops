import type { DimensionTreeNode } from "@/service/data-modeling/models/dimension-modeling";

export function filterDimensionTree(
  nodes: DimensionTreeNode[],
  keyword: string,
): DimensionTreeNode[] {
  const normalized = keyword.trim().toLocaleLowerCase();
  if (!normalized) return nodes;

  return nodes.reduce<DimensionTreeNode[]>((result, node) => {
    const children = filterDimensionTree(node.children || [], normalized);
    const searchableText = [node.title, node.searchText]
      .filter(Boolean)
      .join(" ")
      .toLocaleLowerCase();
    if (searchableText.includes(normalized) || children.length) {
      result.push({ ...node, children });
    }
    return result;
  }, []);
}

export function collectDimensionExpandedKeys(
  nodes: DimensionTreeNode[],
): string[] {
  const result: string[] = [];
  nodes.forEach((node) => {
    const children = node.children || [];
    if (children.length) {
      result.push(node.key);
      result.push(...collectDimensionExpandedKeys(children));
    }
  });
  return result;
}

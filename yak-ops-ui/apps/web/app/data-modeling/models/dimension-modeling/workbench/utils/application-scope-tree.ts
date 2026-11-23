import type { TreeSelectOption } from "@yak-ops/yak-ui";
import type { BusinessTypeTreeNode } from "@/service/data-modeling/planning/business-type";
import type { SubjectDomainDirectoryNode } from "@/service/data-modeling/planning/application-layer/subject-domain";
import type { ModelingRecord } from "@/service/data-modeling/shared/types";

/**
 * Application-table scope hierarchy:
 * business category (not selectable) -> data mart -> subject domain.
 * The backend's subject-domain tree already includes nested marts and subjects.
 * Keep its -MART/-SUBJECT values intact for the DimensionModeling save contract.
 */
export function buildApplicationScopeTree(
  businessTypes: readonly BusinessTypeTreeNode[],
  marts: readonly ModelingRecord[],
  scopeTree: readonly SubjectDomainDirectoryNode[],
): TreeSelectOption[] {
  const businessNodes = new Map<string, TreeSelectOption>();

  const businessNode = (record: BusinessTypeTreeNode): TreeSelectOption | null => {
    const id = String(record.value || record.key || "").trim();
    if (!id) return null;
    const children = (record.children || [])
      .map(businessNode)
      .filter((child): child is TreeSelectOption => child !== null);
    const node: TreeSelectOption = {
      value: `business:${id}`,
      label: String(record.title || id),
      selectable: false,
      children,
    };
    businessNodes.set(id, node);
    return node;
  };

  const options = businessTypes
    .map(businessNode)
    .filter((node): node is TreeSelectOption => node !== null);

  const martById = new Map(marts.map((record) => [String(record.id || ""), record]));
  let unassigned: TreeSelectOption | undefined;
  const fallbackGroup = () => {
    if (!unassigned) {
      unassigned = {
        value: "business:unassigned",
        label: "未关联业务分类",
        selectable: false,
        children: [],
      };
      options.push(unassigned);
    }
    return unassigned;
  };

  const scopeNode = (node: SubjectDomainDirectoryNode): TreeSelectOption | null => {
    const value = String(node.value || node.key || "").trim();
    if (!value || (node.type !== "MART" && node.type !== "SUBJECT")) return null;
    const children = (node.children || [])
      .map(scopeNode)
      .filter((child): child is TreeSelectOption => child !== null);
    return {
      value, // Backend values already carry the MART or SUBJECT suffix.
      label: String(node.title || value),
      children,
    };
  };

  for (const root of scopeTree) {
    const item = scopeNode(root);
    if (!item) continue;
    // Every normal root is a mart; parent marts carry the business-type binding.
    const martId = root.type === "MART" ? item.value.replace(/-MART$/, "") : "";
    const businessTypeId = String(martById.get(martId)?.businessTypeId || "");
    const group = businessNodes.get(businessTypeId) || fallbackGroup();
    const children = group.children as TreeSelectOption[];
    children.push(item);
  }

  return options;
}

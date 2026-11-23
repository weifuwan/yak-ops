import type { TreeSelectOption } from "@yak-ops/yak-ui";
import type { BusinessTypeTreeNode } from "@/service/data-modeling/planning/business-type";
import { PlanningIcon } from "../../../../planning/components/icons";

/** Share the same real-ID tree mapping between the sidebar and the dimension editor. */
export function mapBusinessTypeTreeOption(node: BusinessTypeTreeNode): TreeSelectOption | null {
  const value = String(node.value || node.key || "");
  if (!value) return null;

  const children = (node.children || [])
    .map(mapBusinessTypeTreeOption)
    .filter((option): option is TreeSelectOption => option !== null);

  return {
    value,
    label: String(node.title || value),
    icon: <PlanningIcon name="business-type" size={13} className="text-[#3a3d51]" />,
    searchText: [node.key, node.value, node.parentId].filter(Boolean).join(" "),
    children: children.length ? children : undefined,
  };
}

export function findBusinessTypeTreeOption(
  options: readonly TreeSelectOption[],
  value: string,
): TreeSelectOption | undefined {
  for (const option of options) {
    if (option.value === value) return option;
    const child = findBusinessTypeTreeOption(option.children || [], value);
    if (child) return child;
  }
  return undefined;
}


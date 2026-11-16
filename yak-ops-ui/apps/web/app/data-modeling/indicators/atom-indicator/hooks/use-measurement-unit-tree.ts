import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { Folder, Ruler } from "lucide-react";
import type { TreeSelectOption } from "@yak-ops/yak-ui";

import { standardDirectoryApi } from "@/service/data-modeling/standards/shared/standard-directory";
import type { ModelingAssetTreeNode } from "@/service/data-modeling/shared/types";

function mapUnitNode(node: ModelingAssetTreeNode): TreeSelectOption {
  const children = (node.children || []).map(mapUnitNode);
  const isUnit = node.type === "UNIT_STANDARD";

  return {
    value: node.value,
    label: node.title,
    selectable: isUnit,
    icon: isUnit
      ? <Ruler size={13} className="text-[var(--yak-components-muted-text)]" />
      : <Folder size={13} className="text-[var(--yak-components-muted-text)]" />,
    searchText: node.key + " " + node.id,
    children: children.length ? children : undefined,
  };
}

export function useMeasurementUnitTree(projectCode: string) {
  const query = useQuery({
    queryKey: ["modeling", "atom-indicator-unit-tree", projectCode],
    queryFn: () => standardDirectoryApi.tree(projectCode, "UNIT"),
    enabled: Boolean(projectCode),
  });

  const options = useMemo(
    () => (query.data || []).map(mapUnitNode),
    [query.data],
  );

  return { ...query, options };
}

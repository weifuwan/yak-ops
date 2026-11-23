import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import type { CascaderOption } from "@yak-ops/yak-ui";

import {
  businessProcessApi,
  type BusinessProcessCascaderNode,
} from "@/service/data-modeling/planning/common-layer/business-process";

function mapProcessNode(
  node: BusinessProcessCascaderNode,
  depth = 0,
): CascaderOption {
  const children = node.children || [];

  return {
    value: node.value,
    label: node.label,
    selectable: depth > 0 && children.length === 0,
    children: children.length
      ? children.map((child) => mapProcessNode(child, depth + 1))
      : undefined,
  };
}

export function useBusinessProcessCascader(projectCode: string, busTypeId?: string, enabled = true) {
  const query = useQuery({
    queryKey: ["modeling", "business-process", "cascader", projectCode, busTypeId || ""],
    queryFn: () => businessProcessApi.cascaderOption(projectCode, busTypeId),
    enabled: Boolean(projectCode && enabled),
  });

  const options = useMemo(
    () => (query.data || []).map((node) => mapProcessNode(node)),
    [query.data],
  );

  return { ...query, options };
}

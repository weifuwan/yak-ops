import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";

import {
  dimensionModelingApi,
  type DimensionTreeGrouping,
  type DimensionTreeNode,
  type DimensionWarehouseType,
} from "@/service/data-modeling/models/dimension-modeling";

export const DIMENSION_WAREHOUSE_OPTIONS: Array<{
  value: DimensionWarehouseType;
  label: string;
}> = [
  { value: "SOURCE_LAYER", label: "贴源层" },
  { value: "PUBLIC_LAYER", label: "公共层" },
  { value: "APPLICATION_LAYER", label: "应用层" },
  { value: "NONE_LAYER", label: "未分层" },
];

export function useDimensionTree(projectCode: string) {
  const [dwType, setDwType] = useState<DimensionWarehouseType>("SOURCE_LAYER");
  const [groupBy, setGroupBy] = useState<DimensionTreeGrouping>("BUSINESS_TYPE");
  const [scopeValue, setScopeValue] = useState("");
  const [keyword, setKeyword] = useState("");
  const [expandedKeys, setExpandedKeys] = useState<Set<string>>(
    () => new Set(["DimensionTable_ROOT"]),
  );

  const treeQuery = useQuery<DimensionTreeNode[]>({
    queryKey: [
      "modeling",
      "dimension-tree",
      projectCode,
      dwType,
      groupBy,
      scopeValue,
      keyword,
    ],
    queryFn: () => {
      const params: Record<string, unknown> = {
        dwhType: dwType,
        selectOption: groupBy,
        keyword: keyword.trim() || undefined,
      };
      if (scopeValue) {
        params[groupBy === "BUSINESS_TYPE" ? "busTypeId" : "domainId"] = scopeValue;
      }
      return dimensionModelingApi.tree(projectCode, params);
    },
    enabled: Boolean(projectCode),
  });

  useEffect(() => {
    setExpandedKeys(new Set(["DimensionTable_ROOT"]));
  }, [dwType, groupBy, scopeValue, keyword]);

  const changeDwType = (value: string) => {
    setDwType(value as DimensionWarehouseType);
    setGroupBy("BUSINESS_TYPE");
    setScopeValue("");
  };

  const toggleExpanded = (key: string) => {
    setExpandedKeys((current) => {
      const next = new Set(current);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  };

  return {
    dwType,
    setDwType: changeDwType,
    groupBy,
    setGroupBy,
    scopeValue,
    setScopeValue,
    keyword,
    setKeyword,
    root: treeQuery.data?.[0] || null,
    expandedKeys,
    setExpandedKeys,
    toggleExpanded,
    treeQuery,
  };
}

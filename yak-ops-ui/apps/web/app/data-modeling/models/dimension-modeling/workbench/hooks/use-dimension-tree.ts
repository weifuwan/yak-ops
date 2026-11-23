import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";

import {
  dimensionModelingApi,
  type DimensionTreeGrouping,
  type DimensionTreeNode,
  type DimensionWarehouseType,
} from "@/service/data-modeling/models/dimension-modeling";
import {
  collectDimensionExpandedKeys,
  filterDimensionTree,
} from "../utils/dimension-tree";

export const DIMENSION_WAREHOUSE_OPTIONS: Array<{
  value: DimensionWarehouseType;
  label: string;
}> = [
  { value: "SOURCE_LAYER", label: "贴源层" },
  { value: "PUBLIC_LAYER", label: "公共层" },
  { value: "APPLICATION_LAYER", label: "应用层" },
  { value: "NONE_LAYER", label: "未分层" },
];

const ROOT_KEY = "DimensionTable_ROOT";

function defaultGrouping(dwType: DimensionWarehouseType): DimensionTreeGrouping {
  return dwType === "APPLICATION_LAYER" ? "DATA_MART" : "BUSINESS_TYPE";
}

function scopeParameter(groupBy: DimensionTreeGrouping): string {
  switch (groupBy) {
    case "BUSINESS_TYPE": return "busTypeId";
    case "DATA_DOMAIN": return "domainId";
    case "DATA_MART": return "martId";
    case "SUBJECT_DOMAIN": return "subjectId";
  }
}

export function useDimensionTree(projectCode: string) {
  const [dwType, setDwType] = useState<DimensionWarehouseType>("SOURCE_LAYER");
  const [groupBy, setGroupBy] = useState<DimensionTreeGrouping>("BUSINESS_TYPE");
  const [scopeValue, setScopeValue] = useState("");
  const [keyword, setKeyword] = useState("");
  const [expandedKeys, setExpandedKeys] = useState<Set<string>>(
    () => new Set([ROOT_KEY]),
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
      if (scopeValue) params[scopeParameter(groupBy)] = scopeValue;
      return dimensionModelingApi.tree(projectCode, params);
    },
    enabled: Boolean(projectCode),
    placeholderData: (previousData) => previousData,
  });

  const visibleTree = useMemo(
    () => filterDimensionTree(treeQuery.data || [], keyword),
    [treeQuery.data, keyword],
  );

  const visibleExpandedKeys = useMemo(() => {
    const next = new Set(expandedKeys);
    if (keyword.trim()) {
      collectDimensionExpandedKeys(visibleTree).forEach((key) => next.add(key));
    }
    return next;
  }, [expandedKeys, keyword, visibleTree]);

  useEffect(() => {
    setExpandedKeys(new Set([ROOT_KEY]));
  }, [dwType, groupBy, scopeValue]);

  const changeDwType = (value: string) => {
    const nextDwType = value as DimensionWarehouseType;
    setDwType(nextDwType);
    setGroupBy(defaultGrouping(nextDwType));
    setScopeValue("");
  };

  const changeGroupBy = (value: DimensionTreeGrouping) => {
    // A source table is grouped only by business category, never by data domain.
    if (dwType === "SOURCE_LAYER" && value !== "BUSINESS_TYPE") return;
    setGroupBy(value);
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
    setGroupBy: changeGroupBy,
    scopeValue,
    setScopeValue,
    keyword,
    setKeyword,
    root: treeQuery.data?.[0] || null,
    visibleRoot: visibleTree[0] || null,
    expandedKeys,
    visibleExpandedKeys,
    setExpandedKeys,
    toggleExpanded,
    treeQuery,
  };
}

import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { modelingBusinessTypeApi, modelingEntityApi, type ModelingRecord, type ModelingResourceKey } from "../../../service";
import type { IndicatorGroupBy, IndicatorKind, IndicatorTreeNode } from "../types";
import { buildAtomIndicatorTree, buildGroupedTree, buildTimePeriodTree, collectExpandedKeys, filterTree } from "../utils/indicator-tree";

export function useIndicatorTree(projectCode: string, activeKind: IndicatorKind) {
  const [groupBy, setGroupBy] = useState<IndicatorGroupBy>("BUSINESS_TYPE");
  const [dwType, setDwType] = useState("PUBLIC_LAYER");
  const [treeKeyword, setTreeKeyword] = useState("");
  const [expandedKeys, setExpandedKeys] = useState<Set<string>>(new Set());
  const [selectedTreeKey, setSelectedTreeKey] = useState("");

  const treeQuery = useQuery<IndicatorTreeNode[]>({
    queryKey: ["modeling", "indicator-tree", projectCode, activeKind, groupBy, activeKind === "atomIndicator" ? dwType : "all"],
    queryFn: async () => {
      if (activeKind === "timePeriod") {
        const results = await Promise.all([
          modelingEntityApi.all<ModelingRecord>(projectCode, "timePeriodCategory"),
          modelingEntityApi.all<ModelingRecord>(projectCode, "timePeriod"),
        ]);
        return buildTimePeriodTree(results[0], results[1]);
      }
      if (activeKind === "atomIndicator") {
        const [businessTypes, dataDomains, businessProcesses, atomIndicators] = await Promise.all([
          modelingEntityApi.all<ModelingRecord>(projectCode, "businessType"),
          modelingEntityApi.all<ModelingRecord>(projectCode, "dataDomain"),
          modelingEntityApi.all<ModelingRecord>(projectCode, "businessProcess"),
          modelingEntityApi.all<ModelingRecord>(projectCode, "atomIndicator", { dwType }),
        ]);
        const relatedDomainMap = groupBy === "BUSINESS_TYPE" ? await modelingBusinessTypeApi.allRelatedDomainIds(projectCode) : {};
        return buildAtomIndicatorTree(businessTypes, dataDomains, businessProcesses, atomIndicators, groupBy, new Map(Object.entries(relatedDomainMap)));
      }
      const groupResource: ModelingResourceKey = groupBy === "BUSINESS_TYPE" ? "businessType" : "dataDomain";
      const results = await Promise.all([
        modelingEntityApi.all<ModelingRecord>(projectCode, groupResource),
        modelingEntityApi.all<ModelingRecord>(projectCode, activeKind),
      ]);
      return buildGroupedTree(results[0], results[1], groupBy);
    },
    enabled: Boolean(projectCode),
  });

  const visibleTree = useMemo(() => filterTree(treeQuery.data || [], treeKeyword), [treeQuery.data, treeKeyword]);
  const visibleExpandedKeys = useMemo(() => {
    const next = new Set(expandedKeys);
    if (treeKeyword.trim()) collectExpandedKeys(visibleTree).forEach((key) => next.add(key));
    return next;
  }, [expandedKeys, treeKeyword, visibleTree]);

  useEffect(() => {
    if (activeKind === "atomIndicator") {
      const root = (treeQuery.data || []).find((node) => node.nodeType === "root");
      setExpandedKeys(new Set(root ? [root.key] : []));
      return;
    }
    setExpandedKeys(new Set(collectExpandedKeys(treeQuery.data || [])));
  }, [treeQuery.data, activeKind]);

  useEffect(() => {
    setTreeKeyword("");
    setSelectedTreeKey("");
  }, [activeKind, groupBy, dwType]);

  return { groupBy, setGroupBy, dwType, setDwType, treeKeyword, setTreeKeyword, expandedKeys, setExpandedKeys, selectedTreeKey, setSelectedTreeKey, visibleTree, visibleExpandedKeys, treeQuery };
}

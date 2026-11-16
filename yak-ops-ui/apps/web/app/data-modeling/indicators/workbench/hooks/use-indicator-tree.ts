import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";

import { businessTypeApi } from "@/service/data-modeling/planning/business-type";
import { businessProcessApi } from "@/service/data-modeling/planning/common-layer/business-process";
import { dataDomainApi } from "@/service/data-modeling/planning/common-layer/data-domain";
import { atomIndicatorApi } from "@/service/data-modeling/indicators/atom-indicator";
import { getIndicatorApi } from "@/service/data-modeling/indicators";
import { modifierApi } from "@/service/data-modeling/indicators/modifier";
import { timePeriodApi, timePeriodCategoryApi } from "@/service/data-modeling/indicators/time-period";
import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import type { IndicatorGroupBy, IndicatorKind, IndicatorTreeNode, ModifierScopeType } from "../types";
import {
  buildAtomIndicatorTree,
  buildGroupedTree,
  buildModifierTree,
  buildTimePeriodTree,
  collectExpandedKeys,
  filterTree,
} from "../utils/indicator-tree";

export function useIndicatorTree(projectCode: string, activeKind: IndicatorKind) {
  const [groupBy, setGroupBy] = useState<IndicatorGroupBy>("BUSINESS_TYPE");
  const [dwType, setDwType] = useState("PUBLIC_LAYER");
  const [modifierScopeType, setModifierScopeType] = useState<ModifierScopeType>("DATA_DOMAIN");
  const [modifierScopeValue, setModifierScopeValue] = useState("");
  const [treeKeyword, setTreeKeyword] = useState("");
  const [expandedKeys, setExpandedKeys] = useState<Set<string>>(new Set());
  const [selectedTreeKey, setSelectedTreeKey] = useState("");

  const treeQuery = useQuery<IndicatorTreeNode[]>({
    queryKey: [
      "modeling",
      "indicator-tree",
      projectCode,
      activeKind,
      groupBy,
      activeKind === "atomIndicator" || activeKind === "modifier" ? dwType : "all",
      activeKind === "modifier" ? modifierScopeType : "",
      activeKind === "modifier" ? modifierScopeValue : "",
    ],
    queryFn: async () => {
      if (activeKind === "timePeriod") {
        const results = await Promise.all([
          timePeriodCategoryApi.all<ModelingRecord>(projectCode),
          timePeriodApi.all<ModelingRecord>(projectCode),
        ]);
        return buildTimePeriodTree(results[0], results[1]);
      }
      if (activeKind === "atomIndicator") {
        const [businessTypes, dataDomains, businessProcesses, atomIndicators] = await Promise.all([
          businessTypeApi.all<ModelingRecord>(projectCode),
          dataDomainApi.all<ModelingRecord>(projectCode),
          businessProcessApi.all<ModelingRecord>(projectCode),
          atomIndicatorApi.all<ModelingRecord>(projectCode, { dwType }),
        ]);
        const relatedDomainMap = groupBy === "BUSINESS_TYPE" ? await businessTypeApi.allRelatedDomainIds(projectCode) : {};
        return buildAtomIndicatorTree(businessTypes, dataDomains, businessProcesses, atomIndicators, groupBy, new Map(Object.entries(relatedDomainMap)));
      }
      if (activeKind === "modifier") {
        const groupApi = modifierScopeType === "BUSINESS_TYPE" ? businessTypeApi : dataDomainApi;
        const [groups, modifiers] = await Promise.all([
          groupApi.all<ModelingRecord>(projectCode),
          modifierApi.all<ModelingRecord>(projectCode, { dwType }),
        ]);
        return buildModifierTree(groups, modifiers, modifierScopeType, modifierScopeValue);
      }

      const groupApi = groupBy === "BUSINESS_TYPE" ? businessTypeApi : dataDomainApi;
      const results = await Promise.all([
        groupApi.all<ModelingRecord>(projectCode),
        getIndicatorApi(activeKind).all<ModelingRecord>(projectCode),
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
    if (activeKind === "atomIndicator" || activeKind === "modifier") {
      const root = (treeQuery.data || []).find((node) => node.nodeType === "root");
      setExpandedKeys(new Set(root ? [root.key] : []));
      return;
    }
    setExpandedKeys(new Set(collectExpandedKeys(treeQuery.data || [])));
  }, [treeQuery.data, activeKind]);

  useEffect(() => {
    setTreeKeyword("");
    setSelectedTreeKey("");
  }, [activeKind, groupBy, dwType, modifierScopeType, modifierScopeValue]);

  return {
    groupBy,
    setGroupBy,
    dwType,
    setDwType,
    modifierScopeType,
    setModifierScopeType,
    modifierScopeValue,
    setModifierScopeValue,
    treeKeyword,
    setTreeKeyword,
    expandedKeys,
    setExpandedKeys,
    selectedTreeKey,
    setSelectedTreeKey,
    visibleTree,
    visibleExpandedKeys,
    treeQuery,
  };
}

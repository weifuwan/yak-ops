import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";

import { businessTypeApi } from "@/service/data-modeling/planning/business-type";
import { dataDomainApi } from "@/service/data-modeling/planning/common-layer/data-domain";
import { dwLayerApi } from "@/service/data-modeling/planning/dw-layer";
import { getIndicatorApi } from "@/service/data-modeling/indicators";
import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import type { IndicatorGroupBy, IndicatorKind } from "../types";
import { buildGroupedTree } from "../utils/indicator-tree";
import { useIndicatorTreeView } from "./use-indicator-tree-view";

type GroupedIndicatorKind = Extract<IndicatorKind, "derivativeIndicator" | "composeIndicator">;

export function useGroupedIndicatorTree(
  projectCode: string,
  kind: GroupedIndicatorKind,
  dwType: string,
  enabled: boolean,
) {
  const queryClient = useQueryClient();
  const [groupBy, setGroupBy] = useState<IndicatorGroupBy>("BUSINESS_TYPE");
  const [scopeValue, setScopeValue] = useState("");
  const tree = useIndicatorTreeView({
    projectCode,
    kind,
    queryKeyParts: [groupBy, dwType, scopeValue],
    enabled,
    queryFn: async () => {
      const groupApi = groupBy === "BUSINESS_TYPE" ? businessTypeApi : dataDomainApi;
      const [groups, records, dwLayers] = await Promise.all([
        groupApi.all<ModelingRecord>(projectCode),
        getIndicatorApi(kind).all<ModelingRecord>(projectCode),
        queryClient.fetchQuery({
          queryKey: ["modeling", "indicator-tree-dw-layers", projectCode, dwType],
          queryFn: () => dwLayerApi.all<ModelingRecord>(projectCode, { dwType }),
          staleTime: 60_000,
        }),
      ]);

      // Derived and composite assets store their warehouse layer relation as dwhId.
      const layerIds = new Set(dwLayers.map((layer) => String(layer.id || "")).filter(Boolean));
      const scopedRecords = records.filter((record) => layerIds.has(String(record.dwhId || "")));
      return buildGroupedTree(groups, scopedRecords, groupBy, kind, scopeValue);
    },
  });

  return { ...tree, groupBy, setGroupBy, scopeValue, setScopeValue };
}

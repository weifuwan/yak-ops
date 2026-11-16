import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";

import { businessTypeApi } from "@/service/data-modeling/planning/business-type";
import { dataDomainApi } from "@/service/data-modeling/planning/common-layer/data-domain";
import { businessProcessApi } from "@/service/data-modeling/planning/common-layer/business-process";
import { dataMartApi } from "@/service/data-modeling/planning/application-layer/data-mart";
import { subjectDomainApi } from "@/service/data-modeling/planning/application-layer/subject-domain";
import { dwLayerApi } from "@/service/data-modeling/planning/dw-layer";
import { getIndicatorApi } from "@/service/data-modeling/indicators";
import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import type { IndicatorGroupBy, IndicatorKind } from "../types";
import { buildDerivativeIndicatorTree, buildGroupedTree } from "../utils/indicator-tree";
import { useIndicatorTreeView } from "./use-indicator-tree-view";

type GroupedIndicatorKind = Extract<IndicatorKind, "derivativeIndicator" | "composeIndicator">;

export function useGroupedIndicatorTree(
  projectCode: string,
  kind: GroupedIndicatorKind,
  dwType: string,
  enabled: boolean,
) {
  const queryClient = useQueryClient();
  const [groupBy, setGroupBy] = useState<IndicatorGroupBy>(() =>
    kind === "derivativeIndicator" && dwType === "APPLICATION_LAYER" ? "DATA_MART" : "BUSINESS_TYPE",
  );
  const [scopeValue, setScopeValue] = useState("");
  useEffect(() => {
    if (kind !== "derivativeIndicator") return;
    setGroupBy(dwType === "APPLICATION_LAYER" ? "DATA_MART" : "BUSINESS_TYPE");
    setScopeValue("");
  }, [kind, dwType]);
  const tree = useIndicatorTreeView({
    projectCode,
    kind,
    queryKeyParts: [groupBy, dwType, scopeValue],
    enabled,
    queryFn: async () => {
      if (kind === "derivativeIndicator") {
        const [businessTypes, dataDomains, businessProcesses, dataMarts, subjectDomains, records] = await Promise.all([
          businessTypeApi.all<ModelingRecord>(projectCode),
          dataDomainApi.all<ModelingRecord>(projectCode),
          businessProcessApi.all<ModelingRecord>(projectCode),
          dataMartApi.all<ModelingRecord>(projectCode),
          subjectDomainApi.all<ModelingRecord>(projectCode),
          getIndicatorApi(kind).all<ModelingRecord>(projectCode),
        ]);
        const scopedRecords = records.filter((record) => {
          const type = String(record.dwType || "");
          return dwType === "UNASSIGNED"
            ? type !== "PUBLIC_LAYER" && type !== "APPLICATION_LAYER"
            : type === dwType;
        });
        return buildDerivativeIndicatorTree({
          businessTypes, dataDomains, businessProcesses, dataMarts, subjectDomains,
          records: scopedRecords, groupBy, scopeValue, dwType,
        });
      }

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
      const layerIds = new Set(dwLayers.map((layer) => String(layer.id || "")).filter(Boolean));
      const scopedRecords = records.filter((record) => layerIds.has(String(record.dwhId || "")));
      return buildGroupedTree(groups, scopedRecords, groupBy, kind, scopeValue);
    },
  });

  return { ...tree, groupBy, setGroupBy, scopeValue, setScopeValue };
}

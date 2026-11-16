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
import { buildWarehouseIndicatorTree } from "../utils/indicator-tree";
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
    dwType === "APPLICATION_LAYER" ? "DATA_MART" : "BUSINESS_TYPE",
  );
  const [scopeValue, setScopeValue] = useState("");

  useEffect(() => {
    setGroupBy(dwType === "APPLICATION_LAYER" ? "DATA_MART" : "BUSINESS_TYPE");
    setScopeValue("");
  }, [kind, dwType]);

  const tree = useIndicatorTreeView({
    projectCode,
    kind,
    queryKeyParts: [groupBy, dwType, scopeValue],
    enabled,
    queryFn: async () => {
      const [businessTypes, dataDomains, businessProcesses, dataMarts, subjectDomains, records] = await Promise.all([
        businessTypeApi.all<ModelingRecord>(projectCode),
        dataDomainApi.all<ModelingRecord>(projectCode),
        businessProcessApi.all<ModelingRecord>(projectCode),
        dataMartApi.all<ModelingRecord>(projectCode),
        subjectDomainApi.all<ModelingRecord>(projectCode),
        getIndicatorApi(kind).all<ModelingRecord>(projectCode),
      ]);

      let scopedRecords: ModelingRecord[];
      if (kind === "derivativeIndicator") {
        scopedRecords = records.filter((record) => {
          const type = String(record.dwType || "");
          return dwType === "UNASSIGNED"
            ? type !== "PUBLIC_LAYER" && type !== "APPLICATION_LAYER"
            : type === dwType;
        });
      } else {
        const dwLayers = await queryClient.fetchQuery({
          queryKey: ["modeling", "indicator-tree-dw-layers", projectCode, dwType],
          queryFn: () => dwLayerApi.all<ModelingRecord>(projectCode, { dwType }),
          staleTime: 60_000,
        });
        const layerIds = new Set(dwLayers.map((layer) => String(layer.id || "")).filter(Boolean));
        scopedRecords = records
          .filter((record) => layerIds.has(String(record.dwhId || "")))
          .map((record) => ({ ...record, dwType }));
      }

      return buildWarehouseIndicatorTree({
        kind,
        businessTypes,
        dataDomains,
        businessProcesses,
        dataMarts,
        subjectDomains,
        records: scopedRecords,
        groupBy,
        scopeValue,
        dwType,
      });
    },
  });

  return { ...tree, groupBy, setGroupBy, scopeValue, setScopeValue };
}

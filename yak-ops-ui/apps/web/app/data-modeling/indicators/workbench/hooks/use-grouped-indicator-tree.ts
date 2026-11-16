import { useState } from "react";

import { businessTypeApi } from "@/service/data-modeling/planning/business-type";
import { dataDomainApi } from "@/service/data-modeling/planning/common-layer/data-domain";
import { getIndicatorApi } from "@/service/data-modeling/indicators";
import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import type { IndicatorGroupBy, IndicatorKind } from "../types";
import { buildGroupedTree } from "../utils/indicator-tree";
import { useIndicatorTreeView } from "./use-indicator-tree-view";

type GroupedIndicatorKind = Extract<IndicatorKind, "derivativeIndicator" | "composeIndicator">;

export function useGroupedIndicatorTree(
  projectCode: string,
  kind: GroupedIndicatorKind,
  enabled: boolean,
) {
  const [groupBy, setGroupBy] = useState<IndicatorGroupBy>("BUSINESS_TYPE");
  const tree = useIndicatorTreeView({
    projectCode,
    kind,
    queryKeyParts: [groupBy],
    enabled,
    queryFn: async () => {
      const groupApi = groupBy === "BUSINESS_TYPE" ? businessTypeApi : dataDomainApi;
      const [groups, records] = await Promise.all([
        groupApi.all<ModelingRecord>(projectCode),
        getIndicatorApi(kind).all<ModelingRecord>(projectCode),
      ]);
      return buildGroupedTree(groups, records, groupBy);
    },
  });

  return { ...tree, groupBy, setGroupBy };
}

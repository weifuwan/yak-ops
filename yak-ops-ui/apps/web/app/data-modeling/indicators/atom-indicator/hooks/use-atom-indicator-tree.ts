import { useState } from "react";

import { businessTypeApi } from "@/service/data-modeling/planning/business-type";
import { businessProcessApi } from "@/service/data-modeling/planning/common-layer/business-process";
import { dataDomainApi } from "@/service/data-modeling/planning/common-layer/data-domain";
import { atomIndicatorApi } from "@/service/data-modeling/indicators/atom-indicator";
import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import type { IndicatorGroupBy } from "../../workbench/types";
import { buildAtomIndicatorTree } from "../../workbench/utils/indicator-tree";
import { useIndicatorTreeView } from "../../workbench/hooks/use-indicator-tree-view";

export function useAtomIndicatorTree(projectCode: string, dwType: string, enabled: boolean) {
  const [groupBy, setGroupBy] = useState<IndicatorGroupBy>("BUSINESS_TYPE");
  const tree = useIndicatorTreeView({
    projectCode,
    kind: "atomIndicator",
    queryKeyParts: [groupBy, dwType],
    enabled,
    expandRootOnly: true,
    queryFn: async () => {
      const [businessTypes, dataDomains, businessProcesses, atomIndicators] = await Promise.all([
        businessTypeApi.all<ModelingRecord>(projectCode),
        dataDomainApi.all<ModelingRecord>(projectCode),
        businessProcessApi.all<ModelingRecord>(projectCode),
        atomIndicatorApi.all<ModelingRecord>(projectCode, { dwType }),
      ]);
      const relatedDomainMap = groupBy === "BUSINESS_TYPE"
        ? await businessTypeApi.allRelatedDomainIds(projectCode)
        : {};
      return buildAtomIndicatorTree(
        businessTypes,
        dataDomains,
        businessProcesses,
        atomIndicators,
        groupBy,
        new Map(Object.entries(relatedDomainMap)),
      );
    },
  });

  return { ...tree, groupBy, setGroupBy };
}

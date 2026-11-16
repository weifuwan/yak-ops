import { timePeriodApi, timePeriodCategoryApi } from "@/service/data-modeling/indicators/time-period";
import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import { useIndicatorTreeView } from "../../workbench/hooks/use-indicator-tree-view";
import { buildTimePeriodTree } from "../../workbench/utils/indicator-tree";

export function useTimePeriodIndicatorTree(projectCode: string, enabled: boolean) {
  return useIndicatorTreeView({
    projectCode,
    kind: "timePeriod",
    queryKeyParts: [],
    enabled,
    queryFn: async () => {
      const [categories, records] = await Promise.all([
        timePeriodCategoryApi.all<ModelingRecord>(projectCode),
        timePeriodApi.all<ModelingRecord>(projectCode),
      ]);
      return buildTimePeriodTree(categories, records);
    },
  });
}

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";

import { getIndicatorOptionRecords } from "@/service/data-modeling/indicators/options";
import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import type { IndicatorDefinition, IndicatorOptionResource } from "../types";

export function useIndicatorResources(projectCode: string, definition: IndicatorDefinition) {
  const optionResources = useMemo(() => {
    const resources = new Set<IndicatorOptionResource>(["businessType", "dataDomain"]);
    definition.fields.forEach((field) => {
      if (field.optionsSource) resources.add(field.optionsSource);
    });
    return Array.from(resources);
  }, [definition]);

  const optionsQuery = useQuery<Partial<Record<IndicatorOptionResource, ModelingRecord[]>>>({
    queryKey: ["modeling", "indicator-options", projectCode, definition.key],
    queryFn: async () => {
      const entries = await Promise.all(optionResources.map(async (resource) => [
        resource,
        await getIndicatorOptionRecords(projectCode, resource),
      ] as const));
      return Object.fromEntries(entries) as Partial<Record<IndicatorOptionResource, ModelingRecord[]>>;
    },
    enabled: Boolean(projectCode),
  });

  return { optionsByResource: optionsQuery.data || {}, isFetching: optionsQuery.isFetching };
}

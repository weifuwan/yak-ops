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

  const optionsQuery = useQuery<{
    optionsByResource: Partial<Record<IndicatorOptionResource, ModelingRecord[]>>;
    failedResources: IndicatorOptionResource[];
  }>({
    queryKey: ["modeling", "indicator-options", projectCode, definition.key],
    queryFn: async () => {
      const results = await Promise.all(optionResources.map(async (resource) => {
        try {
          return {
            resource,
            records: await getIndicatorOptionRecords(projectCode, resource),
            failed: false,
          };
        } catch {
          return { resource, records: [] as ModelingRecord[], failed: true };
        }
      }));
      return {
        optionsByResource: Object.fromEntries(
          results.map(({ resource, records }) => [resource, records]),
        ) as Partial<Record<IndicatorOptionResource, ModelingRecord[]>>,
        failedResources: results.filter((item) => item.failed).map((item) => item.resource),
      };
    },
    enabled: Boolean(projectCode),
  });

  return {
    optionsByResource: optionsQuery.data?.optionsByResource || {},
    optionsFailedResources: optionsQuery.data?.failedResources || [],
    isFetching: optionsQuery.isFetching,
  };
}

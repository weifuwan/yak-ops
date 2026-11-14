import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";

import { modelingEntityApi, type ModelingRecord, type ModelingResourceKey } from "../../../service";
import { getDefinition } from "../config/indicator-definitions";
import type { IndicatorKind } from "../types";

export function useIndicatorResources(projectCode: string, kind: IndicatorKind) {
  const definition = getDefinition(kind);
  const optionResources = useMemo(() => {
    const resources = new Set<ModelingResourceKey>(["businessType", "dataDomain"]);
    definition.fields.forEach((field) => {
      if (field.optionsSource) resources.add(field.optionsSource);
    });
    return Array.from(resources);
  }, [definition]);

  const optionsQuery = useQuery<Partial<Record<ModelingResourceKey, ModelingRecord[]>>>({
    queryKey: ["modeling", "indicator-options", projectCode, kind],
    queryFn: async () => {
      const entries = await Promise.all(optionResources.map(async (resource) => [
        resource,
        await modelingEntityApi.all<ModelingRecord>(projectCode, resource),
      ] as const));
      return Object.fromEntries(entries) as Partial<Record<ModelingResourceKey, ModelingRecord[]>>;
    },
    enabled: Boolean(projectCode),
  });

  return { optionsByResource: optionsQuery.data || {}, isFetching: optionsQuery.isFetching };
}

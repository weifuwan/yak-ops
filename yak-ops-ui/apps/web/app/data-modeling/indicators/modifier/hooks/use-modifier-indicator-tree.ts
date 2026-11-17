import { useState } from "react";

import { businessTypeApi } from "@/service/data-modeling/planning/business-type";
import { dataDomainApi } from "@/service/data-modeling/planning/common-layer/data-domain";
import { modifierApi } from "@/service/data-modeling/indicators/modifier";
import type { ModelingRecord } from "@/service/data-modeling/shared/types";
import type { ModifierScopeType } from "../../workbench/types";
import { buildModifierTree } from "../../workbench/utils/indicator-tree";
import { useIndicatorTreeView } from "../../workbench/hooks/use-indicator-tree-view";

export function useModifierIndicatorTree(projectCode: string, dwType: string, enabled: boolean) {
  const [modifierScopeType, setModifierScopeType] = useState<ModifierScopeType>("DATA_DOMAIN");
  const [modifierScopeValue, setModifierScopeValue] = useState("");
  const tree = useIndicatorTreeView({
    projectCode,
    kind: "modifier",
    queryKeyParts: [dwType, modifierScopeType, modifierScopeValue],
    enabled,
    queryFn: async () => {
      const groupApi = modifierScopeType === "BUSINESS_TYPE" ? businessTypeApi : dataDomainApi;
      const [groups, modifiers] = await Promise.all([
        groupApi.all<ModelingRecord>(projectCode),
        modifierApi.all<ModelingRecord>(projectCode, dwType === "UNASSIGNED" ? { dwTypeUnassigned: "true" } : { dwType }),
      ]);
      return buildModifierTree(groups, modifiers, modifierScopeType, modifierScopeValue);
    },
  });

  return {
    ...tree,
    modifierScopeType,
    setModifierScopeType,
    modifierScopeValue,
    setModifierScopeValue,
  };
}

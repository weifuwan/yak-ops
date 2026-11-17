import { getWorkflowDefinition } from "@/service/workflow/definition";

import { hasWorkflowReferenceCycle } from "./adapter";

export function hasSubWorkflowCycle(
  currentWorkflowCode: number,
  selectedWorkflowCode: number,
): Promise<boolean> {
  return hasWorkflowReferenceCycle(
    currentWorkflowCode,
    selectedWorkflowCode,
    getWorkflowDefinition,
  );
}

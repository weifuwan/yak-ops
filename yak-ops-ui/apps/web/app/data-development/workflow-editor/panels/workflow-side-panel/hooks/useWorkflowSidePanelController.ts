import { useQueryClient } from "@tanstack/react-query";

import type { WorkflowDagRecord } from "@/service/workflow/definition";
import { useWorkspace } from "@/hooks/use-workspace";

import { useWorkflowProperties } from "./useWorkflowProperties";
import { useWorkflowSchedule } from "./useWorkflowSchedule";
import { useWorkflowInstances } from "./useWorkflowInstances";
import { useWorkflowVersions } from "./useWorkflowVersions";
import type { PanelKey } from "../shared/types";

interface UseWorkflowSidePanelControllerOptions {
  workflowCode: number;
  dag: WorkflowDagRecord;
  activePanel: PanelKey | null;
  onVersionSwitched: () => void;
}

export function useWorkflowSidePanelController({
  workflowCode,
  dag,
  activePanel,
  onVersionSwitched,
}: UseWorkflowSidePanelControllerOptions) {
  const queryClient = useQueryClient();
  const { currentWorkspace } = useWorkspace();
  const workspaceId = currentWorkspace?.id ?? "";
  const definition = dag.workflowDefinition;
  const workflowIsOnline = String(definition.releaseState).toUpperCase() === "ONLINE";

  const invalidateDefinition = async () => {
    await queryClient.invalidateQueries({
      queryKey: ["data-development", "workflow-definition", workspaceId, workflowCode],
    });
    await queryClient.invalidateQueries({
      queryKey: ["data-development", "workflow-explorer"],
    });
    await queryClient.invalidateQueries({
      queryKey: ["workflow-management", "versions", workspaceId, workflowCode],
    });
  };

  const properties = useWorkflowProperties({
    workflowCode,
    definition,
    invalidateDefinition,
  });
  const schedule = useWorkflowSchedule({
    workflowCode,
    workspaceId,
    activePanel,
    workflowIsOnline,
  });
  const instances = useWorkflowInstances({
    workflowCode,
    workspaceId,
    activePanel,
  });
  const versions = useWorkflowVersions({
    workflowCode,
    workspaceId,
    activePanel,
    invalidateDefinition,
    onVersionSwitched,
  });

  const refreshActivePanel = async () => {
    if (activePanel === "properties") {
      properties.resetProperties();
      await invalidateDefinition();
      return;
    }
    if (activePanel === "schedule") {
      await schedule.scheduleQuery.refetch();
      return;
    }
    if (activePanel === "history" || activePanel === "ops") {
      await instances.instancesQuery.refetch();
      return;
    }
    if (activePanel === "versions") {
      await versions.versionsQuery.refetch();
    }
  };

  return {
    workflowCode,
    definition,
    workflowIsOnline,
    ...properties,
    ...schedule,
    ...instances,
    ...versions,
    refreshActivePanel,
  };
}

export type WorkflowSidePanelController = ReturnType<
  typeof useWorkflowSidePanelController
>;

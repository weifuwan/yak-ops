import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "@yak-ops/yak-ui";

import {
  listWorkflowDefinitionVersions,
  switchWorkflowDefinitionVersion,
} from "@/service/workflow/management";
import type { PanelKey } from "../shared/types";

interface UseWorkflowVersionsOptions {
  workflowCode: number;
  workspaceId: string;
  activePanel: PanelKey | null;
  invalidateDefinition: () => Promise<void>;
  onVersionSwitched: () => void;
}

export function useWorkflowVersions({
  workflowCode,
  workspaceId,
  activePanel,
  invalidateDefinition,
  onVersionSwitched,
}: UseWorkflowVersionsOptions) {
  const [confirmVersion, setConfirmVersion] = useState<number | null>(null);
  const [switchingVersion, setSwitchingVersion] = useState(false);

  const queryClient = useQueryClient();

  const versionsQuery = useQuery({
    queryKey: ["workflow-management", "versions", workspaceId, workflowCode],
    queryFn: ({ signal }) => listWorkflowDefinitionVersions(workflowCode, signal),
    enabled: Boolean(workspaceId && activePanel === "versions"),
  });

  const switchVersion = async (version: number) => {
    setSwitchingVersion(true);
    try {
      await switchWorkflowDefinitionVersion(workflowCode, version);
      setConfirmVersion(null);
      await invalidateDefinition();
      await queryClient.invalidateQueries({
        queryKey: ["workflow-management", "schedule", workspaceId, workflowCode],
      });
      await versionsQuery.refetch();
      onVersionSwitched();
      toast.success("已切换到版本 V" + version);
    } catch (error) {
      toast.error("切换版本失败", {
        description: error instanceof Error ? error.message : "请稍后重试",
      });
    } finally {
      setSwitchingVersion(false);
    }
  };

  const versions = versionsQuery.data?.totalList ?? [];

  return {
    confirmVersion,
    setConfirmVersion,
    switchingVersion,
    versionsQuery,
    versions,
    switchVersion,
  };
}

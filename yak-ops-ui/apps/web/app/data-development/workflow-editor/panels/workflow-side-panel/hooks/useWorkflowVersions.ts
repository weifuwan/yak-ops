import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "@yak-ops/yak-ui";

import {
  listWorkflowDefinitionVersions,
  switchWorkflowDefinitionVersion,
} from "@/service/workflow/management";
import type { PanelKey } from "../shared/types";

const VERSION_PAGE_SIZE_OPTIONS = [10, 20, 50, 100];

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
  const [pagination, setPagination] = useState({
    workflowCode,
    pageNo: 1,
    pageSize: VERSION_PAGE_SIZE_OPTIONS[0],
  });

  const pageNo = pagination.workflowCode === workflowCode ? pagination.pageNo : 1;
  const pageSize =
    pagination.workflowCode === workflowCode
      ? pagination.pageSize
      : VERSION_PAGE_SIZE_OPTIONS[0];

  const queryClient = useQueryClient();

  const versionsQuery = useQuery({
    queryKey: [
      "workflow-management",
      "versions",
      workspaceId,
      workflowCode,
      pageNo,
      pageSize,
    ],
    queryFn: ({ signal }) =>
      listWorkflowDefinitionVersions(workflowCode, pageNo, pageSize, signal),
    enabled: Boolean(workspaceId && activePanel === "versions"),
  });

  const onVersionsPageChange = (nextPageNo: number, nextPageSize: number) => {
    setPagination({
      workflowCode,
      pageNo: nextPageSize === pageSize ? nextPageNo : 1,
      pageSize: nextPageSize,
    });
  };

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
    versionsPageNo: pageNo,
    versionsPageSize: pageSize,
    versionsTotal: versionsQuery.data?.total ?? 0,
    onVersionsPageChange,
    switchVersion,
  };
}

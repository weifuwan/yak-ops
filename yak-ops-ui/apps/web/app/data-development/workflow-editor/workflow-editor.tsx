import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Button } from "@yak-ops/yak-ui";

import { useWorkspace } from "@/hooks/use-workspace";
import { getWorkflowDefinition } from "@/service/workflow/definition";

import { WorkflowNodePalette } from "./node-palette";
import { WorkflowCanvas } from "./workflow-canvas";
import { WorkflowToolbar } from "./workflow-toolbar";

type WorkflowEditorProps = {
  workflowCode: number;
  selectedTaskCode?: number;
};

export function WorkflowEditor({
  workflowCode,
  selectedTaskCode,
}: WorkflowEditorProps) {
  const { currentWorkspace } = useWorkspace();
  const workspaceId = currentWorkspace?.id ?? "";
  const [paletteOpen, setPaletteOpen] = useState(false);

  const query = useQuery({
    queryKey: [
      "data-development",
      "workflow-definition",
      workspaceId,
      workflowCode,
    ],
    queryFn: ({ signal }) => getWorkflowDefinition(workflowCode, signal),
    enabled: Boolean(workspaceId && workflowCode),
  });

  if (query.isPending) {
    return (
      <div className="flex min-h-0 flex-1 items-center justify-center text-sm text-[var(--yak-components-muted-text)]">
        正在加载工作流…
      </div>
    );
  }

  if (query.isError) {
    return (
      <div className="flex min-h-0 flex-1 items-center justify-center">
        <div className="text-center">
          <div className="text-sm text-[var(--yak-color-danger)]">
            工作流加载失败
          </div>
          <Button
            size="sm"
            variant="secondary"
            className="mt-3"
            onClick={() => void query.refetch()}
          >
            重试
          </Button>
        </div>
      </div>
    );
  }

  const tasks = query.data.taskDefinitionList ?? [];
  const relations = query.data.workflowTaskRelationList ?? [];

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <WorkflowToolbar
        paletteOpen={paletteOpen}
        onTogglePalette={() => setPaletteOpen((open) => !open)}
      />
      <div className="flex min-h-0 flex-1 overflow-hidden">
        {paletteOpen ? (
          <WorkflowNodePalette onClose={() => setPaletteOpen(false)} />
        ) : null}
        <WorkflowCanvas
          tasks={tasks}
          relations={relations}
          selectedTaskCode={selectedTaskCode}
        />
      </div>
    </div>
  );
}

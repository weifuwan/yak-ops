import { useQuery } from "@tanstack/react-query";
import { Workflow } from "lucide-react";
import { Button } from "@yak-ops/yak-ui";

import { useWorkspace } from "@/hooks/use-workspace";
import { getWorkflowDefinition } from "@/service/workflow/definition";

import { WorkflowCanvas } from "./workflow-canvas";

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

  if (tasks.length === 0) {
    return (
      <div className="flex min-h-0 flex-1 items-center justify-center text-center">
        <div>
          <Workflow
            size={24}
            aria-hidden="true"
            className="mx-auto text-[var(--yak-components-muted-text)]"
          />
          <div className="mt-3 text-sm font-medium">暂无工作流节点</div>
          <div className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
            当前工作流定义中还没有任务节点。
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-0 flex-1">
      <WorkflowCanvas
        tasks={tasks}
        relations={relations}
        selectedTaskCode={selectedTaskCode}
      />
    </div>
  );
}

import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  ReactFlowProvider,
} from "reactflow";
import { Button } from "@yak-ops/yak-ui";

import { useWorkspace } from "@/hooks/use-workspace";
import {
  getWorkflowDefinition,
  type WorkflowDagRecord,
} from "@/service/workflow/definition";

import { workflowDagToGraph } from "./adapter/workflow-adapter";
import { WorkflowCanvas } from "./canvas/workflow-canvas";
import type { CanvasPanelKey } from "./canvas/canvas-side-panel";
import { layoutWorkflowGraph } from "./layout/elk-layout";
import { WorkflowNodeSelector } from "./node-selector/node-selector";
import {
  WorkflowStoreProvider,
  useWorkflowStore,
} from "./store/workflow-store";
import { WorkflowToolbar } from "./toolbar/workflow-toolbar";
import { WorkflowSidePanel } from "./panels/workflow-side-panel";

type WorkflowEditorProps = {
  workflowCode: number;
  selectedTaskCode?: number;
};

function WorkflowEditorShell({
  workflowCode,
  selectedTaskCode,
  workflowReleaseState,
  dag,
  onVersionSwitched,
  onRefreshSpec,
  isRefreshingSpec,
}: {
  workflowCode: number;
  selectedTaskCode?: number;
  workflowReleaseState: string;
  dag: WorkflowDagRecord;
  onVersionSwitched: () => void;
  onRefreshSpec: () => Promise<void>;
  isRefreshingSpec: boolean;
}) {
  const selectorOpen = useWorkflowStore(
    (state) => Boolean(state.selectorRequest),
  );
  const [activeCanvasPanel, setActiveCanvasPanel] =
    useState<CanvasPanelKey | null>(null);

  useEffect(() => {
    if (!activeCanvasPanel) return;

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setActiveCanvasPanel(null);
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [activeCanvasPanel]);

  const toggleCanvasPanel = (panel: CanvasPanelKey) => {
    setActiveCanvasPanel((current) =>
      current === panel ? null : panel,
    );
  };

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <WorkflowToolbar
        activePanel={activeCanvasPanel}
        onTogglePanel={toggleCanvasPanel}
      />
      <div className="flex min-h-0 flex-1 overflow-hidden">
        {selectorOpen ? (
          <WorkflowNodeSelector />
        ) : null}
        <WorkflowCanvas
          workflowCode={workflowCode}
          selectedTaskCode={selectedTaskCode}
          workflowReleaseState={workflowReleaseState}
          activeCanvasPanel={activeCanvasPanel}
          onCloseCanvasPanel={() => setActiveCanvasPanel(null)}
          dag={dag}
          onRefreshSpec={onRefreshSpec}
          isRefreshingSpec={isRefreshingSpec}
        />
        <WorkflowSidePanel
          workflowCode={workflowCode}
          dag={dag}
          onVersionSwitched={onVersionSwitched}
        />
      </div>
    </div>
  );
}

export function WorkflowEditor({
  workflowCode,
  selectedTaskCode,
}: WorkflowEditorProps) {
  const { currentWorkspace } = useWorkspace();
  const workspaceId = currentWorkspace?.id ?? "";
  const [graphReloadToken, setGraphReloadToken] = useState(0);

  const query = useQuery({
    queryKey: [
      "data-development",
      "workflow-definition",
      workspaceId,
      workflowCode,
    ],
    queryFn: ({ signal }) =>
      getWorkflowDefinition(workflowCode, signal),
    enabled: Boolean(workspaceId && workflowCode),
  });

  const sourceGraph = useMemo(
    () =>
      query.data
        ? workflowDagToGraph(query.data)
        : null,
    [query.data],
  );

  const layoutQuery = useQuery({
    queryKey: [
      "data-development",
      "workflow-definition-layout",
      workspaceId,
      workflowCode,
      graphReloadToken,
    ],
    queryFn: () => layoutWorkflowGraph(sourceGraph!),
    enabled: Boolean(sourceGraph),
    staleTime: Infinity,
  });

  if (
    query.isError ||
    layoutQuery.isError
  ) {
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
            onClick={() => {
              void query.refetch();
              if (sourceGraph) void layoutQuery.refetch();
            }}
          >
            重试
          </Button>
        </div>
      </div>
    );
  }

  if (query.isPending || layoutQuery.isPending || !layoutQuery.data) {
    return (
      <div className="flex min-h-0 flex-1 items-center justify-center text-sm text-[var(--yak-components-muted-text)]">
        正在加载工作流…
      </div>
    );
  }

  return (
    <WorkflowStoreProvider
      key={workflowCode + ":" + graphReloadToken}
      initialGraph={layoutQuery.data}
      workflowCode={workflowCode}
    >
      <ReactFlowProvider>
        <WorkflowEditorShell
          workflowCode={workflowCode}
          selectedTaskCode={selectedTaskCode}
          workflowReleaseState={query.data?.workflowDefinition.releaseState ?? "OFFLINE"}
          dag={query.data!}
          onVersionSwitched={() =>
            setGraphReloadToken((current) => current + 1)
          }
          onRefreshSpec={async () => {
            const result = await query.refetch();
            if (result.isError) throw result.error;
          }}
          isRefreshingSpec={query.isFetching}
        />
      </ReactFlowProvider>
    </WorkflowStoreProvider>
  );
}

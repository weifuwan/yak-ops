import { useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { ReactFlowProvider } from "reactflow";
import { Button, toast } from "@yak-ops/yak-ui";

import { useWorkspace } from "@/hooks/use-workspace";
import {
  getWorkflowDefinition,
  releaseWorkflowDefinition,
  saveWorkflowDefinitionGraph,
  type WorkflowDagRecord,
  type WorkflowTaskRecord,
  type WorkflowTaskRelationRecord,
} from "@/service/workflow/definition";

import { useDataDevelopmentWorkbench } from "../workbench-context";
import { workflowDagToGraph } from "./adapter/workflow-adapter";
import { WorkflowCanvas } from "./canvas/workflow-canvas";
import type { CanvasPanelKey } from "./canvas/canvas-side-panel";
import { layoutWorkflowGraph } from "./layout/elk-layout";
import { WorkflowNodeSelector } from "./node-selector/node-selector";
import {
  WorkflowStoreProvider,
  useWorkflowStore,
  useWorkflowStoreApi,
} from "./store/workflow-store";
import type { WorkflowCanvasEdge, WorkflowCanvasNode } from "./types";
import { WorkflowToolbar } from "./toolbar/workflow-toolbar";
import { WorkflowSidePanel } from "./panels/workflow-side-panel";

type WorkflowEditorProps = {
  workflowCode: number;
  selectedTaskCode?: number;
};

function buildWorkflowRelations(
  dag: WorkflowDagRecord,
  nodes: WorkflowCanvasNode[],
  edges: WorkflowCanvasEdge[],
): WorkflowTaskRelationRecord[] {
  const taskByCode = new Map<number, WorkflowTaskRecord>();
  for (const node of nodes) {
    const task = node.data.task;
    if (!task || node.data.taskCode === null) {
      throw new Error("节点“" + node.data.name + "”尚未完成初始化");
    }
    taskByCode.set(node.data.taskCode, {
      ...task,
      code: node.data.taskCode,
      name: node.data.name,
      taskType: node.data.taskType,
    });
  }

  const makeRelation = (
    preTaskCode: number,
    postTaskCode: number,
    existing?: WorkflowTaskRelationRecord,
  ): WorkflowTaskRelationRecord => ({
    id: existing?.id ?? 0,
    name: existing?.name ?? "",
    workflowDefinitionVersion: dag.workflowDefinition.version,
    projectCode: dag.workflowDefinition.projectCode,
    workflowDefinitionCode: dag.workflowDefinition.code,
    preTaskCode,
    preTaskVersion: preTaskCode === 0 ? 0 : taskByCode.get(preTaskCode)?.version ?? 0,
    postTaskVersion: taskByCode.get(postTaskCode)?.version ?? 0,
    postTaskCode,
    conditionType: existing?.conditionType ?? 0,
    conditionParams: existing?.conditionParams ?? "{}",
  });

  const relations = edges.map((edge) =>
    makeRelation(Number(edge.source), Number(edge.target), edge.data?.relation),
  );
  const incoming = new Set(edges.map((edge) => edge.target));
  for (const node of nodes) {
    if (incoming.has(node.id) || node.data.taskCode === null) continue;
    relations.push(makeRelation(0, node.data.taskCode));
  }
  return relations;
}

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
  const selectorOpen = useWorkflowStore((state) => Boolean(state.selectorRequest));
  const storeApi = useWorkflowStoreApi();
  const queryClient = useQueryClient();
  const {
    registerWorkflowStore,
    syncWorkflowTaskRecords,
    updateWorkflowDefinition,
    updateWorkflowReleaseState,
  } = useDataDevelopmentWorkbench();
  const [activeCanvasPanel, setActiveCanvasPanel] = useState<CanvasPanelKey | null>(null);
  const [saving, setSaving] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const workflowIsOnline = workflowReleaseState.toUpperCase() === "ONLINE";

  useEffect(
    () => registerWorkflowStore(workflowCode, storeApi),
    [registerWorkflowStore, storeApi, workflowCode],
  );

  useEffect(() => {
    if (!activeCanvasPanel) return;
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setActiveCanvasPanel(null);
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [activeCanvasPanel]);

  const invalidateWorkflowQueries = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["data-development", "workflow-definition"] }),
      queryClient.invalidateQueries({ queryKey: ["data-development", "workflow-explorer"] }),
      queryClient.invalidateQueries({ queryKey: ["workflow-management", "versions"] }),
    ]);
  };

  const saveWorkflow = async (): Promise<boolean> => {
    if (workflowIsOnline) {
      toast.warning("工作流已上线，请先下线再修改");
      return false;
    }
    setSaving(true);
    try {
      const state = storeApi.getState();
      if (state.nodes.length === 0) {
        toast.warning("请至少添加一个工作流节点");
        return false;
      }

      const taskDefinitionList = state.nodes.map((node) => {
        const task = node.data.task;
        if (!task || node.data.taskCode === null) {
          throw new Error("节点“" + node.data.name + "”尚未完成初始化");
        }
        return {
          ...task,
          code: node.data.taskCode,
          name: node.data.name,
          taskType: node.data.taskType,
        };
      });

      const workflowTaskRelationList = buildWorkflowRelations(dag, state.nodes, state.edges);
      if (workflowTaskRelationList.length === 0) {
        throw new Error("工作流依赖关系为空，请检查节点连接");
      }
      const locations = JSON.stringify(state.nodes.map((node) => ({
        taskCode: node.data.taskCode,
        x: node.position.x,
        y: node.position.y,
      })));

      const savedDag = await saveWorkflowDefinitionGraph(workflowCode, {
        taskDefinitionList,
        workflowTaskRelationList,
        locations,
      });

      storeApi.getState().replaceGraph(workflowDagToGraph(savedDag));
      syncWorkflowTaskRecords(workflowCode, savedDag.taskDefinitionList);
      updateWorkflowDefinition(savedDag.workflowDefinition);
      await invalidateWorkflowQueries();
      toast.success("工作流保存成功");
      return true;
    } catch (error) {
      toast.error("保存工作流失败", {
        description: error instanceof Error ? error.message : "请稍后重试。",
      });
      return false;
    } finally {
      setSaving(false);
    }
  };

  const handlePublish = async () => {
    setPublishing(true);
    try {
      if (workflowIsOnline) {
        await releaseWorkflowDefinition(workflowCode, "OFFLINE");
        updateWorkflowReleaseState(workflowCode, "OFFLINE");
        await invalidateWorkflowQueries();
        toast.success("工作流已下线，可以继续编辑");
        return;
      }

      const saved = await saveWorkflow();
      if (!saved) return;
      await releaseWorkflowDefinition(workflowCode, "ONLINE");
      updateWorkflowReleaseState(workflowCode, "ONLINE");
      await invalidateWorkflowQueries();
      toast.success("工作流发布成功");
    } catch (error) {
      toast.error(workflowIsOnline ? "工作流下线失败" : "工作流发布失败", {
        description: error instanceof Error ? error.message : "请稍后重试。",
      });
    } finally {
      setPublishing(false);
    }
  };

  const toggleCanvasPanel = (panel: CanvasPanelKey) => {
    setActiveCanvasPanel((current) => current === panel ? null : panel);
  };

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <WorkflowToolbar
        activePanel={activeCanvasPanel}
        onTogglePanel={toggleCanvasPanel}
        onSave={() => void saveWorkflow()}
        onPublish={() => void handlePublish()}
        saving={saving}
        publishing={publishing}
        workflowIsOnline={workflowIsOnline}
      />
      <div className="flex min-h-0 flex-1 overflow-hidden">
        {selectorOpen && !workflowIsOnline ? <WorkflowNodeSelector /> : null}
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

export function WorkflowEditor({ workflowCode, selectedTaskCode }: WorkflowEditorProps) {
  const { currentWorkspace } = useWorkspace();
  const workspaceId = currentWorkspace?.id ?? "";
  const [graphReloadToken, setGraphReloadToken] = useState(0);
  const query = useQuery({
    queryKey: ["data-development", "workflow-definition", workspaceId, workflowCode],
    queryFn: ({ signal }) => getWorkflowDefinition(workflowCode, signal),
    enabled: Boolean(workspaceId && workflowCode),
  });

  const sourceGraph = useMemo(
    () => query.data ? workflowDagToGraph(query.data) : null,
    [query.data],
  );
  const layoutQuery = useQuery({
    queryKey: ["data-development", "workflow-definition-layout", workspaceId, workflowCode, graphReloadToken],
    queryFn: () => layoutWorkflowGraph(sourceGraph!),
    enabled: Boolean(sourceGraph),
    staleTime: Infinity,
  });

  if (query.isError || layoutQuery.isError) {
    return (
      <div className="flex min-h-0 flex-1 items-center justify-center">
        <div className="text-center">
          <div className="text-sm text-[var(--yak-color-danger)]">工作流加载失败</div>
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
          workflowReleaseState={query.data.workflowDefinition.releaseState ?? "OFFLINE"}
          dag={query.data}
          onVersionSwitched={() => setGraphReloadToken((current) => current + 1)}
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

import {
  useEffect,
  useMemo,
  useState,
  type DragEvent,
} from "react";
import ReactFlow, {
  Background,
  BackgroundVariant,
  Controls,
  SelectionMode,
  type ReactFlowInstance,
} from "reactflow";
import "reactflow/dist/style.css";
import {
  Hand,
  MousePointer2,
} from "lucide-react";

import { WORKFLOW_TASK_DRAG_TYPE } from "../constants";
import { useWorkflowInteractions } from "../hooks/use-workflow-interactions";
import {
  useWorkflowStore,
} from "../store/workflow-store";
import type {
  WorkflowCanvasEdge,
  WorkflowCanvasNode,
  WorkflowEdgeData,
  WorkflowNodeData,
} from "../types";
import { WorkflowConnectionLine } from "./connection-line";
import { WorkflowEdge } from "./workflow-edge";
import { WorkflowTaskNode } from "./workflow-node";

const nodeTypes = {
  workflowTask: WorkflowTaskNode,
};

const edgeTypes = {
  workflowEdge: WorkflowEdge,
};

export function WorkflowCanvas({
  workflowCode,
  selectedTaskCode,
}: {
  workflowCode: number;
  selectedTaskCode?: number;
}) {
  const nodes = useWorkflowStore(
    (state) => state.nodes,
  );
  const edges = useWorkflowStore(
    (state) => state.edges,
  );
  const interactionMode = useWorkflowStore(
    (state) => state.interactionMode,
  );
  const onNodesChange = useWorkflowStore(
    (state) => state.onNodesChange,
  );
  const onEdgesChange = useWorkflowStore(
    (state) => state.onEdgesChange,
  );
  const setInteractionMode = useWorkflowStore(
    (state) => state.setInteractionMode,
  );
  const addDroppedNode = useWorkflowStore(
    (state) => state.addDroppedNode,
  );
  const selectTaskCode = useWorkflowStore(
    (state) => state.selectTaskCode,
  );
  const clearSelection = useWorkflowStore(
    (state) => state.clearSelection,
  );

  const {
    handleConnect,
    handleNodesDelete,
    handleEdgesDelete,
    isValidConnection,
  } = useWorkflowInteractions();

  const [instance, setInstance] = useState<
    ReactFlowInstance<
      WorkflowNodeData,
      WorkflowEdgeData
    > | null
  >(null);
  const [hoveredEdgeId, setHoveredEdgeId] =
    useState<string | null>(null);
  const renderEdges = useMemo(
    () =>
      edges.map((edge) => ({
        ...edge,
        data: {
          backend: edge.data?.backend ?? false,
          hovered: edge.id === hoveredEdgeId,
        },
      })),
    [edges, hoveredEdgeId],
  );

  useEffect(() => {
    if (!instance || !selectedTaskCode) return;

    const nodeId = selectTaskCode(selectedTaskCode);
    if (!nodeId) return;

    const node = instance.getNode(nodeId);
    if (!node) return;

    void instance.fitView({
      nodes: [node],
      padding: 1.6,
      maxZoom: 1.15,
    });
  }, [instance, selectTaskCode, selectedTaskCode]);

  const handleDragOver = (
    event: DragEvent<HTMLDivElement>,
  ) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = "copy";
  };

  const handleDrop = (
    event: DragEvent<HTMLDivElement>,
  ) => {
    event.preventDefault();
    if (!instance) return;

    const raw = event.dataTransfer.getData(
      WORKFLOW_TASK_DRAG_TYPE,
    );
    if (!raw) return;

    let taskType = "";
    try {
      const payload = JSON.parse(raw) as {
        taskType?: string;
      };
      taskType = payload.taskType ?? "";
    } catch {
      return;
    }

    if (!taskType) return;

    addDroppedNode(
      taskType,
      instance.screenToFlowPosition({
        x: event.clientX,
        y: event.clientY,
      }),
    );
  };

  return (
    <div className="relative h-full min-h-0 min-w-0 flex-1 bg-[#f2f4f7]">
      <ReactFlow
        id={`workflow-${workflowCode}`}
        nodes={nodes}
        edges={renderEdges}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        onInit={(flow) => {
          setInstance(flow);

          if (selectedTaskCode) {
            const selectedNode = nodes.find(
              (node) =>
                node.data.taskCode === selectedTaskCode,
            );
            if (selectedNode) {
              void flow.fitView({
                nodes: [selectedNode],
                padding: 1.6,
                maxZoom: 1.15,
              });
              return;
            }
          }

          if (nodes.length > 0) {
            void flow.fitView({
              padding: 0.22,
              maxZoom: 1,
            });
          }
        }}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onNodesDelete={handleNodesDelete}
        onEdgesDelete={handleEdgesDelete}
        onConnect={handleConnect}
        isValidConnection={isValidConnection}
        onPaneClick={clearSelection}
        onEdgeMouseEnter={(_event, edge) =>
          setHoveredEdgeId(edge.id)
        }
        onEdgeMouseLeave={() =>
          setHoveredEdgeId(null)
        }
        onDragOver={handleDragOver}
        onDrop={handleDrop}
        nodesDraggable={
          interactionMode === "pointer"
        }
        nodesConnectable={
          interactionMode === "pointer"
        }
        elementsSelectable={
          interactionMode === "pointer"
        }
        panOnDrag={interactionMode === "pan"}
        selectionOnDrag={
          interactionMode === "pointer"
        }
        selectionMode={SelectionMode.Partial}
        edgesFocusable
        connectOnClick={false}
        connectionLineComponent={WorkflowConnectionLine}
        connectionRadius={24}
        deleteKeyCode={["Backspace", "Delete"]}
        minZoom={0.25}
        maxZoom={1.8}
        proOptions={{ hideAttribution: true }}
      >
        <Background
          variant={BackgroundVariant.Dots}
          gap={16}
          size={1.5}
          color="#C5CBD5"
        />
        <Controls
          position="top-right"
          showInteractive={false}
          className="!top-12"
          fitViewOptions={{
            padding: 0.22,
            maxZoom: 1,
          }}
        />
      </ReactFlow>

      <div className="absolute right-3 top-3 z-10 flex overflow-hidden rounded-md border border-[var(--yak-components-border)] bg-white shadow-sm">
        <button
          type="button"
          title="指针模式"
          aria-label="指针模式"
          aria-pressed={
            interactionMode === "pointer"
          }
          onClick={() =>
            setInteractionMode("pointer")
          }
          className={[
            "flex size-8 cursor-pointer items-center justify-center border-r border-[var(--yak-components-border)] transition-colors",
            interactionMode === "pointer"
              ? "bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]"
              : "text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)]",
          ].join(" ")}
        >
          <MousePointer2
            size={15}
            strokeWidth={1.7}
          />
        </button>
        <button
          type="button"
          title="抓手模式"
          aria-label="抓手模式"
          aria-pressed={interactionMode === "pan"}
          onClick={() =>
            setInteractionMode("pan")
          }
          className={[
            "flex size-8 cursor-pointer items-center justify-center transition-colors",
            interactionMode === "pan"
              ? "bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]"
              : "text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)]",
          ].join(" ")}
        >
          <Hand size={15} strokeWidth={1.7} />
        </button>
      </div>

      {nodes.length === 0 ? (
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
          <div className="rounded-lg border border-dashed border-[var(--yak-components-border)] bg-white/85 px-5 py-4 text-center shadow-sm">
            <div className="text-sm font-medium">
              暂无工作流节点
            </div>
            <div className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
              点击“添加节点”，选择或拖入一个节点类型。
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

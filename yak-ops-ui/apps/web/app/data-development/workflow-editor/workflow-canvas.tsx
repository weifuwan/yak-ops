import {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from "react";
import {
  Background,
  BackgroundVariant,
  Controls,
  ReactFlow,
  type Edge,
  type ReactFlowInstance,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { toast } from "@yak-ops/yak-ui";

import type {
  WorkflowTaskRecord,
  WorkflowTaskRelationRecord,
} from "@/service/workflow/definition";

import { buildWorkflowCanvas } from "./workflow-layout";
import {
  WorkflowTaskNode,
  type WorkflowCanvasNode,
  type WorkflowNodeAction,
} from "./workflow-node";

const nodeTypes = {
  workflowTask: WorkflowTaskNode,
};

type WorkflowCanvasProps = {
  tasks: WorkflowTaskRecord[];
  relations: WorkflowTaskRelationRecord[];
  selectedTaskCode?: number;
};

const actionLabel: Record<WorkflowNodeAction, string> = {
  "create-upstream": "新建上游",
  open: "打开节点",
  delete: "删除节点",
  "create-downstream": "新建下游",
};

export function WorkflowCanvas({
  tasks,
  relations,
  selectedTaskCode,
}: WorkflowCanvasProps) {
  const [instance, setInstance] =
    useState<ReactFlowInstance<WorkflowCanvasNode, Edge> | null>(null);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(
    selectedTaskCode ? String(selectedTaskCode) : null,
  );

  const canvas = useMemo(
    () => buildWorkflowCanvas(tasks, relations),
    [tasks, relations],
  );

  useEffect(() => {
    if (selectedTaskCode) {
      setSelectedNodeId(String(selectedTaskCode));
      return;
    }

    setSelectedNodeId((current) =>
      current && canvas.nodes.some((node) => node.id === current)
        ? current
        : null,
    );
  }, [canvas.nodes, selectedTaskCode]);

  const handleNodeAction = useCallback(
    (action: WorkflowNodeAction, taskCode: number) => {
      setSelectedNodeId(String(taskCode));

      if (action === "open") {
        toast.info("节点已选中", {
          description: "节点详情编辑将在后续接入。",
        });
        return;
      }

      toast.info(actionLabel[action], {
        description: "节点编辑动作已接入，后续将连接工作流保存接口。",
      });
    },
    [],
  );

  const nodes = useMemo(
    () =>
      canvas.nodes.map((node) => ({
        ...node,
        selected: node.id === selectedNodeId,
        data: {
          ...node.data,
          onAction: handleNodeAction,
        },
      })),
    [canvas.nodes, handleNodeAction, selectedNodeId],
  );

  useEffect(() => {
    if (!instance || nodes.length === 0) return;

    const selectedNode = selectedNodeId
      ? nodes.find((node) => node.id === selectedNodeId)
      : undefined;

    const frame = window.requestAnimationFrame(() => {
      if (selectedTaskCode && selectedNode) {
        void instance.fitView({
          nodes: [selectedNode],
          duration: 220,
          padding: 1.8,
          maxZoom: 1.15,
        });
        return;
      }

      if (!selectedNodeId) {
        void instance.fitView({
          duration: 220,
          padding: 0.28,
          maxZoom: 1,
        });
      }
    });

    return () => window.cancelAnimationFrame(frame);
  }, [instance, nodes, selectedNodeId, selectedTaskCode]);

  return (
    <div className="h-full min-h-0 w-full bg-[#fbfcfe]">
      <ReactFlow<WorkflowCanvasNode, Edge>
        nodes={nodes}
        edges={canvas.edges}
        nodeTypes={nodeTypes}
        onInit={setInstance}
        onNodeClick={(_event, node) => setSelectedNodeId(node.id)}
        onPaneClick={() => setSelectedNodeId(null)}
        fitView
        fitViewOptions={{ padding: 0.28, maxZoom: 1 }}
        nodesDraggable={false}
        nodesConnectable={false}
        edgesFocusable={false}
        connectOnClick={false}
        deleteKeyCode={null}
        minZoom={0.25}
        maxZoom={1.8}
        proOptions={{ hideAttribution: true }}
      >
        <Background
          variant={BackgroundVariant.Dots}
          gap={16}
          size={1}
          color="#dfe4ec"
        />
        <Controls
          position="top-right"
          showInteractive={false}
          fitViewOptions={{ padding: 0.28, maxZoom: 1 }}
        />
      </ReactFlow>
    </div>
  );
}

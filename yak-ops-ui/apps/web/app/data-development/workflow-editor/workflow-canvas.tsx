import { useEffect, useMemo, useState } from "react";
import {
  Background,
  BackgroundVariant,
  Controls,
  ReactFlow,
  type Edge,
  type ReactFlowInstance,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";

import type {
  WorkflowTaskRecord,
  WorkflowTaskRelationRecord,
} from "@/service/workflow/definition";

import { buildWorkflowCanvas } from "./workflow-layout";
import {
  WorkflowTaskNode,
  type WorkflowCanvasNode,
} from "./workflow-node";

const nodeTypes = {
  workflowTask: WorkflowTaskNode,
};

type WorkflowCanvasProps = {
  tasks: WorkflowTaskRecord[];
  relations: WorkflowTaskRelationRecord[];
  selectedTaskCode?: number;
};

export function WorkflowCanvas({
  tasks,
  relations,
  selectedTaskCode,
}: WorkflowCanvasProps) {
  const [instance, setInstance] =
    useState<ReactFlowInstance<WorkflowCanvasNode, Edge> | null>(null);

  const { nodes, edges } = useMemo(
    () => buildWorkflowCanvas(tasks, relations, selectedTaskCode),
    [tasks, relations, selectedTaskCode],
  );

  useEffect(() => {
    if (!instance || nodes.length === 0) return;

    const selectedNode = selectedTaskCode
      ? nodes.find((node) => node.id === String(selectedTaskCode))
      : undefined;

    const frame = window.requestAnimationFrame(() => {
      if (selectedNode) {
        void instance.fitView({
          nodes: [selectedNode],
          duration: 220,
          padding: 1.6,
          maxZoom: 1.15,
        });
        return;
      }

      void instance.fitView({
        duration: 220,
        padding: 0.24,
        maxZoom: 1,
      });
    });

    return () => window.cancelAnimationFrame(frame);
  }, [instance, nodes, selectedTaskCode]);

  return (
    <div className="h-full min-h-0 w-full bg-[#fbfcfe]">
      <ReactFlow<WorkflowCanvasNode, Edge>
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        onInit={setInstance}
        fitView
        fitViewOptions={{ padding: 0.24, maxZoom: 1 }}
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
          fitViewOptions={{ padding: 0.24, maxZoom: 1 }}
        />
      </ReactFlow>
    </div>
  );
}

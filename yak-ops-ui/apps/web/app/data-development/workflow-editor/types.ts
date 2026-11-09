import type { Edge, Node, XYPosition } from "@xyflow/react";

import type { WorkflowTaskRecord } from "@/service/workflow/definition";

export type WorkflowInteractionMode = "pointer" | "pan";

export type WorkflowSelectorRequest =
  | { kind: "add" }
  | { kind: "upstream"; nodeId: string }
  | { kind: "downstream"; nodeId: string }
  | { kind: "edge"; edgeId: string };

export type WorkflowNodeData = {
  taskCode: number | null;
  name: string;
  taskType: string;
  draft: boolean;
  task?: WorkflowTaskRecord;
};

export type WorkflowEdgeData = {
  backend: boolean;
  hovered?: boolean;
};

export type WorkflowCanvasNode = Node<WorkflowNodeData, "workflowTask">;
export type WorkflowCanvasEdge = Edge<WorkflowEdgeData, "workflowEdge">;

export type WorkflowGraph = {
  nodes: WorkflowCanvasNode[];
  edges: WorkflowCanvasEdge[];
};

export type WorkflowNodePositionMap = Record<string, XYPosition>;

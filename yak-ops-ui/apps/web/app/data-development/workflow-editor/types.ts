import type { Edge, Node, XYPosition } from "reactflow";

import type {
  WorkflowTaskRecord,
  WorkflowTaskRelationRecord,
} from "@/service/workflow/definition";

export type WorkflowInteractionMode = "pointer" | "pan";

export type WorkflowEdgeStyle = "bezier" | "step" | "smoothstep";

export type WorkflowSelectorRequest =
  | { kind: "add" }
  | { kind: "upstream"; nodeId: string }
  | { kind: "downstream"; nodeId: string; sourceHandle?: string };

export type WorkflowEdgeSelectorRequest = {
  kind: "edge";
  edgeId: string;
  anchor: XYPosition;
};

export type WorkflowPopoverSelectorRequest =
  | WorkflowEdgeSelectorRequest
  | { kind: "upstream-popover"; nodeId: string; anchor: XYPosition }
  | {
      kind: "downstream-popover";
      nodeId: string;
      sourceHandle?: string;
      anchor: XYPosition;
    };

export type WorkflowNodeInsertionRequest =
  | WorkflowSelectorRequest
  | WorkflowEdgeSelectorRequest;

export type WorkflowBranchOutputKind =
  | "success"
  | "failed"
  | "switch-case"
  | "default"
  | "unassigned";

export type WorkflowBranchOutput = {
  id: string;
  label: string;
  kind: WorkflowBranchOutputKind;
  caseIndex?: number;
};

export type WorkflowNodeData = {
  workflowCode?: number;
  taskCode: number | null;
  name: string;
  taskType: string;
  draft: boolean;
  task?: WorkflowTaskRecord;
  focused?: boolean;
  readOnly?: boolean;
  menuOpen?: boolean;
  onRun?: () => void;
  onOpenMenu?: (position: { x: number; y: number }) => void;
  branchOutputs?: WorkflowBranchOutput[];
  branchOutputsPreview?: WorkflowBranchOutput[];
};

export type WorkflowEdgeData = {
  backend: boolean;
  hovered?: boolean;
  relation?: WorkflowTaskRelationRecord;
};

export type WorkflowCanvasNode = Node<WorkflowNodeData>;
export type WorkflowCanvasEdge = Edge<WorkflowEdgeData>;

export type WorkflowGraph = {
  nodes: WorkflowCanvasNode[];
  edges: WorkflowCanvasEdge[];
  preservePositions?: boolean;
};

export type WorkflowNodePositionMap = Record<string, XYPosition>;

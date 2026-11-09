import type { ElkNode } from "elkjs/lib/elk-api";
import ELK from "elkjs/lib/elk.bundled.js";

import {
  WORKFLOW_NODE_HEIGHT,
  WORKFLOW_NODE_WIDTH,
} from "../constants";
import type { WorkflowGraph } from "../types";

const elk = new ELK();

export async function layoutWorkflowGraph(
  graph: WorkflowGraph,
): Promise<WorkflowGraph> {
  if (graph.nodes.length === 0) return graph;

  const layoutInput: ElkNode = {
    id: "workflow-root",
    layoutOptions: {
      "elk.algorithm": "layered",
      "elk.direction": "RIGHT",
      "elk.edgeRouting": "ORTHOGONAL",
      "elk.spacing.nodeNode": "72",
      "elk.spacing.edgeNode": "32",
      "elk.layered.spacing.nodeNodeBetweenLayers": "120",
      "elk.layered.nodePlacement.strategy": "BRANDES_KOEPF",
      "elk.layered.crossingMinimization.strategy": "LAYER_SWEEP",
      "elk.layered.layering.strategy": "NETWORK_SIMPLEX",
    },
    children: graph.nodes.map((node) => ({
      id: node.id,
      width: node.measured?.width ?? WORKFLOW_NODE_WIDTH,
      height: node.measured?.height ?? WORKFLOW_NODE_HEIGHT,
    })),
    edges: graph.edges.map((edge) => ({
      id: edge.id,
      sources: [edge.source],
      targets: [edge.target],
    })),
  };

  const result = await elk.layout(layoutInput);
  const positions = new Map(
    (result.children ?? []).map((node) => [
      node.id,
      {
        x: node.x ?? 0,
        y: node.y ?? 0,
      },
    ]),
  );

  return {
    nodes: graph.nodes.map((node) => ({
      ...node,
      position: positions.get(node.id) ?? node.position,
    })),
    edges: graph.edges,
  };
}

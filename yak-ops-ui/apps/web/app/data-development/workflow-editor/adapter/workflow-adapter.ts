import { Position } from "reactflow";

import type { WorkflowDagRecord } from "@/service/workflow/definition";

import {
  WORKFLOW_NODE_HEIGHT,
  WORKFLOW_NODE_WIDTH,
} from "../constants";
import type {
  WorkflowCanvasEdge,
  WorkflowCanvasNode,
  WorkflowGraph,
} from "../types";

type SavedNodePosition = {
  taskCode?: number;
  code?: number;
  x?: number;
  y?: number;
};

function parseSavedPositions(
  value?: string | null,
): Map<number, { x: number; y: number }> {
  const positions = new Map<number, { x: number; y: number }>();
  if (!value) return positions;

  try {
    const parsed: unknown = JSON.parse(value);
    const entries: Array<[number, SavedNodePosition]> = Array.isArray(parsed)
      ? parsed.map((item) => {
          const position = item as SavedNodePosition;
          return [Number(position.taskCode ?? position.code), position];
        })
      : parsed && typeof parsed === "object"
        ? Object.entries(parsed as Record<string, SavedNodePosition>).map(
            ([code, position]) => [Number(code), position],
          )
        : [];

    for (const [code, position] of entries) {
      if (
        Number.isSafeInteger(code) &&
        Number.isFinite(position.x) &&
        Number.isFinite(position.y)
      ) {
        positions.set(code, { x: Number(position.x), y: Number(position.y) });
      }
    }
  } catch {
    return positions;
  }

  return positions;
}

export function workflowDagToGraph(
  dag: WorkflowDagRecord,
): WorkflowGraph {
  const savedPositions = parseSavedPositions(dag.workflowDefinition.locations);
  const nodes: WorkflowCanvasNode[] = dag.taskDefinitionList.map(
    (task, index) => ({
      id: String(task.code),
      type: "workflowTask",
      position: savedPositions.get(task.code) ?? {
        x: (index % 4) * (WORKFLOW_NODE_WIDTH + 120),
        y: Math.floor(index / 4) * (WORKFLOW_NODE_HEIGHT + 100),
      },
      sourcePosition: Position.Right,
      targetPosition: Position.Left,
      data: {
        workflowCode: dag.workflowDefinition.code,
        taskCode: task.code,
        name: task.name,
        taskType: task.taskType,
        draft: false,
        task,
      },
    }),
  );

  const nodeIds = new Set(nodes.map((node) => node.id));
  const edges: WorkflowCanvasEdge[] = dag.workflowTaskRelationList
    .filter(
      (relation) =>
        relation.preTaskCode !== 0 &&
        relation.postTaskCode !== 0 &&
        nodeIds.has(String(relation.preTaskCode)) &&
        nodeIds.has(String(relation.postTaskCode)),
    )
    .map((relation) => ({
      id: "relation:" + relation.preTaskCode + ":" + relation.postTaskCode,
      type: "workflowEdge",
      source: String(relation.preTaskCode),
      sourceHandle: "source",
      target: String(relation.postTaskCode),
      targetHandle: "target",
      data: { backend: true, relation },
    }));

  return {
    nodes,
    edges,
    preservePositions:
      nodes.length > 0 &&
      nodes.every((node) => savedPositions.has(Number(node.id))),
  };
}

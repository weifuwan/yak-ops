import { Position } from "@xyflow/react";

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

export function workflowDagToGraph(
  dag: WorkflowDagRecord,
): WorkflowGraph {
  const nodes: WorkflowCanvasNode[] = dag.taskDefinitionList.map(
    (task, index) => ({
      id: String(task.code),
      type: "workflowTask",
      position: {
        x: (index % 4) * (WORKFLOW_NODE_WIDTH + 120),
        y:
          Math.floor(index / 4) *
          (WORKFLOW_NODE_HEIGHT + 100),
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

  const edges: WorkflowCanvasEdge[] =
    dag.workflowTaskRelationList
      .filter(
        (relation) =>
          relation.preTaskCode !== 0 &&
          relation.postTaskCode !== 0 &&
          nodeIds.has(String(relation.preTaskCode)) &&
          nodeIds.has(String(relation.postTaskCode)),
      )
      .map((relation) => ({
        id:
          "relation:" +
          relation.preTaskCode +
          ":" +
          relation.postTaskCode,
        type: "workflowEdge",
        source: String(relation.preTaskCode),
        target: String(relation.postTaskCode),
        data: {
          backend: true,
        },
      }));

  return { nodes, edges };
}

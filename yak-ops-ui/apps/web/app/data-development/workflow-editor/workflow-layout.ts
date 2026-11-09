import {
  MarkerType,
  type Edge,
} from "@xyflow/react";

import type {
  WorkflowTaskRecord,
  WorkflowTaskRelationRecord,
} from "@/service/workflow/definition";

import type { WorkflowCanvasNode } from "./workflow-node";

const NODE_WIDTH = 220;
const HORIZONTAL_GAP = 80;
const VERTICAL_GAP = 110;
const CANVAS_PADDING = 80;

const taskId = (code: number) => String(code);

export function buildWorkflowCanvas(
  tasks: WorkflowTaskRecord[],
  relations: WorkflowTaskRelationRecord[],
  selectedTaskCode?: number,
): { nodes: WorkflowCanvasNode[]; edges: Edge[] } {
  const taskCodes = new Set(tasks.map((task) => task.code));
  const validRelations = relations.filter(
    (relation) =>
      relation.preTaskCode !== 0 &&
      relation.postTaskCode !== 0 &&
      taskCodes.has(relation.preTaskCode) &&
      taskCodes.has(relation.postTaskCode),
  );

  const incoming = new Map<number, number>();
  const outgoing = new Map<number, number[]>();
  const levelByCode = new Map<number, number>();

  for (const task of tasks) {
    incoming.set(task.code, 0);
    outgoing.set(task.code, []);
    levelByCode.set(task.code, 0);
  }

  for (const relation of validRelations) {
    incoming.set(
      relation.postTaskCode,
      (incoming.get(relation.postTaskCode) ?? 0) + 1,
    );
    outgoing.get(relation.preTaskCode)?.push(relation.postTaskCode);
  }

  const queue = tasks
    .filter((task) => (incoming.get(task.code) ?? 0) === 0)
    .map((task) => task.code);
  const processed = new Set<number>();

  while (queue.length > 0) {
    const code = queue.shift();
    if (code === undefined || processed.has(code)) continue;
    processed.add(code);

    for (const nextCode of outgoing.get(code) ?? []) {
      levelByCode.set(
        nextCode,
        Math.max(
          levelByCode.get(nextCode) ?? 0,
          (levelByCode.get(code) ?? 0) + 1,
        ),
      );
      const nextIncoming = (incoming.get(nextCode) ?? 1) - 1;
      incoming.set(nextCode, nextIncoming);
      if (nextIncoming === 0) queue.push(nextCode);
    }
  }

  let fallbackLevel =
    Math.max(0, ...Array.from(levelByCode.values())) + 1;
  for (const task of tasks) {
    if (!processed.has(task.code)) {
      levelByCode.set(task.code, fallbackLevel);
      fallbackLevel += 1;
    }
  }

  const levels = new Map<number, WorkflowTaskRecord[]>();
  for (const task of tasks) {
    const level = levelByCode.get(task.code) ?? 0;
    const levelTasks = levels.get(level) ?? [];
    levelTasks.push(task);
    levels.set(level, levelTasks);
  }

  const nodes = tasks.map((task) => {
    const level = levelByCode.get(task.code) ?? 0;
    const levelTasks = levels.get(level) ?? [task];
    const index = levelTasks.findIndex((item) => item.code === task.code);
    const rowWidth =
      levelTasks.length * NODE_WIDTH +
      Math.max(0, levelTasks.length - 1) * HORIZONTAL_GAP;

    return {
      id: taskId(task.code),
      type: "workflowTask",
      position: {
        x:
          CANVAS_PADDING +
          index * (NODE_WIDTH + HORIZONTAL_GAP) -
          rowWidth / 2 +
          NODE_WIDTH / 2,
        y: CANVAS_PADDING + level * VERTICAL_GAP,
      },
      data: {
        name: task.name,
        taskType: task.taskType,
      },
      selected: task.code === selectedTaskCode,
      draggable: false,
      selectable: true,
    } satisfies WorkflowCanvasNode;
  });

  const edges: Edge[] = validRelations.map((relation) => ({
    id: `${relation.preTaskCode}-${relation.postTaskCode}`,
    source: taskId(relation.preTaskCode),
    target: taskId(relation.postTaskCode),
    type: "smoothstep",
    markerEnd: {
      type: MarkerType.ArrowClosed,
      width: 14,
      height: 14,
    },
    style: {
      stroke: "var(--yak-components-border)",
      strokeWidth: 1.25,
    },
  }));

  return { nodes, edges };
}

import { useCallback } from "react";
import type { Connection } from "@xyflow/react";
import { toast } from "@yak-ops/yak-ui";

import {
  useWorkflowStore,
  useWorkflowStoreApi,
} from "../store/workflow-store";
import type { WorkflowCanvasEdge } from "../types";

function createsCycle(
  source: string,
  target: string,
  edges: WorkflowCanvasEdge[],
) {
  const outgoing = new Map<string, string[]>();

  for (const edge of edges) {
    const targets = outgoing.get(edge.source) ?? [];
    targets.push(edge.target);
    outgoing.set(edge.source, targets);
  }

  const queue = [target];
  const visited = new Set<string>();

  while (queue.length > 0) {
    const current = queue.shift();
    if (!current || visited.has(current)) continue;
    if (current === source) return true;

    visited.add(current);
    queue.push(...(outgoing.get(current) ?? []));
  }

  return false;
}

export function useWorkflowInteractions() {
  const store = useWorkflowStoreApi();
  const addConnection = useWorkflowStore(
    (state) => state.addConnection,
  );
  const removeNodes = useWorkflowStore(
    (state) => state.removeNodes,
  );
  const removeEdges = useWorkflowStore(
    (state) => state.removeEdges,
  );

  const isValidConnection = useCallback(
    (connection: Connection | WorkflowCanvasEdge) => {
      const source = connection.source;
      const target = connection.target;

      if (!source || !target || source === target) {
        return false;
      }

      const edges = store.getState().edges;
      const duplicate = edges.some(
        (edge) =>
          edge.source === source &&
          edge.target === target,
      );

      if (duplicate) return false;

      return !createsCycle(source, target, edges);
    },
    [store],
  );

  const handleConnect = useCallback(
    (connection: Connection) => {
      if (!isValidConnection(connection)) {
        toast.warning("无法创建连线", {
          description:
            "不允许自连接、重复连接或形成环路。",
        });
        return;
      }

      addConnection(connection);
    },
    [addConnection, isValidConnection],
  );

  const handleNodesDelete = useCallback(
    (nodes: Array<{ id: string }>) => {
      removeNodes(nodes.map((node) => node.id));
    },
    [removeNodes],
  );

  const handleEdgesDelete = useCallback(
    (edges: Array<{ id: string }>) => {
      removeEdges(edges.map((edge) => edge.id));
    },
    [removeEdges],
  );

  return {
    handleConnect,
    handleNodesDelete,
    handleEdgesDelete,
    isValidConnection,
  };
}

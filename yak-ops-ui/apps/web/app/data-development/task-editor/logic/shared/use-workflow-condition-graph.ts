import { useEffect, useState } from "react";

import { useDataDevelopmentWorkbench } from "../../../workbench-context";
import { getConditionWorkflowGraph } from "../conditions/adapter";
import type { ConditionWorkflowGraph } from "../conditions/types";

const EMPTY_GRAPH: ConditionWorkflowGraph = {
  upstreamTasks: [],
  successTargets: [],
  failedTargets: [],
  unassignedTargetCount: 0,
};

export function useWorkflowConditionGraph(
  workflowCode: number,
  nodeId: string,
  enabled = true,
): ConditionWorkflowGraph {
  const { getWorkflowStore } = useDataDevelopmentWorkbench();
  const [graph, setGraph] = useState<ConditionWorkflowGraph>(EMPTY_GRAPH);

  useEffect(() => {
    if (!enabled) {
      setGraph(EMPTY_GRAPH);
      return;
    }

    const store = getWorkflowStore(workflowCode);
    if (!store) {
      setGraph(EMPTY_GRAPH);
      return;
    }

    const sync = () => {
      const state = store.getState();
      setGraph(getConditionWorkflowGraph(state.nodes, state.edges, nodeId));
    };

    sync();
    return store.subscribe(sync);
  }, [enabled, getWorkflowStore, nodeId, workflowCode]);

  return graph;
}

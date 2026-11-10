import {
  createContext,
  useContext,
  useRef,
  type PropsWithChildren,
} from "react";
import {
  
  Position,
  applyEdgeChanges,
  applyNodeChanges,
  type Connection,
  type EdgeChange,
  type NodeChange,
  type XYPosition,
} from "@xyflow/react";
import { temporal, type TemporalState } from "zundo";
import { useStore } from "zustand";
import { createStore, type StoreApi } from "zustand/vanilla";

import type {
  WorkflowCanvasEdge,
  WorkflowCanvasNode,
  WorkflowGraph,
  WorkflowInteractionMode,
  WorkflowSelectorRequest,
} from "../types";

type WorkflowHistoryState = Pick<
  WorkflowEditorState,
  "nodes" | "edges"
>;

export type WorkflowEditorState = {
  nodes: WorkflowCanvasNode[];
  edges: WorkflowCanvasEdge[];
  interactionMode: WorkflowInteractionMode;
  selectorRequest: WorkflowSelectorRequest | null;
  draftNodeSequence: number;
  draftEdgeSequence: number;

  onNodesChange: (
    changes: NodeChange<WorkflowCanvasNode>[],
  ) => void;
  onEdgesChange: (
    changes: EdgeChange<WorkflowCanvasEdge>[],
  ) => void;
  setInteractionMode: (
    mode: WorkflowInteractionMode,
  ) => void;
  openSelector: (
    request: WorkflowSelectorRequest,
  ) => void;
  closeSelector: () => void;
  addDroppedNode: (
    taskType: string,
    position: XYPosition,
  ) => string;
  insertNodeFromSelector: (
    taskType: string,
  ) => string;
  addConnection: (connection: Connection) => string | null;
  removeNodes: (ids: string[]) => void;
  removeEdges: (ids: string[]) => void;
  replaceGraph: (graph: WorkflowGraph) => void;
  selectTaskCode: (taskCode: number) => string | null;
  clearSelection: () => void;
};

export type WorkflowStoreApi = StoreApi<WorkflowEditorState> & {
  temporal: StoreApi<TemporalState<WorkflowHistoryState>>;
};

function edgeId(
  sequence: number,
  source: string,
  target: string,
) {
  return (
    "draft-edge:" +
    sequence +
    ":" +
    source +
    ":" +
    target
  );
}

function createDraftEdge(
  sequence: number,
  source: string,
  target: string,
): WorkflowCanvasEdge {
  return {
    id: edgeId(sequence, source, target),
    type: "workflowEdge",
    source,
    target,
    data: {
      backend: false,
    },
  };
}

function createDraftNode(
  sequence: number,
  taskType: string,
  position: XYPosition,
  workflowCode: number,
): WorkflowCanvasNode {
  return {
    id: "draft:" + sequence,
    type: "workflowTask",
    position,
    sourcePosition: Position.Right,
    targetPosition: Position.Left,
    data: {
      workflowCode,
      taskCode: null,
      taskType,
      name: taskType + " 节点",
      draft: true,
    },
  };
}

function getDefaultPosition(
  nodes: WorkflowCanvasNode[],
): XYPosition {
  if (nodes.length === 0) return { x: 120, y: 120 };

  const rightMost = nodes.reduce(
    (current, node) =>
      node.position.x > current.position.x ? node : current,
    nodes[0],
  );

  return {
    x: rightMost.position.x + 340,
    y: rightMost.position.y,
  };
}

function graphHistorySnapshot(
  state: WorkflowEditorState,
): WorkflowHistoryState {
  return {
    nodes: state.nodes.map((node) => ({
      ...node,
      selected: false,
      dragging: false,
    })),
    edges: state.edges.map((edge) => ({
      ...edge,
      selected: false,
      data: {
        backend: edge.data?.backend ?? false,
        hovered: false,
      },
    })),
  };
}

function historyEquals(
  a: WorkflowHistoryState,
  b: WorkflowHistoryState,
) {
  return JSON.stringify(a) === JSON.stringify(b);
}

export function createWorkflowStore(
  initialGraph: WorkflowGraph,
  workflowCode: number,
): WorkflowStoreApi {
  return createStore<WorkflowEditorState>()(
    temporal<
      WorkflowEditorState,
      [],
      [],
      WorkflowHistoryState
    >(
      (set, get) => ({
        nodes: initialGraph.nodes,
        edges: initialGraph.edges,
        interactionMode: "pointer",
        selectorRequest: null,
        draftNodeSequence: 0,
        draftEdgeSequence: 0,

        onNodesChange: (changes) => {
          const applicable = changes.filter(
            (change) => change.type !== "remove",
          );
          if (applicable.length === 0) return;

          set((state) => ({
            nodes: applyNodeChanges(
              applicable,
              state.nodes,
            ),
          }));
        },

        onEdgesChange: (changes) => {
          const applicable = changes.filter(
            (change) => change.type !== "remove",
          );
          if (applicable.length === 0) return;

          set((state) => ({
            edges: applyEdgeChanges(
              applicable,
              state.edges,
            ),
          }));
        },

        setInteractionMode: (interactionMode) =>
          set({ interactionMode }),

        openSelector: (selectorRequest) =>
          set({ selectorRequest }),

        closeSelector: () =>
          set({ selectorRequest: null }),

        addDroppedNode: (taskType, position) => {
          const sequence = get().draftNodeSequence + 1;
          const node = createDraftNode(
            sequence,
            taskType,
            position,
            workflowCode,
          );

          set((state) => ({
            nodes: [
              ...state.nodes.map((item) => ({
                ...item,
                selected: false,
              })),
              { ...node, selected: true },
            ],
            edges: state.edges.map((edge) => ({
              ...edge,
              selected: false,
            })),
            draftNodeSequence: sequence,
          }));

          return node.id;
        },

        insertNodeFromSelector: (taskType) => {
          const state = get();
          const request =
            state.selectorRequest ?? ({ kind: "add" } as const);
          const sequence = state.draftNodeSequence + 1;
          let position = getDefaultPosition(state.nodes);
          let edges = state.edges;
          let edgeSequence = state.draftEdgeSequence;

          if (
            request.kind === "upstream" ||
            request.kind === "downstream"
          ) {
            const anchor = state.nodes.find(
              (node) => node.id === request.nodeId,
            );
            if (anchor) {
              position =
                request.kind === "upstream"
                  ? {
                      x: anchor.position.x - 340,
                      y: anchor.position.y,
                    }
                  : {
                      x: anchor.position.x + 340,
                      y: anchor.position.y,
                    };
            }
          }

          if (request.kind === "edge") {
            const edge = state.edges.find(
              (item) => item.id === request.edgeId,
            );
            const source = edge
              ? state.nodes.find(
                  (node) => node.id === edge.source,
                )
              : undefined;
            const target = edge
              ? state.nodes.find(
                  (node) => node.id === edge.target,
                )
              : undefined;

            if (source && target) {
              position = {
                x:
                  (source.position.x +
                    target.position.x) /
                  2,
                y:
                  (source.position.y +
                    target.position.y) /
                  2,
              };
            }
          }

          const node = createDraftNode(
            sequence,
            taskType,
            position,
            workflowCode,
          );

          if (
            request.kind === "upstream" ||
            request.kind === "downstream"
          ) {
            edgeSequence += 1;
            edges = [
              ...edges,
              request.kind === "upstream"
                ? createDraftEdge(
                    edgeSequence,
                    node.id,
                    request.nodeId,
                  )
                : createDraftEdge(
                    edgeSequence,
                    request.nodeId,
                    node.id,
                  ),
            ];
          }

          if (request.kind === "edge") {
            const currentEdge = edges.find(
              (item) => item.id === request.edgeId,
            );
            if (currentEdge) {
              edges = edges.filter(
                (item) => item.id !== currentEdge.id,
              );
              edgeSequence += 1;
              const before = createDraftEdge(
                edgeSequence,
                currentEdge.source,
                node.id,
              );
              edgeSequence += 1;
              const after = createDraftEdge(
                edgeSequence,
                node.id,
                currentEdge.target,
              );
              edges = [...edges, before, after];
            }
          }

          set({
            nodes: [
              ...state.nodes.map((item) => ({
                ...item,
                selected: false,
              })),
              { ...node, selected: true },
            ],
            edges: edges.map((edge) => ({
              ...edge,
              selected: false,
            })),
            draftNodeSequence: sequence,
            draftEdgeSequence: edgeSequence,
            selectorRequest:
              request.kind === "add" ? request : null,
          });

          return node.id;
        },

        addConnection: (connection) => {
          if (!connection.source || !connection.target) {
            return null;
          }

          const sequence = get().draftEdgeSequence + 1;
          const edge = createDraftEdge(
            sequence,
            connection.source,
            connection.target,
          );

          set((state) => ({
            edges: [
              ...state.edges.map((item) => ({
                ...item,
                selected: false,
              })),
              { ...edge, selected: true },
            ],
            nodes: state.nodes.map((node) => ({
              ...node,
              selected: false,
            })),
            draftEdgeSequence: sequence,
          }));

          return edge.id;
        },

        removeNodes: (ids) => {
          if (ids.length === 0) return;
          const deleting = new Set(ids);

          set((state) => ({
            nodes: state.nodes.filter(
              (node) => !deleting.has(node.id),
            ),
            edges: state.edges.filter(
              (edge) =>
                !deleting.has(edge.source) &&
                !deleting.has(edge.target),
            ),
          }));
        },

        removeEdges: (ids) => {
          if (ids.length === 0) return;
          const deleting = new Set(ids);
          set((state) => ({
            edges: state.edges.filter(
              (edge) => !deleting.has(edge.id),
            ),
          }));
        },

        replaceGraph: (graph) =>
          set({
            nodes: graph.nodes,
            edges: graph.edges,
          }),

        selectTaskCode: (taskCode) => {
          const node = get().nodes.find(
            (item) => item.data.taskCode === taskCode,
          );
          if (!node) return null;

          set((state) => ({
            nodes: state.nodes.map((item) => ({
              ...item,
              selected: item.id === node.id,
            })),
            edges: state.edges.map((edge) => ({
              ...edge,
              selected: false,
            })),
          }));

          return node.id;
        },

        clearSelection: () =>
          set((state) => ({
            nodes: state.nodes.map((node) => ({
              ...node,
              selected: false,
            })),
            edges: state.edges.map((edge) => ({
              ...edge,
              selected: false,
            })),
          })),
      }),
      {
        limit: 100,
        partialize: graphHistorySnapshot,
        equality: historyEquals,
      },
    ),
  ) as WorkflowStoreApi;
}

const WorkflowStoreContext =
  createContext<WorkflowStoreApi | null>(null);

export function WorkflowStoreProvider({
  initialGraph,
  workflowCode,
  children,
}: PropsWithChildren<{
  initialGraph: WorkflowGraph;
  workflowCode: number;
}>) {
  const storeRef = useRef<WorkflowStoreApi | null>(null);

  if (!storeRef.current) {
    storeRef.current = createWorkflowStore(initialGraph, workflowCode);
  }

  return (
    <WorkflowStoreContext.Provider value={storeRef.current}>
      {children}
    </WorkflowStoreContext.Provider>
  );
}

export function useWorkflowStoreApi() {
  const store = useContext(WorkflowStoreContext);
  if (!store) {
    throw new Error(
      "useWorkflowStoreApi must be used within WorkflowStoreProvider",
    );
  }
  return store;
}

export function useWorkflowStore<T>(
  selector: (state: WorkflowEditorState) => T,
) {
  return useStore(useWorkflowStoreApi(), selector);
}

export function useWorkflowHistory<T>(
  selector: (
    state: TemporalState<WorkflowHistoryState>,
  ) => T,
) {
  const store = useWorkflowStoreApi();
  return useStore(store.temporal, selector);
}

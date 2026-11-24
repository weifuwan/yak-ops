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
} from "reactflow";
import { temporal, type TemporalState } from "zundo";
import { useStore } from "zustand";
import { createStore, type StoreApi } from "zustand/vanilla";

import { getWorkflowBranchOutputs } from "../adapter/workflow-branch-adapter";
import type {
  WorkflowBranchOutput,
  WorkflowCanvasEdge,
  WorkflowCanvasNode,
  WorkflowGraph,
  WorkflowInteractionMode,
  WorkflowEdgeStyle,
  WorkflowSelectorRequest,
  WorkflowPopoverSelectorRequest,
  WorkflowNodeInsertionRequest,
  WorkflowNodeCreationRequest,
} from "../types";
import type { WorkflowTaskRecord } from "@/service/workflow/definition";
import { buildScriptTaskCommentTemplate } from "../adapter/script-comment-template";

type WorkflowHistoryState = Pick<
  WorkflowEditorState,
  "nodes" | "edges"
>;

export type WorkflowEditorState = {
  nodes: WorkflowCanvasNode[];
  edges: WorkflowCanvasEdge[];
  previewEdges: WorkflowCanvasEdge[] | null;
  previewNodeId: string | null;
  interactionMode: WorkflowInteractionMode;
  edgeStyle: WorkflowEdgeStyle;
  selectorRequest: WorkflowSelectorRequest | null;
  edgeSelectorRequest: WorkflowPopoverSelectorRequest | null;
  nodeCreationRequest: WorkflowNodeCreationRequest | null;
  draftNodeSequence: number;
  draftEdgeSequence: number;

  onNodesChange: (
    changes: NodeChange[],
  ) => void;
  onEdgesChange: (
    changes: EdgeChange[],
  ) => void;
  setInteractionMode: (
    mode: WorkflowInteractionMode,
  ) => void;
  setEdgeStyle: (style: WorkflowEdgeStyle) => void;
  openSelector: (
    request: WorkflowSelectorRequest,
  ) => void;
  closeSelector: () => void;
  openEdgeSelector: (request: WorkflowPopoverSelectorRequest) => void;
  closeEdgeSelector: () => void;
  requestNodeCreation: (
    taskType: string,
    insertionRequest?: WorkflowNodeInsertionRequest,
    position?: XYPosition,
    source?: "sidebar" | "popover" | "drop",
  ) => void;
  cancelNodeCreation: () => void;
  createNodeFromRequest: (
    taskCode: number,
    projectCode: number,
    name: string,
  ) => string | null;
  updateTaskDefinition: (nodeId: string, task: WorkflowTaskRecord) => boolean;
  setSwitchPreview: (
    nodeId: string,
    branchOutputs: WorkflowBranchOutput[],
    edges: WorkflowCanvasEdge[],
  ) => void;
  clearSwitchPreview: (nodeId: string) => void;
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
  sourceHandle: string,
) {
  return (
    "draft-edge:" +
    sequence +
    ":" +
    source +
    ":" +
    sourceHandle +
    ":" +
    target
  );
}

function createDraftEdge(
  sequence: number,
  source: string,
  target: string,
  sourceHandle = "source",
): WorkflowCanvasEdge {
  return {
    id: edgeId(sequence, source, target, sourceHandle),
    type: "workflowEdge",
    source,
    sourceHandle,
    target,
    targetHandle: "target",
    data: {
      backend: false,
    },
  };
}

function getNextDraftNodeName(
  taskType: string,
  nodes: WorkflowCanvasNode[],
): string {
  const existingNames = new Set(
    nodes.map((node) => node.data.name.trim()),
  );
  const baseName = taskType + " 节点";
  let sequence = 1;

  while (existingNames.has(baseName + " " + sequence)) {
    sequence += 1;
  }

  return baseName + " " + sequence;
}

function defaultTaskParams(
  taskType: string,
  createdBy: string,
): Record<string, unknown> {
  switch (taskType.toUpperCase()) {
    case "HTTP":
      return {
        url: "",
        httpMethod: "GET",
        httpParams: [],
        httpBody: "",
        httpCheckCondition: "STATUS_CODE_DEFAULT",
        condition: "",
        connectTimeout: 10000,
        resourceList: [],
        localParams: [],
      };
    case "JAVA":
      return {
        runType: "FAT_JAR",
        mainJar: null,
        mainClass: "",
        mainArgs: "",
        jvmArgs: "",
        isModulePath: false,
        resourceList: [],
        localParams: [],
      };
    case "CONDITIONS":
      return {
        localParams: [],
        dependence: {
          relation: "AND",
          dependTaskList: [],
        },
        conditionResult: {
          successNode: [],
          failedNode: [],
        },
      };
    case "SWITCH":
      return {
        localParams: [],
        switchResult: {
          dependTaskList: [{ condition: "", nextNode: null }],
          nextNode: null,
        },
        nextBranch: null,
      };
    case "DEPENDENT":
      return {
        localParams: [],
        dependence: {
          relation: "AND",
          dependTaskList: [],
          checkInterval: 60,
          failurePolicy: "DEPENDENT_FAILURE_FAILURE",
          failureWaitingTime: 30,
        },
      };
    case "SUB_WORKFLOW":
      return {
        localParams: [],
        workflowDefinitionCode: 0,
      };
    case "SHELL":
      return {
        rawScript: buildScriptTaskCommentTemplate(createdBy),
        resourceList: [],
        localParams: [],
        dependence: {},
        conditionResult: { successNode: [], failedNode: [] },
        waitStartTimeout: {},
        switchResult: {},
      };
    case "PYTHON":
      return {
        rawScript: buildScriptTaskCommentTemplate(createdBy),
        resourceList: [],
        localParams: [],
      };
    case "SQL":
      return {
        type: "",
        datasource: "",
        sql: "",
        sqlSource: "SCRIPT",
        sqlResource: "",
        sqlType: 0,
        sendEmail: false,
        displayRows: 10,
        showType: "TABLE",
        connParams: "",
        preStatements: [],
        postStatements: [],
        groupId: 0,
        title: "",
        limit: 0,
        localParams: [],
      };
    default:
      return { resourceList: [], localParams: [] };
  }
}

function createDraftNode(
  taskType: string,
  position: XYPosition,
  workflowCode: number,
  projectCode: number,
  taskCode: number,
  name: string,
  createdBy: string,
): WorkflowCanvasNode {
  const task: WorkflowTaskRecord = {
    id: 0,
    code: taskCode,
    name,
    version: 0,
    description: "",
    projectCode,
    userId: 0,
    taskType,
    taskParams: defaultTaskParams(taskType, createdBy),
    flag: "YES",
    taskPriority: "MEDIUM",
    workerGroup: "default",
    environmentCode: -1,
    failRetryTimes: 0,
    failRetryInterval: 1,
    timeoutFlag: "CLOSE",
    timeoutNotifyStrategy: "WARNFAILED",
    timeout: 0,
    delayTime: 0,
    taskGroupId: 0,
    taskGroupPriority: 0,
    cpuQuota: -1,
    memoryMax: -1,
    taskExecuteType: "BATCH",
  };

  return {
    id: String(taskCode),
    type: "workflowTask",
    position,
    sourcePosition: Position.Right,
    targetPosition: Position.Left,
    data: {
      workflowCode,
      taskCode,
      taskType,
      name,
      draft: true,
      task,
      branchOutputs: getWorkflowBranchOutputs(taskType, task.taskParams),
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
      data: {
        ...node.data,
        branchOutputsPreview: undefined,
      },
      selected: false,
      dragging: false,
    })),
    edges: state.edges.map((edge) => ({
      ...edge,
      selected: false,
      data: {
        backend: edge.data?.backend ?? false,
        hovered: false,
        relation: edge.data?.relation,
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
  createdBy = "当前用户",
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
        previewEdges: null,
        previewNodeId: null,
        interactionMode: "pointer",
        edgeStyle: "bezier",
        selectorRequest: null,
        edgeSelectorRequest: null,
        nodeCreationRequest: null,
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
            previewEdges: state.previewEdges
              ? applyEdgeChanges(applicable, state.previewEdges)
              : null,
          }));
        },

        setInteractionMode: (interactionMode) =>
          set({ interactionMode }),

        setEdgeStyle: (edgeStyle) =>
          set({ edgeStyle }),

        openSelector: (selectorRequest) =>
          set({ selectorRequest }),

        closeSelector: () =>
          set({ selectorRequest: null }),

        openEdgeSelector: (edgeSelectorRequest) =>
          set({ edgeSelectorRequest }),

        closeEdgeSelector: () =>
          set({ edgeSelectorRequest: null }),

        requestNodeCreation: (
          taskType,
          insertionRequest,
          position,
          source = "sidebar",
        ) => {
          const state = get();
          set((current) => ({
            nodeCreationRequest: {
              taskType,
              insertionRequest,
              position,
              defaultName: getNextDraftNodeName(taskType, state.nodes),
            },
            edgeSelectorRequest:
              source === "popover" ? null : current.edgeSelectorRequest,
          }));
        },

        cancelNodeCreation: () =>
          set({ nodeCreationRequest: null }),

        updateTaskDefinition: (nodeId, task) => {
          if (!get().nodes.some((node) => node.id === nodeId)) return false;
          set((state) => ({
            nodes: state.nodes.map((node) =>
              node.id === nodeId
                ? {
                    ...node,
                    data: {
                      ...node.data,
                      taskCode: task.code,
                      taskType: task.taskType,
                      name: task.name,
                      task,
                      branchOutputs: [
                        ...getWorkflowBranchOutputs(task.taskType, task.taskParams),
                        ...((node.data.branchOutputs ?? []).some((output) => output.id === "unassigned")
                          ? [{ id: "unassigned", label: "未配置", kind: "unassigned" as const }]
                          : []),
                      ],
                      branchOutputsPreview: undefined,
                    },
                  }
                : node,
            ),
            previewEdges: state.previewNodeId === nodeId ? null : state.previewEdges,
            previewNodeId: state.previewNodeId === nodeId ? null : state.previewNodeId,
          }));
          return true;
        },

        setSwitchPreview: (nodeId, branchOutputs, previewEdges) => {
          if (!get().nodes.some((node) => node.id === nodeId)) return;
          set((state) => ({
            nodes: state.nodes.map((node) =>
              node.id === nodeId
                ? {
                    ...node,
                    data: {
                      ...node.data,
                      branchOutputsPreview: branchOutputs,
                    },
                  }
                : node,
            ),
            previewEdges,
            previewNodeId: nodeId,
          }));
        },

        clearSwitchPreview: (nodeId) =>
          set((state) => ({
            nodes: state.nodes.map((node) =>
              node.id === nodeId
                ? {
                    ...node,
                    data: {
                      ...node.data,
                      branchOutputsPreview: undefined,
                    },
                  }
                : node,
            ),
            previewEdges: state.previewNodeId === nodeId ? null : state.previewEdges,
            previewNodeId: state.previewNodeId === nodeId ? null : state.previewNodeId,
          })),

        createNodeFromRequest: (taskCode, projectCode, name) => {
          const state = get();
          const creationRequest = state.nodeCreationRequest;
          const normalizedName = name.trim();
          if (!creationRequest || !normalizedName) return null;

          const request =
            creationRequest.insertionRequest ?? ({ kind: "add" } as const);
          const sequence = state.draftNodeSequence + 1;
          let position = creationRequest.position ?? getDefaultPosition(state.nodes);
          let edges = state.edges;
          let edgeSequence = state.draftEdgeSequence;
          const previewSourceHandle = state.previewEdges?.find(
            (edge) => edge.id === (request.kind === "edge" ? request.edgeId : ""),
          )?.sourceHandle;

          if (
            !creationRequest.position &&
            (request.kind === "upstream" ||
              request.kind === "downstream")
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

          if (!creationRequest.position && request.kind === "edge") {
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
            creationRequest.taskType,
            position,
            workflowCode,
            projectCode,
            taskCode,
            normalizedName,
            createdBy,
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
                    request.sourceHandle ?? "source",
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
                currentEdge.sourceHandle ?? "source",
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

          const originalEdgeIds = new Set(state.edges.map((edge) => edge.id));
          const nextEdgeIds = new Set(edges.map((edge) => edge.id));
          const removedEdgeIds = new Set(
            state.edges
              .filter((edge) => !nextEdgeIds.has(edge.id))
              .map((edge) => edge.id),
          );
          const addedEdges = edges.filter((edge) => !originalEdgeIds.has(edge.id));
          const nextPreviewEdges = state.previewEdges
            ? [
                ...state.previewEdges.filter((edge) => !removedEdgeIds.has(edge.id)),
                ...addedEdges.map((edge) => ({
                  ...edge,
                  sourceHandle:
                    request.kind === "edge" &&
                    previewSourceHandle &&
                    edge.source === state.edges.find((item) => item.id === request.edgeId)?.source &&
                    edge.target === node.id
                      ? previewSourceHandle
                      : edge.sourceHandle,
                })),
              ].map((edge) => ({ ...edge, selected: false }))
            : null;

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
            previewEdges: nextPreviewEdges,
            draftNodeSequence: sequence,
            draftEdgeSequence: edgeSequence,
            nodeCreationRequest: null,
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
            connection.sourceHandle ?? "source",
          );

          set((state) => ({
            edges: [
              ...state.edges.map((item) => ({
                ...item,
                selected: false,
              })),
              { ...edge, selected: true },
            ],
            previewEdges: state.previewEdges
              ? [
                  ...state.previewEdges.map((item) => ({
                    ...item,
                    selected: false,
                  })),
                  { ...edge, selected: true },
                ]
              : null,
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
            previewEdges: state.previewEdges
              ? state.previewEdges.filter(
                  (edge) =>
                    !deleting.has(edge.source) &&
                    !deleting.has(edge.target),
                )
              : null,
            previewNodeId: state.previewNodeId && deleting.has(state.previewNodeId)
              ? null
              : state.previewNodeId,
          }));
        },

        removeEdges: (ids) => {
          if (ids.length === 0) return;
          const deleting = new Set(ids);
          set((state) => ({
            edges: state.edges.filter(
              (edge) => !deleting.has(edge.id),
            ),
            previewEdges: state.previewEdges
              ? state.previewEdges.filter((edge) => !deleting.has(edge.id))
              : null,
          }));
        },

        replaceGraph: (graph) =>
          set({
            nodes: graph.nodes,
            edges: graph.edges,
            previewEdges: null,
            previewNodeId: null,
            nodeCreationRequest: null,
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
  createdBy = "当前用户",
  children,
}: PropsWithChildren<{
  initialGraph: WorkflowGraph;
  workflowCode: number;
  createdBy?: string;
}>) {
  const storeRef = useRef<WorkflowStoreApi | null>(null);

  if (!storeRef.current) {
    storeRef.current = createWorkflowStore(initialGraph, workflowCode, createdBy);
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
): T {
  const store = useWorkflowStoreApi();
  return useStore(store, selector);
}

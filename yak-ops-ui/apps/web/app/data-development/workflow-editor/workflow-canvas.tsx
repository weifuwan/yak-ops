import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type DragEvent,
} from "react";
import {
  Background,
  BackgroundVariant,
  ConnectionLineType,
  Controls,
  MarkerType,
  ReactFlow,
  addEdge,
  type Connection,
  type Edge,
  type ReactFlowInstance,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import { toast } from "@yak-ops/yak-ui";

import type {
  WorkflowTaskRecord,
  WorkflowTaskRelationRecord,
} from "@/service/workflow/definition";

import { WORKFLOW_TASK_DRAG_TYPE } from "./node-palette";
import { buildWorkflowCanvas } from "./workflow-layout";
import {
  WorkflowTaskNode,
  type WorkflowCanvasNode,
  type WorkflowNodeAction,
} from "./workflow-node";

const nodeTypes = {
  workflowTask: WorkflowTaskNode,
};

type WorkflowCanvasProps = {
  tasks: WorkflowTaskRecord[];
  relations: WorkflowTaskRelationRecord[];
  selectedTaskCode?: number;
};

const actionLabel: Record<WorkflowNodeAction, string> = {
  "create-upstream": "新建上游",
  open: "打开节点",
  delete: "删除节点",
  "create-downstream": "新建下游",
};

const edgePairKey = (source: string, target: string) => `${source}->${target}`;

const createsCycle = (
  source: string,
  target: string,
  edges: Edge[],
): boolean => {
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
};

export function WorkflowCanvas({
  tasks,
  relations,
  selectedTaskCode,
}: WorkflowCanvasProps) {
  const [instance, setInstance] =
    useState<ReactFlowInstance<WorkflowCanvasNode, Edge> | null>(null);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(
    selectedTaskCode ? String(selectedTaskCode) : null,
  );
  const [selectedEdgeId, setSelectedEdgeId] = useState<string | null>(null);
  const [draftNodes, setDraftNodes] = useState<WorkflowCanvasNode[]>([]);
  const [draftEdges, setDraftEdges] = useState<Edge[]>([]);
  const [removedBackendEdgeIds, setRemovedBackendEdgeIds] = useState<Set<string>>(
    new Set(),
  );
  const draftSequenceRef = useRef(0);

  const canvas = useMemo(
    () => buildWorkflowCanvas(tasks, relations),
    [tasks, relations],
  );

  useEffect(() => {
    setDraftNodes([]);
    setDraftEdges([]);
    setRemovedBackendEdgeIds(new Set());
    setSelectedEdgeId(null);
  }, [tasks, relations]);

  useEffect(() => {
    if (selectedTaskCode) {
      setSelectedNodeId(String(selectedTaskCode));
      setSelectedEdgeId(null);
      return;
    }

    setSelectedNodeId((current) =>
      current &&
      [...canvas.nodes, ...draftNodes].some((node) => node.id === current)
        ? current
        : null,
    );
  }, [canvas.nodes, draftNodes, selectedTaskCode]);

  const handleNodeAction = useCallback(
    (action: WorkflowNodeAction, taskCode: number) => {
      setSelectedNodeId(
        taskCode < 0 ? `draft:${Math.abs(taskCode)}` : String(taskCode),
      );
      setSelectedEdgeId(null);

      if (action === "open") {
        toast.info("节点已选中", {
          description: "节点详情编辑将在后续接入。",
        });
        return;
      }

      toast.info(actionLabel[action], {
        description: "节点编辑动作已接入，后续将连接工作流保存接口。",
      });
    },
    [],
  );

  const nodes = useMemo(
    () =>
      [...canvas.nodes, ...draftNodes].map((node) => ({
        ...node,
        selected: node.id === selectedNodeId,
        data: {
          ...node.data,
          onAction: handleNodeAction,
        },
      })),
    [canvas.nodes, draftNodes, handleNodeAction, selectedNodeId],
  );

  const visibleBackendEdges = useMemo(
    () =>
      canvas.edges.filter((edge) => !removedBackendEdgeIds.has(edge.id)),
    [canvas.edges, removedBackendEdgeIds],
  );

  const rawEdges = useMemo(
    () => [...visibleBackendEdges, ...draftEdges],
    [draftEdges, visibleBackendEdges],
  );

  const edges = useMemo(
    () =>
      rawEdges.map((edge) => {
        const selected = edge.id === selectedEdgeId;
        return {
          ...edge,
          selected,
          style: {
            ...edge.style,
            stroke: selected
              ? "var(--yak-color-primary)"
              : edge.style?.stroke ?? "var(--yak-components-border)",
            strokeWidth: selected ? 2 : edge.style?.strokeWidth ?? 1.25,
          },
          markerEnd: {
            type: MarkerType.ArrowClosed,
            width: 14,
            height: 14,
            color: selected
              ? "var(--yak-color-primary)"
              : "var(--yak-components-muted-text)",
          },
        };
      }),
    [rawEdges, selectedEdgeId],
  );

  const isValidConnection = useCallback(
    (connection: Connection | Edge) => {
      const source = connection.source;
      const target = connection.target;

      if (!source || !target || source === target) return false;

      const pair = edgePairKey(source, target);
      if (
        rawEdges.some(
          (edge) => edgePairKey(edge.source, edge.target) === pair,
        )
      ) {
        return false;
      }

      return !createsCycle(source, target, rawEdges);
    },
    [rawEdges],
  );

  const handleConnect = useCallback(
    (connection: Connection) => {
      if (!connection.source || !connection.target) return;

      if (!isValidConnection(connection)) {
        toast.warning("无法创建连线", {
          description: "不允许自连接、重复连接或形成环路。",
        });
        return;
      }

      const id = `draft-edge:${edgePairKey(
        connection.source,
        connection.target,
      )}`;

      setDraftEdges((current) =>
        addEdge(
          {
            ...connection,
            id,
            type: "smoothstep",
            markerEnd: {
              type: MarkerType.ArrowClosed,
              width: 14,
              height: 14,
            },
            style: {
              stroke: "var(--yak-components-muted-text)",
              strokeWidth: 1.25,
            },
          },
          current,
        ),
      );
      setSelectedNodeId(null);
      setSelectedEdgeId(id);
    },
    [isValidConnection],
  );

  const deleteEdges = useCallback(
    (deletedEdges: Edge[]) => {
      if (deletedEdges.length === 0) return;

      const backendIds = new Set(canvas.edges.map((edge) => edge.id));
      const deletingIds = new Set(deletedEdges.map((edge) => edge.id));

      setDraftEdges((current) =>
        current.filter((edge) => !deletingIds.has(edge.id)),
      );

      setRemovedBackendEdgeIds((current) => {
        const next = new Set(current);
        for (const edge of deletedEdges) {
          if (backendIds.has(edge.id)) next.add(edge.id);
        }
        return next;
      });

      setSelectedEdgeId((current) =>
        current && deletingIds.has(current) ? null : current,
      );
    },
    [canvas.edges],
  );

  useEffect(() => {
    if (!instance || nodes.length === 0) return;

    const selectedNode = selectedNodeId
      ? nodes.find((node) => node.id === selectedNodeId)
      : undefined;

    const frame = window.requestAnimationFrame(() => {
      if (selectedTaskCode && selectedNode) {
        void instance.fitView({
          nodes: [selectedNode],
          duration: 220,
          padding: 1.8,
          maxZoom: 1.15,
        });
        return;
      }

      if (!selectedNodeId && !selectedEdgeId) {
        void instance.fitView({
          duration: 220,
          padding: 0.28,
          maxZoom: 1,
        });
      }
    });

    return () => window.cancelAnimationFrame(frame);
  }, [
    instance,
    nodes,
    selectedEdgeId,
    selectedNodeId,
    selectedTaskCode,
  ]);

  const handleDragOver = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = "copy";
  };

  const handleDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    if (!instance) return;

    const raw = event.dataTransfer.getData(WORKFLOW_TASK_DRAG_TYPE);
    if (!raw) return;

    let taskType = "";
    try {
      const payload = JSON.parse(raw) as { taskType?: string };
      taskType = payload.taskType ?? "";
    } catch {
      return;
    }
    if (!taskType) return;

    draftSequenceRef.current += 1;
    const sequence = draftSequenceRef.current;
    const id = `draft:${sequence}`;
    const taskCode = -sequence;
    const position = instance.screenToFlowPosition({
      x: event.clientX,
      y: event.clientY,
    });

    const nextNode: WorkflowCanvasNode = {
      id,
      type: "workflowTask",
      position,
      data: {
        taskCode,
        taskType,
        name: `${taskType} 节点`,
      },
      draggable: false,
      selectable: true,
    };

    setDraftNodes((current) => [...current, nextNode]);
    setSelectedNodeId(id);
    setSelectedEdgeId(null);
  };

  return (
    <div className="relative h-full min-h-0 min-w-0 flex-1 bg-[#fbfcfe]">
      <ReactFlow<WorkflowCanvasNode, Edge>
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        onInit={setInstance}
        onNodeClick={(_event, node) => {
          setSelectedNodeId(node.id);
          setSelectedEdgeId(null);
        }}
        onEdgeClick={(_event, edge) => {
          setSelectedNodeId(null);
          setSelectedEdgeId(edge.id);
        }}
        onPaneClick={() => {
          setSelectedNodeId(null);
          setSelectedEdgeId(null);
        }}
        onConnect={handleConnect}
        isValidConnection={isValidConnection}
        onEdgesDelete={deleteEdges}
        onDragOver={handleDragOver}
        onDrop={handleDrop}
        fitView
        fitViewOptions={{ padding: 0.28, maxZoom: 1 }}
        nodesDraggable={false}
        nodesConnectable
        edgesFocusable
        edgesDeletable
        connectOnClick={false}
        connectionLineType={ConnectionLineType.SmoothStep}
        connectionLineStyle={{
          stroke: "var(--yak-color-primary)",
          strokeWidth: 1.5,
        }}
        connectionRadius={24}
        deleteKeyCode={["Backspace", "Delete"]}
        minZoom={0.25}
        maxZoom={1.8}
        proOptions={{ hideAttribution: true }}
      >
        <Background
          variant={BackgroundVariant.Dots}
          gap={16}
          size={1}
          color="#dfe4ec"
        />
        <Controls
          position="top-right"
          showInteractive={false}
          fitViewOptions={{ padding: 0.28, maxZoom: 1 }}
        />
      </ReactFlow>

      {nodes.length === 0 ? (
        <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
          <div className="rounded-md border border-dashed border-[var(--yak-components-border)] bg-white/80 px-5 py-4 text-center">
            <div className="text-sm font-medium">暂无工作流节点</div>
            <div className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
              点击“添加节点”，将节点类型拖入画布。
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}

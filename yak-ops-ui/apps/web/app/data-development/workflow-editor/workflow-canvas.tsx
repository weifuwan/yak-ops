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
  Controls,
  ReactFlow,
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
  const [draftNodes, setDraftNodes] = useState<WorkflowCanvasNode[]>([]);
  const draftSequenceRef = useRef(0);

  const canvas = useMemo(
    () => buildWorkflowCanvas(tasks, relations),
    [tasks, relations],
  );

  useEffect(() => {
    setDraftNodes([]);
  }, [tasks]);

  useEffect(() => {
    if (selectedTaskCode) {
      setSelectedNodeId(String(selectedTaskCode));
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

      if (!selectedNodeId) {
        void instance.fitView({
          duration: 220,
          padding: 0.28,
          maxZoom: 1,
        });
      }
    });

    return () => window.cancelAnimationFrame(frame);
  }, [instance, nodes, selectedNodeId, selectedTaskCode]);

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
  };

  return (
    <div className="relative h-full min-h-0 min-w-0 flex-1 bg-[#fbfcfe]">
      <ReactFlow<WorkflowCanvasNode, Edge>
        nodes={nodes}
        edges={canvas.edges}
        nodeTypes={nodeTypes}
        onInit={setInstance}
        onNodeClick={(_event, node) => setSelectedNodeId(node.id)}
        onPaneClick={() => setSelectedNodeId(null)}
        onDragOver={handleDragOver}
        onDrop={handleDrop}
        fitView
        fitViewOptions={{ padding: 0.28, maxZoom: 1 }}
        nodesDraggable={false}
        nodesConnectable={false}
        edgesFocusable={false}
        connectOnClick={false}
        deleteKeyCode={null}
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

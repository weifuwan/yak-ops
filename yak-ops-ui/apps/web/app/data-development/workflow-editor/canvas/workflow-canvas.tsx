import {
  useEffect,
  useMemo,
  useState,
  type DragEvent,
  type MouseEvent as ReactMouseEvent,
} from "react";
import { createPortal } from "react-dom";
import ReactFlow, {
  Background,
  BackgroundVariant,
  SelectionMode,
  type ReactFlowInstance,
} from "reactflow";
import "reactflow/dist/style.css";
import {
  ArrowDownToLine,
  ArrowUpFromLine,
  Check,
  FolderOpen,
  Hand,
  Maximize2,
  Minus,
  MousePointer2,
  Play,
  Plus,
  Spline,
  Unlink,
} from "lucide-react";
import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  Input,
  toast,
} from "@yak-ops/yak-ui";

import {
  generateWorkflowTaskCode,
  type WorkflowDagRecord,
} from "@/service/workflow/definition";
import { useWorkspace } from "@/hooks/use-workspace";
import {
  startWorkflowInstance,
  type WorkflowTaskDependType,
} from "@/service/workflow/execution";

import { useDataDevelopmentWorkbench } from "../../workbench-context";
import { WORKFLOW_TASK_DRAG_TYPE } from "../constants";
import { useWorkflowInteractions } from "../hooks/use-workflow-interactions";
import {
  useWorkflowStore,
} from "../store/workflow-store";
import type {
  WorkflowCanvasEdge,
  WorkflowCanvasNode,
  WorkflowEdgeData,
  WorkflowNodeData,
} from "../types";
import { WorkflowConnectionLine } from "./connection-line";
import { WorkflowEdge } from "./workflow-edge";
import { WorkflowTaskNode } from "./workflow-node";
import {
  CanvasSidePanel,
  type CanvasPanelKey,
} from "./canvas-side-panel";

const nodeTypes = {
  workflowTask: WorkflowTaskNode,
};

const edgeTypes = {
  workflowEdge: WorkflowEdge,
};

type NodeContextMenuState = {
  node: WorkflowCanvasNode;
  x: number;
  y: number;
};

type PendingNodeRun = {
  node: WorkflowCanvasNode;
  taskDependType: WorkflowTaskDependType;
  title: string;
  scopeDescription: string;
};

type PendingNodeRename = {
  id: string;
  name: string;
  taskType: string;
};

export function WorkflowCanvas({
  workflowCode,
  selectedTaskCode,
  workflowReleaseState,
  activeCanvasPanel,
  onCloseCanvasPanel,
  dag,
  onRefreshSpec,
  isRefreshingSpec,
}: {
  workflowCode: number;
  selectedTaskCode?: number;
  workflowReleaseState: string;
  activeCanvasPanel: CanvasPanelKey | null;
  onCloseCanvasPanel: () => void;
  dag: WorkflowDagRecord;
  onRefreshSpec: () => Promise<void>;
  isRefreshingSpec: boolean;
}) {
  const nodes = useWorkflowStore(
    (state) => state.nodes,
  );
  const edges = useWorkflowStore(
    (state) => state.edges,
  );
  const interactionMode = useWorkflowStore(
    (state) => state.interactionMode,
  );
  const edgeStyle = useWorkflowStore(
    (state) => state.edgeStyle,
  );
  const onNodesChange = useWorkflowStore(
    (state) => state.onNodesChange,
  );
  const onEdgesChange = useWorkflowStore(
    (state) => state.onEdgesChange,
  );
  const setInteractionMode = useWorkflowStore(
    (state) => state.setInteractionMode,
  );
  const setEdgeStyle = useWorkflowStore(
    (state) => state.setEdgeStyle,
  );
  const openSelector = useWorkflowStore(
    (state) => state.openSelector,
  );
  const selectorRequest = useWorkflowStore(
    (state) => state.selectorRequest,
  );
  const addDroppedNode = useWorkflowStore(
    (state) => state.addDroppedNode,
  );
  const renameDraftNode = useWorkflowStore(
    (state) => state.renameDraftNode,
  );
  const selectTaskCode = useWorkflowStore(
    (state) => state.selectTaskCode,
  );
  const clearSelection = useWorkflowStore(
    (state) => state.clearSelection,
  );
  const removeNodes = useWorkflowStore(
    (state) => state.removeNodes,
  );

  const { openTask } = useDataDevelopmentWorkbench();
  const { currentWorkspace } = useWorkspace();
  const projectCode = currentWorkspace?.id ?? "";
  const isWorkflowOnline =
    workflowReleaseState.toUpperCase() === "ONLINE";
  const edgeStyleLabel =
    edgeStyle === "bezier"
      ? "曲线"
      : edgeStyle === "step"
        ? "阶梯线"
        : "平滑阶梯线";

  const [contextMenu, setContextMenu] =
    useState<NodeContextMenuState | null>(null);
  const [contextMenuPortalRoot, setContextMenuPortalRoot] =
    useState<HTMLElement | null>(null);
  const [pendingRemoval, setPendingRemoval] =
    useState<WorkflowCanvasNode | null>(null);
  const [removalPending, setRemovalPending] = useState(false);
  const [pendingRun, setPendingRun] =
    useState<PendingNodeRun | null>(null);
  const [runPending, setRunPending] = useState(false);
  const [pendingNodeRename, setPendingNodeRename] =
    useState<PendingNodeRename | null>(null);
  const [nodeNameValue, setNodeNameValue] = useState("");
  const [nodeNameError, setNodeNameError] = useState<string | null>(null);

  const {
    handleConnect,
    handleNodesDelete,
    handleEdgesDelete,
    isValidConnection,
  } = useWorkflowInteractions();

  const [instance, setInstance] = useState<
    ReactFlowInstance<
      WorkflowNodeData,
      WorkflowEdgeData
    > | null
  >(null);
  const [zoomPercent, setZoomPercent] = useState(100);
  const [focusedNodeId, setFocusedNodeId] =
    useState<string | null>(null);
  const [hoveredEdgeId, setHoveredEdgeId] =
    useState<string | null>(null);
  const renderNodes = useMemo(
    () =>
      nodes.map((node) => ({
        ...node,
        data: {
          ...node.data,
          focused: node.id === focusedNodeId,
          readOnly: isWorkflowOnline,
        },
      })),
    [nodes, focusedNodeId, isWorkflowOnline],
  );
  const renderEdges = useMemo(
    () =>
      edges.map((edge) => ({
        ...edge,
        data: {
          backend: edge.data?.backend ?? false,
          hovered: edge.id === hoveredEdgeId,
          relation: edge.data?.relation,
        },
      })),
    [edges, hoveredEdgeId],
  );

  useEffect(() => {
    setContextMenuPortalRoot(document.body);
  }, []);

  useEffect(() => {
    if (
      focusedNodeId &&
      !nodes.some((node) => node.id === focusedNodeId)
    ) {
      setFocusedNodeId(null);
    }
  }, [focusedNodeId, nodes]);

  useEffect(() => {
    if (!instance || !selectedTaskCode) return;

    const nodeId = selectTaskCode(selectedTaskCode);
    if (!nodeId) return;

    const node = instance.getNode(nodeId);
    if (!node) return;

    void instance.fitView({
      nodes: [node],
      padding: 1.6,
      maxZoom: 1.15,
    });
  }, [instance, selectTaskCode, selectedTaskCode]);

  const handleLocateNode = (nodeId: string) => {
    if (!instance) return;

    const node = instance.getNode(nodeId);
    if (!node) {
      toast.warning("无法定位节点", {
        description: "该节点可能已被移除，请刷新列表后重试。",
      });
      return;
    }

    setFocusedNodeId(nodeId);
    void instance.fitView({
      nodes: [node],
      padding: 0.7,
      maxZoom: 1.15,
      duration: 360,
    });
  };

  const handleNodeContextMenu = (
    event: ReactMouseEvent,
    node: WorkflowCanvasNode,
  ) => {
    event.preventDefault();
    event.stopPropagation();
    setContextMenu({
      node,
      x: event.clientX,
      y: event.clientY,
    });
  };

  const openNode = (node: WorkflowCanvasNode) => {
    setContextMenu(null);
    const nodeWorkflowCode = node.data.workflowCode ?? workflowCode;
    const latestTask =
      node.data.taskCode === null
        ? node.data.task
        : dag.taskDefinitionList.find(
            (item) => item.code === node.data.taskCode,
          ) ?? node.data.task;

    if (latestTask) {
      openTask(nodeWorkflowCode, latestTask, node.id, workflowReleaseState);
      return;
    }

    toast.warning("节点配置尚未初始化，请重新添加节点");
  };

  const requestNodeRemoval = (node: WorkflowCanvasNode) => {
    setContextMenu(null);
    if (isWorkflowOnline) return;
    setPendingRemoval(node);
  };

  const confirmNodeRemoval = async () => {
    if (!pendingRemoval) return;
    if (isWorkflowOnline) {
      setPendingRemoval(null);
      return;
    }
    setRemovalPending(true);
    try {
      removeNodes([pendingRemoval.id]);
      setPendingRemoval(null);
      toast.success("节点已从工作流草稿移除，保存工作流后生效");
    } finally {
      setRemovalPending(false);
    }
  };

  const requestNodeRun = (
    node: WorkflowCanvasNode,
    taskDependType: WorkflowTaskDependType,
    title: string,
    scopeDescription: string,
  ) => {
    setContextMenu(null);
    setPendingRun({
      node,
      taskDependType,
      title,
      scopeDescription,
    });
  };

  const confirmNodeRun = async () => {
    if (!pendingRun || pendingRun.node.data.taskCode === null) return;

    setRunPending(true);
    try {
      const result = await startWorkflowInstance(
        String(workflowCode),
        {
          startNodeList: pendingRun.node.data.taskCode,
          taskDependType: pendingRun.taskDependType,
        },
      );
      toast.success("节点运行已提交", {
        description: "工作流实例 ID：" + result.workflowInstanceId,
      });
      setPendingRun(null);
    } catch (error) {
      toast.error("节点运行失败", {
        description:
          error instanceof Error ? error.message : "请稍后重试。",
      });
    } finally {
      setRunPending(false);
    }
  };

  const cancelNodeRename = () => {
    if (pendingNodeRename) {
      removeNodes([pendingNodeRename.id]);
    }
    setPendingNodeRename(null);
    setNodeNameError(null);
  };

  const finishNodeRename = () => {
    setPendingNodeRename(null);
    setNodeNameError(null);
  };

  const confirmNodeRename = () => {
    if (!pendingNodeRename) return;

    const normalizedName = nodeNameValue.trim();
    if (!normalizedName) {
      setNodeNameError("请输入节点名称");
      return;
    }

    const renamed = renameDraftNode(
      pendingNodeRename.id,
      normalizedName,
    );
    if (!renamed) {
      toast.warning("节点名称修改失败", {
        description: "该节点可能已被移除，请重新添加。",
      });
      finishNodeRename();
      return;
    }

    finishNodeRename();
  };

  const handleDragOver = (
    event: DragEvent<HTMLDivElement>,
  ) => {
    event.preventDefault();
    event.dataTransfer.dropEffect = "copy";
  };

  const handleDrop = async (
    event: DragEvent<HTMLDivElement>,
  ) => {
    event.preventDefault();
    if (!instance || isWorkflowOnline) return;

    const raw = event.dataTransfer.getData(
      WORKFLOW_TASK_DRAG_TYPE,
    );
    if (!raw) return;

    let taskType = "";
    try {
      const payload = JSON.parse(raw) as {
        taskType?: string;
      };
      taskType = payload.taskType ?? "";
    } catch {
      return;
    }

    if (!taskType) return;

    if (!projectCode) {
      toast.warning("请先选择工作空间");
      return;
    }
    try {
      const taskCode = await generateWorkflowTaskCode();
      const createdNode = addDroppedNode(
        taskType,
        instance.screenToFlowPosition({
          x: event.clientX,
          y: event.clientY,
        }),
        taskCode,
        Number(projectCode),
      );
      setPendingNodeRename({ ...createdNode, taskType });
      setNodeNameValue(createdNode.name);
      setNodeNameError(null);
    } catch (error) {
      toast.error("创建节点失败", {
        description: error instanceof Error ? error.message : "请稍后重试。",
      });
    }
  };

  return (
    <div className="flex h-full min-h-0 min-w-0 flex-1 overflow-hidden bg-[#f2f4f7]">
      <div className="relative min-h-0 min-w-0 flex-1 overflow-hidden">
      <ReactFlow
        id={`workflow-${workflowCode}`}
        nodes={renderNodes}
        edges={renderEdges}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        onInit={(flow) => {
          setInstance(flow);
          setZoomPercent(Math.round(flow.getZoom() * 100));

          if (selectedTaskCode) {
            const selectedNode = nodes.find(
              (node) =>
                node.data.taskCode === selectedTaskCode,
            );
            if (selectedNode) {
              void flow.fitView({
                nodes: [selectedNode],
                padding: 1.6,
                maxZoom: 1.15,
              });
              return;
            }
          }

          if (nodes.length > 0) {
            void flow.fitView({
              padding: 0.22,
              maxZoom: 1,
            });
          }
        }}
        onMove={(_event, viewport) => {
          setZoomPercent(Math.round(viewport.zoom * 100));
        }}
        onNodesChange={(changes) =>
          onNodesChange(
            isWorkflowOnline
              ? changes.filter((change) => change.type !== "remove" && change.type !== "position")
              : changes,
          )
        }
        onEdgesChange={(changes) =>
          onEdgesChange(
            isWorkflowOnline
              ? changes.filter((change) => change.type !== "remove")
              : changes,
          )
        }
        onNodesDelete={isWorkflowOnline ? undefined : handleNodesDelete}
        onEdgesDelete={isWorkflowOnline ? undefined : handleEdgesDelete}
        onConnect={isWorkflowOnline ? undefined : handleConnect}
        isValidConnection={isValidConnection}
        onPaneClick={() => {
          setContextMenu(null);
          setFocusedNodeId(null);
          clearSelection();
        }}
        onNodeClick={(_event, node) => setFocusedNodeId(node.id)}
        onNodeContextMenu={handleNodeContextMenu}
        onEdgeMouseEnter={(_event, edge) =>
          setHoveredEdgeId(edge.id)
        }
        onEdgeMouseLeave={() =>
          setHoveredEdgeId(null)
        }
        onDragOver={handleDragOver}
        onDrop={handleDrop}
        nodesDraggable={
          !isWorkflowOnline && interactionMode === "pointer"
        }
        nodesConnectable={
          !isWorkflowOnline && interactionMode === "pointer"
        }
        elementsSelectable={
          interactionMode === "pointer"
        }
        panOnDrag={interactionMode === "pan"}
        selectionOnDrag={
          interactionMode === "pointer"
        }
        selectionMode={SelectionMode.Partial}
        edgesFocusable
        connectOnClick={false}
        connectionLineComponent={WorkflowConnectionLine}
        connectionRadius={24}
        deleteKeyCode={["Backspace", "Delete"]}
        minZoom={0.25}
        maxZoom={1.8}
        proOptions={{ hideAttribution: true }}
      >
        <Background
          variant={BackgroundVariant.Dots}
          gap={16}
          size={1.5}
          color="#C5CBD5"
        />

      </ReactFlow>

      {contextMenu && contextMenuPortalRoot
        ? createPortal(
            <DropdownMenu
              open
              onOpenChange={(open) => {
                if (!open) setContextMenu(null);
              }}
            >
              <DropdownMenuTrigger
                aria-hidden="true"
                tabIndex={-1}
                className="pointer-events-none fixed size-px opacity-0"
                style={{
                  left: contextMenu.x,
                  top: contextMenu.y,
                }}
              />
              <DropdownMenuContent
                side="bottom"
                align="start"
                sideOffset={0}
                className="w-[216px] rounded-md p-1"
              >
                <DropdownMenuItem
                  onClick={() => openNode(contextMenu.node)}
                >
                  <FolderOpen size={14} aria-hidden="true" />
                  打开节点
                </DropdownMenuItem>
                <DropdownMenuItem
                  disabled={
                    !contextMenu.node.data.draft &&
                    (isWorkflowOnline ||
                      contextMenu.node.data.taskCode === null ||
                      nodes.filter(
                        (node) =>
                          !node.data.draft &&
                          node.data.taskCode !== null,
                      ).length <= 1)
                  }
                  title={
                    !contextMenu.node.data.draft && isWorkflowOnline
                      ? "在线工作流需要先下线才能修改"
                      : !contextMenu.node.data.draft &&
                          nodes.filter(
                            (node) =>
                              !node.data.draft &&
                              node.data.taskCode !== null,
                          ).length <= 1
                        ? "工作流至少需要保留一个节点"
                        : undefined
                  }
                  onClick={() =>
                    requestNodeRemoval(contextMenu.node)
                  }
                >
                  <Unlink size={14} aria-hidden="true" />
                  从工作流移除
                </DropdownMenuItem>

                <DropdownMenuSeparator />

                <DropdownMenuItem
                  disabled={
                    !isWorkflowOnline ||
                    contextMenu.node.data.draft ||
                    contextMenu.node.data.taskCode === null
                  }
                  title={
                    !isWorkflowOnline
                      ? "请先发布工作流"
                      : contextMenu.node.data.draft ||
                          contextMenu.node.data.taskCode === null
                        ? "未保存节点暂不支持运行"
                        : undefined
                  }
                  onClick={() =>
                    requestNodeRun(
                      contextMenu.node,
                      "TASK_ONLY",
                      "运行该节点",
                      "仅执行当前节点。",
                    )
                  }
                >
                  <Play size={14} aria-hidden="true" />
                  运行该节点
                </DropdownMenuItem>
                <DropdownMenuItem
                  disabled={
                    !isWorkflowOnline ||
                    contextMenu.node.data.draft ||
                    contextMenu.node.data.taskCode === null
                  }
                  title={
                    !isWorkflowOnline
                      ? "请先发布工作流"
                      : contextMenu.node.data.draft ||
                          contextMenu.node.data.taskCode === null
                        ? "未保存节点暂不支持运行"
                        : undefined
                  }
                  onClick={() =>
                    requestNodeRun(
                      contextMenu.node,
                      "TASK_PRE",
                      "运行到该节点",
                      "执行当前节点及其所有上游依赖节点。",
                    )
                  }
                >
                  <ArrowDownToLine size={14} aria-hidden="true" />
                  运行到该节点
                </DropdownMenuItem>
                <DropdownMenuItem
                  disabled={
                    !isWorkflowOnline ||
                    contextMenu.node.data.draft ||
                    contextMenu.node.data.taskCode === null
                  }
                  title={
                    !isWorkflowOnline
                      ? "请先发布工作流"
                      : contextMenu.node.data.draft ||
                          contextMenu.node.data.taskCode === null
                        ? "未保存节点暂不支持运行"
                        : undefined
                  }
                  onClick={() =>
                    requestNodeRun(
                      contextMenu.node,
                      "TASK_POST",
                      "从该节点运行",
                      "从当前节点开始，执行其所有下游依赖节点。",
                    )
                  }
                >
                  <ArrowUpFromLine size={14} aria-hidden="true" />
                  从该节点运行
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>,
            contextMenuPortalRoot,
          )
        : null}

      <Dialog
        open={Boolean(pendingRemoval)}
        onOpenChange={(open) => {
          if (!open && !removalPending) setPendingRemoval(null);
        }}
      >
        {pendingRemoval ? (
          <DialogContent className="w-[420px]">
            <DialogTitle className="text-base font-semibold">
              从工作流移除节点
            </DialogTitle>
            <DialogDescription className="mt-2 text-sm leading-6 text-[var(--yak-components-muted-text)]">
              {pendingRemoval.data.draft ||
              pendingRemoval.data.taskCode === null
                ? "该节点尚未保存，只会从当前画布移除。"
                : "确定将“" +
                  pendingRemoval.data.name +
                  "”从当前工作流移除吗？相关连线也会一并移除，任务定义本身不会删除。"}
            </DialogDescription>
            <div className="mt-6 flex justify-end gap-2">
              <Button
                size="sm"
                variant="secondary"
                isDisabled={removalPending}
                onClick={() => setPendingRemoval(null)}
              >
                取消
              </Button>
              <Button
                size="sm"
                variant="danger"
                isPending={removalPending}
                onClick={() => void confirmNodeRemoval()}
              >
                移除
              </Button>
            </div>
          </DialogContent>
        ) : null}
      </Dialog>

      <Dialog
        open={Boolean(pendingNodeRename)}
        onOpenChange={(open) => {
          if (!open) cancelNodeRename();
        }}
      >
        {pendingNodeRename ? (
          <DialogContent className="w-[420px]">
            <DialogTitle className="text-base font-semibold">
              设置节点名称
            </DialogTitle>
            <div className="mt-5">
              <Input
                id="workflow-draft-node-name"
                autoFocus
                aria-label="节点名称"
                aria-invalid={Boolean(nodeNameError)}
                maxLength={128}
                value={nodeNameValue}
                placeholder="请输入节点名称"
                onChange={(event) => {
                  setNodeNameValue(event.target.value);
                  if (nodeNameError) setNodeNameError(null);
                }}
                onFocus={(event) => event.currentTarget.select()}
                onKeyDown={(event) => {
                  if (
                    event.key === "Enter" &&
                    !event.nativeEvent.isComposing
                  ) {
                    event.preventDefault();
                    confirmNodeRename();
                  }
                }}
              />
              {nodeNameError ? (
                <p className="mt-1.5 text-xs text-[var(--yak-color-danger)]">
                  {nodeNameError}
                </p>
              ) : null}
            </div>
            <div className="mt-6 flex justify-end gap-2">
              <Button
                size="sm"
                variant="secondary"
                onClick={cancelNodeRename}
              >
                取消
              </Button>
              <Button
                size="sm"
                variant="primary"
                isDisabled={!nodeNameValue.trim()}
                onClick={confirmNodeRename}
              >
                确定
              </Button>
            </div>
          </DialogContent>
        ) : null}
      </Dialog>

      <Dialog
        open={Boolean(pendingRun)}
        onOpenChange={(open) => {
          if (!open && !runPending) setPendingRun(null);
        }}
      >
        {pendingRun ? (
          <DialogContent className="w-[420px]">
            <DialogTitle className="text-base font-semibold">
              {pendingRun.title}
            </DialogTitle>
            <DialogDescription className="mt-2 text-sm leading-6 text-[var(--yak-components-muted-text)]">
              将创建一次新的工作流实例，{pendingRun.scopeDescription}
            </DialogDescription>
            <div className="mt-6 flex justify-end gap-2">
              <Button
                size="sm"
                variant="secondary"
                isDisabled={runPending}
                onClick={() => setPendingRun(null)}
              >
                取消
              </Button>
              <Button
                size="sm"
                isPending={runPending}
                onClick={() => void confirmNodeRun()}
              >
                确认运行
              </Button>
            </div>
          </DialogContent>
        ) : null}
      </Dialog>

      <div className="pointer-events-none absolute inset-y-0 left-0 z-10 flex w-12 items-center justify-center pl-2">
        <div className="pointer-events-auto flex flex-col items-center rounded-lg border-[0.5px] border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-0.5 text-[var(--yak-components-input-icon)] shadow-lg">
          <button
            type="button"
            title="新建节点"
            aria-label="新建节点"
            disabled={isWorkflowOnline}
            aria-expanded={selectorRequest?.kind === "add"}
            onClick={() => openSelector({ kind: "add" })}
            className="flex size-8 cursor-pointer items-center justify-center rounded-md transition-colors hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-components-panel-text)] data-[selector-open=true]:bg-[var(--yak-color-primary-subtle)] data-[selector-open=true]:text-[var(--yak-color-primary)] disabled:cursor-not-allowed disabled:opacity-40"
            data-selector-open={selectorRequest?.kind === "add" ? "true" : undefined}
          >
            <svg viewBox="0 0 16 16" className="size-4" aria-hidden="true">
              <circle cx="8" cy="8" r="7" fill="currentColor" />
              <path
                d="M8 4.5v7M4.5 8h7"
                stroke="var(--yak-components-panel-bg)"
                strokeWidth="1.6"
                strokeLinecap="round"
              />
            </svg>
          </button>

          <div className="my-1 h-[0.5px] w-3.5 bg-[var(--yak-components-control-border)]" />

          <button
            type="button"
            title="指针模式"
            aria-label="指针模式"
            aria-pressed={interactionMode === "pointer"}
            onClick={() => setInteractionMode("pointer")}
            className={[
              "flex size-8 cursor-pointer items-center justify-center rounded-md transition-colors",
              interactionMode === "pointer"
                ? "bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]"
                : "text-[var(--yak-components-input-icon)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-components-panel-text)]",
            ].join(" ")}
          >
            <MousePointer2 size={16} strokeWidth={1.7} />
          </button>
          <button
            type="button"
            title="抓手模式"
            aria-label="抓手模式"
            aria-pressed={interactionMode === "pan"}
            onClick={() => setInteractionMode("pan")}
            className={[
              "flex size-8 cursor-pointer items-center justify-center rounded-md transition-colors",
              interactionMode === "pan"
                ? "bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]"
                : "text-[var(--yak-components-input-icon)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-components-panel-text)]",
            ].join(" ")}
          >
            <Hand size={16} strokeWidth={1.7} />
          </button>

          <div className="my-1 h-[0.5px] w-3.5 bg-[var(--yak-components-control-border)]" />

          <button
            type="button"
            title="适应画布"
            aria-label="适应画布"
            disabled={!instance}
            onClick={() =>
              instance?.fitView({
                padding: 0.22,
                maxZoom: 1,
                duration: 180,
              })
            }
            className="flex size-8 cursor-pointer items-center justify-center rounded-md text-[var(--yak-components-input-icon)] transition-colors hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-components-panel-text)] disabled:cursor-not-allowed disabled:opacity-40"
          >
            <Maximize2 size={16} strokeWidth={1.7} />
          </button>

          <DropdownMenu>
            <DropdownMenuTrigger
              aria-label={`连线样式：${edgeStyleLabel}`}
              title={`连线样式：${edgeStyleLabel}`}
              className="flex size-8 cursor-pointer items-center justify-center rounded-md text-[var(--yak-components-input-icon)] outline-none transition-colors hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-components-panel-text)] data-[popup-open]:bg-[var(--yak-color-hover)] data-[popup-open]:text-[var(--yak-color-primary)]"
            >
              <Spline size={16} strokeWidth={1.7} />
            </DropdownMenuTrigger>
            <DropdownMenuContent
              side="right"
              align="start"
              sideOffset={8}
              className="w-44"
            >
              <div className="px-2.5 py-2 text-[11px] font-medium text-[var(--yak-components-muted-text)]">
                连线样式
              </div>
              <DropdownMenuItem
                onClick={() => setEdgeStyle("bezier")}
                className={
                  edgeStyle === "bezier"
                    ? "bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]"
                    : ""
                }
              >
                <svg viewBox="0 0 32 18" className="h-5 w-8 shrink-0" aria-hidden="true">
                  <path
                    d="M2 14 C10 14 9 4 16 4 S22 14 30 4"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.8"
                    strokeLinecap="round"
                  />
                </svg>
                <span className="flex-1">曲线</span>
                {edgeStyle === "bezier" ? <Check size={14} /> : null}
              </DropdownMenuItem>
              <DropdownMenuItem
                onClick={() => setEdgeStyle("step")}
                className={
                  edgeStyle === "step"
                    ? "bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]"
                    : ""
                }
              >
                <svg viewBox="0 0 32 18" className="h-5 w-8 shrink-0" aria-hidden="true">
                  <path
                    d="M2 14 H12 V4 H22 V14 H30"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.8"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
                <span className="flex-1">阶梯线</span>
                {edgeStyle === "step" ? <Check size={14} /> : null}
              </DropdownMenuItem>
              <DropdownMenuItem
                onClick={() => setEdgeStyle("smoothstep")}
                className={
                  edgeStyle === "smoothstep"
                    ? "bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]"
                    : ""
                }
              >
                <svg viewBox="0 0 32 18" className="h-5 w-8 shrink-0" aria-hidden="true">
                  <path
                    d="M2 14 H9 Q12 14 12 11 V7 Q12 4 15 4 H19 Q22 4 22 7 V11 Q22 14 25 14 H30"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.8"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
                <span className="flex-1">平滑阶梯线</span>
                {edgeStyle === "smoothstep" ? <Check size={14} /> : null}
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>

      <div className="pointer-events-none absolute bottom-3 right-3 z-10">
        <div className="pointer-events-auto flex h-9 items-center rounded-lg border-[0.5px] border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-0.5 text-[13px] text-[var(--yak-components-input-icon)] shadow-lg">
          <button
            type="button"
            title="缩小"
            aria-label="缩小画布"
            disabled={!instance}
            onClick={() => instance?.zoomOut({ duration: 160 })}
            className="flex size-8 cursor-pointer items-center justify-center rounded-md transition-colors hover:bg-[var(--yak-color-hover)] disabled:cursor-not-allowed disabled:opacity-40"
          >
            <Minus size={15} strokeWidth={1.8} />
          </button>
          <button
            type="button"
            title="点击恢复到 100%"
            aria-label={`当前缩放比例 ${zoomPercent}%，点击恢复到 100%`}
            disabled={!instance}
            onClick={() => instance?.zoomTo(1, { duration: 160 })}
            className="flex h-8 min-w-10 cursor-pointer items-center justify-center rounded-md px-1 text-xs font-medium tabular-nums text-[var(--yak-components-panel-text)] transition-colors hover:bg-[var(--yak-color-hover)] disabled:cursor-not-allowed disabled:opacity-40"
          >
            {zoomPercent}%
          </button>
          <button
            type="button"
            title="放大"
            aria-label="放大画布"
            disabled={!instance}
            onClick={() => instance?.zoomIn({ duration: 160 })}
            className="flex size-8 cursor-pointer items-center justify-center rounded-md transition-colors hover:bg-[var(--yak-color-hover)] disabled:cursor-not-allowed disabled:opacity-40"
          >
            <Plus size={15} strokeWidth={1.8} />
          </button>
        </div>
      </div>

      </div>
      <CanvasSidePanel
        activePanel={activeCanvasPanel}
        onClose={onCloseCanvasPanel}
        dag={dag}
        nodes={nodes}
        focusedNodeId={focusedNodeId}
        onLocateNode={handleLocateNode}
        onRefresh={onRefreshSpec}
        isRefreshing={isRefreshingSpec}
      />
    </div>
  );
}

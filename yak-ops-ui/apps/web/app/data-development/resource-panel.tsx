import { useDeferredValue, useEffect, useMemo, useRef, useState } from "react";
import { useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Bot,
  Copy,
  CopyPlus,
  ExternalLink,
  FolderInput,
  Pencil,
  RefreshCw,
  Search,
  Star,
  Trash2,
  X,
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
  PanelHeader,
  toast,
} from "@yak-ops/yak-ui";

import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { listUserPreferences, updateUserPreferenceFavorite } from "@/service/preference";
import {
  copyWorkflowDefinition,
  deleteWorkflowDefinition,
  listWorkflowDefinitions,
  listWorkflowTasks,
  moveWorkflowDefinition,
  renameWorkflowDefinition,
  renameWorkflowTask,
  type WorkflowDefinitionRecord,
  type WorkflowTaskRecord,
} from "@/service/workflow/definition";

import {
  VirtualTree,
  type VirtualTreeNode,
} from "./virtual-tree";
import { useDataDevelopmentWorkbench } from "./workbench-context";
import { TaskTypeBadgeIcon } from "./workflow-editor/components/task-type-badge-icon";
import { WorkflowTreeIcon } from "./workflow-editor/components/workflow-tree-icon";

const PAGE_SIZE = 200;
const workflowExplorerKey = ["data-development", "workflow-explorer"] as const;

type WorkflowScope = "ALL" | "MINE";

type WorkflowTreeNode =
  | (VirtualTreeNode & {
      kind: "workflow";
      workflow: WorkflowDefinitionRecord;
    })
  | (VirtualTreeNode & {
      kind: "task";
      workflow: WorkflowDefinitionRecord;
      task: WorkflowTaskRecord;
    })
  | (VirtualTreeNode & {
      kind: "message";
      workflow: WorkflowDefinitionRecord;
      state: "loading" | "error" | "empty";
      retry?: () => void;
    });

const workflowNodeId = (code: number) => "workflow-tree-" + code;
const taskNodeId = (workflowCode: number, taskCode: number) =>
  "workflow-tree-" + workflowCode + "-task-" + taskCode;

type ActionableWorkflowTreeNode = Extract<
  WorkflowTreeNode,
  { kind: "workflow" | "task" }
>;
type WorkflowTreeWorkflowNode = Extract<WorkflowTreeNode, { kind: "workflow" }>;
type ResourceActionDialog =
  | { kind: "rename"; item: ActionableWorkflowTreeNode }
  | { kind: "move"; item: WorkflowTreeWorkflowNode }
  | { kind: "delete"; item: WorkflowTreeWorkflowNode };

const DATA_DEVELOPMENT_PREFERENCE_SCENE = "DATA_DEVELOPMENT_RESOURCE" as const;
const preferenceItemKey = (item: ActionableWorkflowTreeNode) =>
  item.kind === "workflow"
    ? "workflow:" + item.workflow.code
    : "task:" + item.workflow.code + ":" + item.task.code;

const describeRecord = (record: {
  modifyBy?: string | null;
  updateTime?: string | null;
}) =>
  [
    record.modifyBy ? record.modifyBy + "修改" : null,
    record.updateTime || null,
  ]
    .filter(Boolean)
    .join(" ");

function WorkflowTreeContent({ item }: { item: WorkflowTreeNode }) {
  if (item.kind === "message") {
    if (item.state === "loading") {
      return (
        <span className="min-w-0 flex-1 truncate px-1 text-[11px] text-[var(--yak-components-muted-text)]">
          正在加载节点…
        </span>
      );
    }

    if (item.state === "error") {
      return (
        <div className="flex min-w-0 flex-1 items-center justify-between gap-2 pr-2">
          <span className="truncate px-1 text-[11px] text-[var(--yak-color-danger)]">
            节点加载失败
          </span>
          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              item.retry?.();
            }}
            className="shrink-0 cursor-pointer text-[11px] text-[var(--yak-color-primary)] hover:underline"
          >
            重试
          </button>
        </div>
      );
    }

    return (
      <span className="min-w-0 flex-1 truncate px-1 text-[11px] text-[var(--yak-components-muted-text)]">
        暂无节点
      </span>
    );
  }

  if (item.kind === "workflow") {
    const description = describeRecord(item.workflow);
    const online = item.workflow.releaseState?.toUpperCase() === "ONLINE";

    return (
      <div className="flex min-w-0 flex-1 items-center gap-1.5 pr-2">
        <span
          title={item.workflow.releaseState || "未知状态"}
          aria-label={item.workflow.releaseState || "未知状态"}
          className={[
            "size-1.5 shrink-0 rounded-full",
            online
              ? "bg-[var(--yak-color-success)]"
              : "bg-[var(--yak-color-primary)]",
          ].join(" ")}
        />
        <WorkflowTreeIcon size={14} />
        <span className="min-w-0 shrink truncate font-medium">
          {item.workflow.name}
        </span>
        {description ? (
          <span className="min-w-0 flex-1 truncate text-[10px] text-[var(--yak-components-muted-text)]">
            {description}
          </span>
        ) : null}
      </div>
    );
  }

  const description = describeRecord(item.task);

  return (
    <div className="flex min-w-0 flex-1 items-center gap-1.5 pr-2">
      <span className="size-1.5 shrink-0" aria-hidden="true" />
      <TaskTypeBadgeIcon
        taskType={item.task.taskType}
        size={14}
      />
      <span className="min-w-0 shrink truncate">{item.task.name}</span>
      {description ? (
        <span className="min-w-0 flex-1 truncate text-[10px] text-[var(--yak-components-muted-text)]">
          {description}
        </span>
      ) : (
        <span className="min-w-0 flex-1" />
      )}
      <span className="shrink-0 text-[10px] text-[var(--yak-components-muted-text)]">
        {item.task.taskType}
      </span>
    </div>
  );
}

export function DataDevelopmentResourcePanel({ width }: { width: number }) {
  const { currentUser } = useAuth();
  const { workspaces, currentWorkspace } = useWorkspace();
  const {
    activeTab,
    openTask,
    openWorkflow,
    updateTabTitle,
    closeTab,
  } = useDataDevelopmentWorkbench();
  const queryClient = useQueryClient();
  const treeViewportRef = useRef<HTMLDivElement | null>(null);
  const [search, setSearch] = useState("");
  const [scope, setScope] = useState<WorkflowScope>("ALL");
  const [expandedCodes, setExpandedCodes] = useState<Set<number>>(
    () => new Set(),
  );
  const [contextMenu, setContextMenu] = useState<{
    item: ActionableWorkflowTreeNode;
    x: number;
    y: number;
  } | null>(null);
  const [dialog, setDialog] = useState<ResourceActionDialog | null>(null);
  const [renameValue, setRenameValue] = useState("");
  const [targetWorkspaceId, setTargetWorkspaceId] = useState("");
  const [actionPending, setActionPending] = useState(false);
  const deferredSearch = useDeferredValue(search.trim());
  const workspaceId = currentWorkspace?.id ?? "";
  const preferenceQueryKey = [
    "user-preferences",
    DATA_DEVELOPMENT_PREFERENCE_SCENE,
    currentUser?.id ?? "",
  ] as const;

  const preferencesQuery = useQuery({
    queryKey: preferenceQueryKey,
    queryFn: ({ signal }) =>
      listUserPreferences(DATA_DEVELOPMENT_PREFERENCE_SCENE, signal),
    enabled: Boolean(currentUser?.id),
  });

  useEffect(() => {
    if (activeTab?.kind !== "task") return;

    setExpandedCodes((current) => {
      if (current.has(activeTab.workflowCode)) return current;

      const next = new Set(current);
      next.add(activeTab.workflowCode);
      return next;
    });
  }, [activeTab]);
  const userId =
    scope === "MINE" && currentUser?.id ? Number(currentUser.id) : undefined;

  const workflowsQuery = useQuery({
    queryKey: [
      ...workflowExplorerKey,
      workspaceId,
      "list",
      scope,
      deferredSearch,
      userId ?? 0,
    ],
    queryFn: ({ signal }) =>
      listWorkflowDefinitions(
        {
          pageNo: 1,
          pageSize: PAGE_SIZE,
          searchVal: deferredSearch || undefined,
          userId,
        },
        signal,
      ),
    enabled: Boolean(workspaceId),
  });

  const workflows = workflowsQuery.data?.totalList ?? [];
  const expandedWorkflows = workflows.filter((workflow) =>
    expandedCodes.has(workflow.code),
  );

  const taskQueries = useQueries({
    queries: expandedWorkflows.map((workflow) => ({
      queryKey: [
        ...workflowExplorerKey,
        workspaceId,
        "tasks",
        workflow.code,
      ],
      queryFn: ({ signal }) => listWorkflowTasks(workflow.code, signal),
    })),
  });

  const taskQueryByWorkflowCode = useMemo(
    () =>
      new Map(
        expandedWorkflows.map((workflow, index) => [
          workflow.code,
          taskQueries[index],
        ]),
      ),
    [expandedWorkflows, taskQueries],
  );

  const treeItems = useMemo<WorkflowTreeNode[]>(() => {
    const items: WorkflowTreeNode[] = [];

    for (const workflow of workflows) {
      const expanded = expandedCodes.has(workflow.code);
      const workflowSelected =
        activeTab?.kind === "workflow" &&
        activeTab.workflowCode === workflow.code;

      items.push({
        id: workflowNodeId(workflow.code),
        level: 1,
        ariaLabel: [workflow.name, describeRecord(workflow)]
          .filter(Boolean)
          .join(" "),
        expandable: true,
        expanded,
        selected: workflowSelected,
        workflow,
        kind: "workflow",
      });

      if (!expanded) continue;

      const taskQuery = taskQueryByWorkflowCode.get(workflow.code);
      const childBase = {
        parentId: workflowNodeId(workflow.code),
        level: 2,
        workflow,
      } as const;

      if (!taskQuery || taskQuery.isPending) {
        items.push({
          ...childBase,
          id: workflowNodeId(workflow.code) + "-loading",
          ariaLabel: "正在加载节点",
          kind: "message",
          state: "loading",
        });
        continue;
      }

      if (taskQuery.isError) {
        items.push({
          ...childBase,
          id: workflowNodeId(workflow.code) + "-error",
          ariaLabel: "节点加载失败",
          kind: "message",
          state: "error",
          retry: () => {
            void taskQuery.refetch();
          },
        });
        continue;
      }

      const tasks = taskQuery.data ?? [];
      if (tasks.length === 0) {
        items.push({
          ...childBase,
          id: workflowNodeId(workflow.code) + "-empty",
          ariaLabel: "暂无节点",
          kind: "message",
          state: "empty",
        });
        continue;
      }

      for (const task of tasks) {
        items.push({
          ...childBase,
          id: taskNodeId(workflow.code, task.code),
          ariaLabel: [task.name, task.taskType, describeRecord(task)]
            .filter(Boolean)
            .join(" "),
          selected:
            activeTab?.kind === "task" &&
            activeTab.workflowCode === workflow.code &&
            activeTab.task.code === task.code,
          kind: "task",
          task,
        });
      }
    }

    return items;
  }, [
    activeTab,
    expandedCodes,
    taskQueryByWorkflowCode,
    workflows,
  ]);

  const toggleWorkflow = (workflowCode: number) => {
    setExpandedCodes((current) => {
      const next = new Set(current);
      if (next.has(workflowCode)) {
        next.delete(workflowCode);
      } else {
        next.add(workflowCode);
      }
      return next;
    });
  };

  const refresh = async () => {
    await queryClient.invalidateQueries({
      queryKey: [...workflowExplorerKey, workspaceId],
    });
  };

  const isFavorite = (item: ActionableWorkflowTreeNode) =>
    Boolean(
      preferencesQuery.data?.some(
        (record) =>
          record.itemKey === preferenceItemKey(item) && record.favorite,
      ),
    );

  const resourcePath = (item: ActionableWorkflowTreeNode) => {
    const projectName = currentWorkspace?.name || "当前项目";
    return item.kind === "workflow"
      ? `/${projectName}/${item.workflow.name}`
      : `/${projectName}/${item.workflow.name}/${item.task.name}`;
  };

  const copyText = async (value: string, label: string) => {
    try {
      if (!navigator.clipboard?.writeText) {
        throw new Error("Clipboard API unavailable");
      }
      await navigator.clipboard.writeText(value);
      toast.success(`${label}已复制`);
    } catch {
      toast.error("复制失败，请检查浏览器剪贴板权限");
    }
  };

  const toggleFavorite = async (item: ActionableWorkflowTreeNode) => {
    const favorite = isFavorite(item);
    try {
      await updateUserPreferenceFavorite(
        DATA_DEVELOPMENT_PREFERENCE_SCENE,
        preferenceItemKey(item),
        { favorite: !favorite },
      );
      await queryClient.invalidateQueries({ queryKey: preferenceQueryKey });
      toast.success(favorite ? "已取消收藏" : "已收藏");
    } catch {
      // The HTTP layer reports the request failure.
    }
  };

  const openResource = (item: ActionableWorkflowTreeNode) => {
    if (item.kind === "workflow") {
      openWorkflow(item.workflow);
    } else {
      openTask(item.workflow.code, item.task);
    }
  };

  const cloneWorkflow = async (item: ActionableWorkflowTreeNode) => {
    if (item.kind !== "workflow" || !workspaceId) return;
    try {
      await copyWorkflowDefinition(item.workflow.code, Number(workspaceId));
      toast.success("工作流克隆成功");
      await refresh();
    } catch {
      // The HTTP layer reports the request failure.
    }
  };

  const openRenameDialog = (item: ActionableWorkflowTreeNode) => {
    setRenameValue(item.kind === "workflow" ? item.workflow.name : item.task.name);
    setDialog({ kind: "rename", item });
  };

  const openMoveDialog = (item: WorkflowTreeWorkflowNode) => {
    const nextTarget = workspaces.find((workspace) => workspace.id !== workspaceId);
    setTargetWorkspaceId(nextTarget?.id ?? "");
    setDialog({ kind: "move", item });
  };

  const openDeleteDialog = (item: WorkflowTreeWorkflowNode) => {
    setDialog({ kind: "delete", item });
  };

  const submitDialog = async () => {
    if (!dialog || actionPending) return;

    setActionPending(true);
    try {
      if (dialog.kind === "rename") {
        const name = renameValue.trim();
        if (!name) {
          toast.error("名称不能为空");
          return;
        }

        if (dialog.item.kind === "workflow") {
          await renameWorkflowDefinition(dialog.item.workflow.code, name);
          updateTabTitle("workflow:" + dialog.item.workflow.code, name);
        } else {
          await renameWorkflowTask(
            dialog.item.workflow.code,
            dialog.item.task.code,
            name,
          );
          updateTabTitle(
            "task:" + dialog.item.workflow.code + ":" + dialog.item.task.code,
            name,
          );
        }
        toast.success("重命名成功");
      } else if (dialog.kind === "move") {
        if (!targetWorkspaceId) {
          toast.error("请选择目标项目");
          return;
        }
        await moveWorkflowDefinition(
          dialog.item.workflow.code,
          Number(targetWorkspaceId),
        );
        closeTab("workflow:" + dialog.item.workflow.code);
        toast.success("工作流已移动");
      } else {
        await deleteWorkflowDefinition(dialog.item.workflow.code);
        closeTab("workflow:" + dialog.item.workflow.code);
        toast.success("工作流已删除");
      }

      setDialog(null);
      await refresh();
    } catch {
      // Keep the dialog open so the user can review and retry.
    } finally {
      setActionPending(false);
    }
  };

  return (
    <aside
      aria-label="数据开发"
      className="flex shrink-0 flex-col bg-[var(--yak-components-panel-bg)]"
      style={{ width }}
    >
      <PanelHeader
        title="数据开发"
        ariaLabel="数据开发操作"
        moreActions={
          <DropdownMenuItem onClick={() => void refresh()}>
            <RefreshCw size={14} aria-hidden="true" />
            刷新
          </DropdownMenuItem>
        }
      />

      <div className="px-3 pb-2 pt-1.5">
        <div className="flex h-[30px] min-w-0 items-center rounded-[var(--yak-radius-control-small)] border border-[var(--yak-components-input-border)] bg-[var(--yak-components-input-bg-focus)] transition-colors hover:border-[var(--yak-components-input-border-hover)] focus-within:border-[var(--yak-components-input-border-focus)]">
          <Input
            size="small"
            variant="filled"
            aria-label="搜索名称 / 节点ID / 修改人"
            placeholder="搜索名称 / 节点ID / 修改人"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                event.preventDefault();
                void workflowsQuery.refetch();
              }
            }}
            className="h-full min-w-0 flex-1 rounded-none border-0 bg-transparent px-2.5 text-xs shadow-none hover:border-transparent hover:bg-transparent focus:border-transparent focus:bg-transparent"
          />
          {search ? (
            <button
              type="button"
              aria-label="清除搜索"
              title="清除搜索"
              onClick={() => setSearch("")}
              className="flex size-6 shrink-0 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] transition-colors hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-components-panel-text)]"
            >
              <X size={13} aria-hidden="true" />
            </button>
          ) : null}
          <button
            type="button"
            aria-label="搜索"
            title="搜索"
            onClick={() => void workflowsQuery.refetch()}
            disabled={workflowsQuery.isFetching}
            className="mr-1 flex size-6 shrink-0 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] transition-colors hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-color-primary)] focus-visible:outline focus-visible:outline-1 focus-visible:outline-[var(--yak-color-primary)] disabled:cursor-not-allowed disabled:opacity-50"
          >
            <Search size={14} aria-hidden="true" />
          </button>
        </div>
      </div>

      <div className="mx-3 mb-2 grid h-7 grid-cols-2 bg-[var(--yak-color-hover)] text-xs">
        {([
          ["ALL", "全部"],
          ["MINE", "我负责的"],
        ] as const).map(([value, label]) => (
          <button
            key={value}
            type="button"
            onClick={() => setScope(value)}
            className={[
              "cursor-pointer px-2 transition-colors",
              scope === value
                ? "bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]"
                : "text-[var(--yak-components-muted-text)] hover:text-[var(--yak-components-panel-text)]",
            ].join(" ")}
          >
            {label}
          </button>
        ))}
      </div>

      <div ref={treeViewportRef} className="min-h-0 flex-1 px-2 pb-2">
        {workflowsQuery.isPending ? (
          <div className="px-3 py-8 text-center text-xs text-[var(--yak-components-muted-text)]">
            正在加载工作流…
          </div>
        ) : workflowsQuery.isError ? (
          <div className="px-3 py-8 text-center">
            <div className="text-xs text-[var(--yak-color-danger)]">
              工作流加载失败
            </div>
            <Button
              size="sm"
              variant="secondary"
              className="mt-3"
              onClick={() => void workflowsQuery.refetch()}
            >
              重试
            </Button>
          </div>
        ) : workflows.length === 0 ? (
          <div className="px-3 py-8 text-center text-xs text-[var(--yak-components-muted-text)]">
            {deferredSearch ? "没有匹配的工作流" : "暂无工作流"}
          </div>
        ) : (
          <VirtualTree
            ariaLabel="项目目录"
            items={treeItems}
            rowHeight={26}
            className="h-full"
            onToggle={(item) => {
              if (item.kind === "workflow") {
                toggleWorkflow(item.workflow.code);
              }
            }}
            onActivate={(item) => {
              if (item.kind === "workflow") {
                openWorkflow(item.workflow);
              } else if (item.kind === "task") {
                openTask(item.workflow.code, item.task);
              }
            }}
            onContextMenu={(item, event) => {
              if (item.kind === "message") return;
              setContextMenu({
                item,
                x: event.clientX,
                y: event.clientY,
              });
            }}
            renderItem={(item) => <WorkflowTreeContent item={item} />}
          />
        )}
      </div>

      {(workflowsQuery.data?.total ?? 0) > workflows.length ? (
        <div className="shrink-0 border-t border-[var(--yak-components-border)] px-3 py-2 text-center text-[11px] text-[var(--yak-components-muted-text)]">
          当前显示前 {workflows.length} 个工作流
        </div>
      ) : null}

      {contextMenu ? (
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
            style={{ left: contextMenu.x, top: contextMenu.y }}
          />
          <DropdownMenuContent
            side="bottom"
            align="start"
            sideOffset={0}
            collisionBoundary={treeViewportRef.current}
            collisionPadding={8}
            collisionAvoidance={{
              side: "shift",
              align: "shift",
              fallbackAxisSide: "none",
            }}
            className="w-[228px] rounded-md p-1"
          >
            <DropdownMenuItem disabled title="DataAgent 功能尚未接入">
              <Bot size={14} aria-hidden="true" />
              添加到 DataAgent
            </DropdownMenuItem>
            <DropdownMenuItem
              disabled={contextMenu.item.kind !== "workflow"}
              title={
                contextMenu.item.kind === "task"
                  ? "单节点克隆尚未接入"
                  : undefined
              }
              onClick={() => void cloneWorkflow(contextMenu.item)}
            >
              <CopyPlus size={14} aria-hidden="true" />
              克隆
            </DropdownMenuItem>
            <DropdownMenuItem
              onClick={() =>
                void copyText(
                  contextMenu.item.kind === "workflow"
                    ? contextMenu.item.workflow.name
                    : contextMenu.item.task.name,
                  "名称",
                )
              }
            >
              <Copy size={14} aria-hidden="true" />
              复制名称
            </DropdownMenuItem>
            <DropdownMenuItem
              onClick={() => void copyText(resourcePath(contextMenu.item), "路径")}
            >
              <Copy size={14} aria-hidden="true" />
              复制路径
            </DropdownMenuItem>

            <DropdownMenuSeparator />

            <DropdownMenuItem onClick={() => openResource(contextMenu.item)}>
              <ExternalLink size={14} aria-hidden="true" />
              在侧边打开
              <span className="ml-auto text-[11px] text-[var(--yak-components-muted-text)]">
                Ctrl+Enter
              </span>
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => void toggleFavorite(contextMenu.item)}>
              <Star
                size={14}
                aria-hidden="true"
                className={isFavorite(contextMenu.item) ? "fill-current text-amber-500" : ""}
              />
              {isFavorite(contextMenu.item) ? "取消收藏" : "收藏"}
            </DropdownMenuItem>

            <DropdownMenuSeparator />

            <DropdownMenuItem
              disabled={contextMenu.item.kind !== "workflow" || workspaces.length < 2}
              title={
                contextMenu.item.kind === "task"
                  ? "暂不支持单节点跨项目移动"
                  : workspaces.length < 2
                    ? "当前没有其他可移动的项目"
                    : undefined
              }
              onClick={() => {
                if (contextMenu.item.kind === "workflow") {
                  openMoveDialog(contextMenu.item);
                }
              }}
            >
              <FolderInput size={14} aria-hidden="true" />
              移动...
            </DropdownMenuItem>
            <DropdownMenuItem
              disabled={contextMenu.item.workflow.releaseState?.toUpperCase() === "ONLINE"}
              title={
                contextMenu.item.workflow.releaseState?.toUpperCase() === "ONLINE"
                  ? "在线工作流需要先下线才能修改"
                  : undefined
              }
              onClick={() => openRenameDialog(contextMenu.item)}
            >
              <Pencil size={14} aria-hidden="true" />
              重命名...
              <span className="ml-auto text-[11px] text-[var(--yak-components-muted-text)]">
                Enter
              </span>
            </DropdownMenuItem>
            <DropdownMenuItem
              tone="danger"
              disabled={contextMenu.item.kind !== "workflow"}
              title={
                contextMenu.item.kind === "task"
                  ? "单节点删除需要先完善依赖校验和连线调整"
                  : undefined
              }
              onClick={() => {
                if (contextMenu.item.kind === "workflow") {
                  openDeleteDialog(contextMenu.item);
                }
              }}
            >
              <Trash2 size={14} aria-hidden="true" />
              删除
              <span className="ml-auto text-[11px] text-[var(--yak-components-muted-text)]">
                Windows+Backspace
              </span>
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      ) : null}

      <Dialog
        open={dialog?.kind === "rename"}
        onOpenChange={(open) => {
          if (!open && !actionPending) setDialog(null);
        }}
      >
        {dialog?.kind === "rename" ? (
          <DialogContent className="w-[420px]">
            <DialogTitle className="text-base font-semibold">
              重命名{dialog.item.kind === "workflow" ? "工作流" : "节点"}
            </DialogTitle>
            <DialogDescription className="mt-2 text-sm text-[var(--yak-components-muted-text)]">
              修改后会同步更新资源目录和已打开的工作区标签。
            </DialogDescription>
            <div className="mt-4">
              <Input
                size="small"
                value={renameValue}
                onChange={(event) => setRenameValue(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === "Enter") {
                    event.preventDefault();
                    void submitDialog();
                  }
                }}
                aria-label="资源名称"
                autoFocus
              />
            </div>
            <div className="mt-6 flex justify-end gap-2">
              <Button
                size="sm"
                variant="secondary"
                isDisabled={actionPending}
                onClick={() => setDialog(null)}
              >
                取消
              </Button>
              <Button
                size="sm"
                isPending={actionPending}
                onClick={() => void submitDialog()}
              >
                保存
              </Button>
            </div>
          </DialogContent>
        ) : null}
      </Dialog>

      <Dialog
        open={dialog?.kind === "move"}
        onOpenChange={(open) => {
          if (!open && !actionPending) setDialog(null);
        }}
      >
        {dialog?.kind === "move" ? (
          <DialogContent className="w-[420px]">
            <DialogTitle className="text-base font-semibold">移动工作流</DialogTitle>
            <DialogDescription className="mt-2 text-sm text-[var(--yak-components-muted-text)]">
              将“{dialog.item.workflow.name}”移动到另一个项目。
            </DialogDescription>
            <div className="mt-4">
              <label className="mb-1.5 block text-xs text-[var(--yak-components-muted-text)]">
                目标项目
              </label>
              <select
                value={targetWorkspaceId}
                onChange={(event) => setTargetWorkspaceId(event.target.value)}
                className="h-9 w-full rounded-md border border-[var(--yak-components-control-border)] bg-[var(--yak-components-input-bg)] px-3 text-sm outline-none focus:border-[var(--yak-color-primary)]"
              >
                <option value="">请选择目标项目</option>
                {workspaces
                  .filter((workspace) => workspace.id !== workspaceId)
                  .map((workspace) => (
                    <option key={workspace.id} value={workspace.id}>
                      {workspace.name}
                    </option>
                  ))}
              </select>
            </div>
            <div className="mt-6 flex justify-end gap-2">
              <Button
                size="sm"
                variant="secondary"
                isDisabled={actionPending}
                onClick={() => setDialog(null)}
              >
                取消
              </Button>
              <Button
                size="sm"
                isPending={actionPending}
                isDisabled={!targetWorkspaceId}
                onClick={() => void submitDialog()}
              >
                移动
              </Button>
            </div>
          </DialogContent>
        ) : null}
      </Dialog>

      <Dialog
        open={dialog?.kind === "delete"}
        onOpenChange={(open) => {
          if (!open && !actionPending) setDialog(null);
        }}
      >
        {dialog?.kind === "delete" ? (
          <DialogContent className="w-[420px]">
            <DialogTitle className="text-base font-semibold">删除工作流</DialogTitle>
            <DialogDescription className="mt-2 text-sm leading-6 text-[var(--yak-components-muted-text)]">
              确定删除“{dialog.item.workflow.name}”吗？删除后无法恢复。
            </DialogDescription>
            <div className="mt-6 flex justify-end gap-2">
              <Button
                size="sm"
                variant="secondary"
                isDisabled={actionPending}
                onClick={() => setDialog(null)}
              >
                取消
              </Button>
              <Button
                size="sm"
                variant="danger"
                isPending={actionPending}
                onClick={() => void submitDialog()}
              >
                删除
              </Button>
            </div>
          </DialogContent>
        ) : null}
      </Dialog>
    </aside>
  );
}

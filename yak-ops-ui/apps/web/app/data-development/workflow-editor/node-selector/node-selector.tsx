import {
  useDeferredValue,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { createPortal } from "react-dom";
import { useQuery } from "@tanstack/react-query";
import {
  ChevronDown,
  ChevronRight,
  Search,
  X,
} from "lucide-react";
import { Input, toast } from "@yak-ops/yak-ui";
import { useWorkspace } from "@/hooks/use-workspace";
import { generateWorkflowTaskCode } from "@/service/workflow/definition";

import {
  listWorkflowTaskTypes,
  type WorkflowTaskTypeRecord,
} from "@/service/workflow/task-type";

import { TaskTypeBadgeIcon } from "../components/task-type-badge-icon";
import { WORKFLOW_TASK_DRAG_TYPE } from "../constants";
import { useWorkflowStore } from "../store/workflow-store";
import type {
  WorkflowNodeInsertionRequest,
  WorkflowSelectorRequest,
} from "../types";

const CATEGORY_LABELS: Record<string, string> = {
  Universal: "Universal",
  universal: "Universal",
  Cloud: "Cloud",
  cloud: "Cloud",
  Logic: "Logic",
  logic: "Logic",
  DataIntegration: "DataIntegration",
  dataIntegration: "DataIntegration",
  MachineLearning: "MachineLearning",
  machineLearning: "MachineLearning",
  Other: "Other",
  other: "Other",
};

function selectorTitle(kind: WorkflowSelectorRequest["kind"]) {
  switch (kind) {
    case "upstream":
      return "新建上游";
    case "downstream":
      return "新建下游";
    default:
      return "新建节点";
  }
}

function TaskTypeItem({
  task,
  onSelect,
  variant,
}: {
  task: WorkflowTaskTypeRecord;
  onSelect: () => void;
  variant: "sidebar" | "popover";
}) {
  return (
    <button
      type="button"
      draggable
      onDragStart={(event) => {
        event.dataTransfer.effectAllowed = "copy";
        event.dataTransfer.setData(
          WORKFLOW_TASK_DRAG_TYPE,
          JSON.stringify({
            taskType: task.taskType,
            taskCategory: task.taskCategory,
          }),
        );
      }}
      onClick={onSelect}
      className={variant === "popover"
        ? "group flex h-8 w-full cursor-grab items-center rounded-md border border-transparent bg-white px-2 text-left text-xs text-[var(--yak-components-panel-text)] transition-colors hover:bg-[#F3F5F9] active:cursor-grabbing"
        : "group flex h-7 w-full cursor-grab items-center rounded-[2px] border border-[#dcdee3] bg-white px-1.5 text-left text-xs text-[var(--yak-components-panel-text)] transition-[border-color,box-shadow] hover:border-[#c4cbd1] hover:shadow-[0_2px_10px_#00000014] active:cursor-grabbing"}
    >
      <span className={variant === "popover"
        ? "mr-2 flex size-5 shrink-0 items-center justify-center"
        : "mr-2 flex size-4 shrink-0 items-center justify-center"}>
        <TaskTypeBadgeIcon taskType={task.taskType} />
      </span>
      <span className="min-w-0 truncate leading-4">
        {task.taskType}
      </span>
    </button>
  );
}

function WorkflowNodePicker({
  mode,
}: {
  mode: "sidebar" | "popover";
}) {
  const isPopover = mode === "popover";
  const request = useWorkflowStore((state) =>
    isPopover ? state.edgeSelectorRequest : state.selectorRequest,
  );
  const closeSelector = useWorkflowStore((state) =>
    isPopover ? state.closeEdgeSelector : state.closeSelector,
  );
  const insertNode = useWorkflowStore(
    (state) => state.insertNodeFromSelector,
  );
  const { currentWorkspace } = useWorkspace();
  const projectCode = currentWorkspace?.id ?? "";

  const [search, setSearch] = useState("");
  const [collapsed, setCollapsed] = useState<Set<string>>(
    new Set(),
  );
  const [portalRoot, setPortalRoot] = useState<HTMLElement | null>(null);
  const panelRef = useRef<HTMLDivElement | null>(null);
  const deferredSearch = useDeferredValue(
    search.trim().toLowerCase(),
  );
  const requestKey = request?.kind === "edge"
    ? "edge:" + request.edgeId
    : request?.kind === "upstream-popover"
      ? "upstream:" + request.nodeId
      : request?.kind ?? "closed";

  useEffect(() => {
    if (!isPopover) return;
    setPortalRoot(document.body);
    return () => setPortalRoot(null);
  }, [isPopover]);

  useEffect(() => {
    if (!isPopover || !request) return;

    const handlePointerDown = (event: PointerEvent) => {
      const target = event.target;
      if (!(target instanceof Element)) return;
      if (
        panelRef.current?.contains(target) ||
        target.closest("[data-workflow-edge-picker-trigger]")
      ) {
        return;
      }
      closeSelector();
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") closeSelector();
    };

    document.addEventListener("pointerdown", handlePointerDown, true);
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("pointerdown", handlePointerDown, true);
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [closeSelector, isPopover, request]);

  useEffect(() => {
    setSearch("");
  }, [requestKey]);

  const query = useQuery({
    queryKey: [
      "data-development",
      "workflow-task-types",
    ],
    queryFn: ({ signal }) =>
      listWorkflowTaskTypes(signal),
  });

  const grouped = useMemo(() => {
    const source = query.data ?? [];
    const filtered = deferredSearch
      ? source.filter((item) =>
          item.taskType
            .toLowerCase()
            .includes(deferredSearch),
        )
      : source;

    const groups = new Map<
      string,
      WorkflowTaskTypeRecord[]
    >();

    for (const item of filtered) {
      const items = groups.get(item.taskCategory) ?? [];
      items.push(item);
      groups.set(item.taskCategory, items);
    }

    return Array.from(groups.entries());
  }, [deferredSearch, query.data]);

  const handleSelectTaskType = async (task: WorkflowTaskTypeRecord) => {
    if (!projectCode) {
      toast.warning("请先选择工作空间");
      return;
    }
    try {
      const taskCode = await generateWorkflowTaskCode();
      const insertionRequest: WorkflowNodeInsertionRequest | undefined =
        isPopover && request
          ? request.kind === "upstream-popover"
            ? { kind: "upstream", nodeId: request.nodeId }
            : request
          : undefined;
      insertNode(
        task.taskType,
        taskCode,
        Number(projectCode),
        insertionRequest,
      );
      closeSelector();
    } catch (error) {
      toast.error("创建节点失败", {
        description: error instanceof Error ? error.message : "请稍后重试。",
      });
    }
  };

  if (!request) return null;

  const taskList = query.isPending ? (
    <div className="px-2 py-5 text-center text-xs text-[var(--yak-components-muted-text)]">
      正在加载节点类型…
    </div>
  ) : query.isError ? (
    <button
      type="button"
      onClick={() => void query.refetch()}
      className="w-full cursor-pointer px-2 py-5 text-center text-xs text-[var(--yak-color-danger)]"
    >
      节点类型加载失败，点击重试
    </button>
  ) : grouped.length === 0 ? (
    <div className="px-2 py-5 text-center text-xs text-[var(--yak-components-muted-text)]">
      没有匹配的节点类型
    </div>
  ) : (
    <div>
      {grouped.map(([category, tasks]) => {
        const isCollapsed =
          collapsed.has(category) &&
          deferredSearch.length === 0;

        return (
          <section
            key={category}
            className={isPopover ? "pt-1" : "pt-2"}
          >
            <button
              type="button"
              aria-expanded={!isCollapsed}
              onClick={() =>
                setCollapsed((current) => {
                  const next = new Set(current);
                  if (next.has(category)) {
                    next.delete(category);
                  } else {
                    next.add(category);
                  }
                  return next;
                })
              }
              className={isPopover
                ? "relative flex h-7 w-full cursor-pointer items-center rounded-md px-5 text-left text-[11px] font-semibold text-[#667085] transition-colors hover:bg-[#F4F6F8]"
                : "relative flex h-7 w-full cursor-pointer items-center bg-[#f7f8fa] pl-5 pr-2 text-left text-xs font-semibold text-[#333] transition-colors hover:bg-[#f1f3f5]"}
            >
              {isCollapsed ? (
                <ChevronRight
                  size={11}
                  strokeWidth={1.8}
                  aria-hidden="true"
                  className="absolute left-1.5 text-[#707780]"
                />
              ) : (
                <ChevronDown
                  size={11}
                  strokeWidth={1.8}
                  aria-hidden="true"
                  className="absolute left-1.5 text-[#707780]"
                />
              )}
              <span className="truncate">
                {CATEGORY_LABELS[category] ?? category}
              </span>
            </button>

            {!isCollapsed ? (
              <div className="pt-1">
                <div className={isPopover ? "grid gap-0.5" : "grid gap-1"}>
                  {tasks.map((task) => (
                    <TaskTypeItem
                      key={task.taskCategory + ":" + task.taskType}
                      task={task}
                      variant={isPopover ? "popover" : "sidebar"}
                      onSelect={() => handleSelectTaskType(task)}
                    />
                  ))}
                </div>
              </div>
            ) : null}
          </section>
        );
      })}
    </div>
  );

  if (isPopover) {
    if (!portalRoot || !("anchor" in request)) return null;

    const panelWidth = 300;
    const panelHeight = Math.max(240, Math.min(520, window.innerHeight - 24));
    const preferredLeft = request.anchor.x + 14;
    const left = preferredLeft + panelWidth <= window.innerWidth - 12
      ? preferredLeft
      : Math.max(12, request.anchor.x - panelWidth - 14);
    const top = Math.max(
      12,
      Math.min(
        request.anchor.y - panelHeight / 2,
        window.innerHeight - panelHeight - 12,
      ),
    );

    return createPortal(
      <div
        ref={panelRef}
        role="dialog"
        aria-label={request.kind === "upstream-popover" ? "新建上游" : "插入节点"}
        className="fixed z-[1200] flex w-[300px] flex-col overflow-hidden rounded-xl border border-[#E5E7EB] bg-white shadow-[0_12px_36px_rgba(15,23,42,0.18)]"
        style={{ left, top, height: panelHeight }}
      >
        <div className="flex h-11 shrink-0 items-center justify-between border-b border-[#EEF0F3] px-3">
          <div className="flex h-full items-center gap-5">
            <span className="relative flex h-full items-center text-xs font-semibold text-[#2563EB] after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:rounded-full after:bg-[#2563EB]">
              节点
            </span>
          </div>
          <button
            type="button"
            aria-label="关闭节点选择器"
            title="关闭"
            onClick={closeSelector}
            className="flex size-7 cursor-pointer items-center justify-center rounded-md text-[#98A2B3] transition-colors hover:bg-[#F2F4F7] hover:text-[#344054]"
          >
            <X size={14} strokeWidth={1.8} />
          </button>
        </div>

        <div className="shrink-0 px-2.5 py-2">
          <div className="relative rounded-lg focus-within:ring-2 focus-within:ring-[#2563EB]/15">
            <Search
              size={14}
              strokeWidth={1.7}
              aria-hidden="true"
              className="pointer-events-none absolute left-2.5 top-1/2 z-10 -translate-y-1/2 text-[#98A2B3]"
            />
            <Input
              size="small"
              variant="outlined"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="搜索节点类型"
              className="h-8 rounded-lg border-[#D6DCE5] bg-[#FAFBFC] pl-8 text-xs"
            />
          </div>
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-2">
          {taskList}
        </div>
      </div>,
      portalRoot,
    );
  }

  return (
    <aside className="flex h-full w-[220px] shrink-0 flex-col overflow-hidden border-r border-[#dcdee3] bg-white">
      <div className="relative shrink-0 border-b border-[#e6e8eb] px-3 pb-1.5 pt-2 text-xs">
        <div className="relative mb-1 flex h-6 items-center pr-12 font-bold leading-6">
          <div className="flex min-w-0 items-center">
            <span className="truncate text-[var(--yak-color-primary)]">
              {selectorTitle(
                request.kind === "edge"
                  ? "add"
                  : request.kind === "upstream-popover"
                    ? "upstream"
                    : request.kind,
              )}
            </span>
          </div>

          <div className="absolute right-0 top-0 flex h-6 items-center">
            <button
              type="button"
              aria-label="关闭节点选择器"
              title="关闭"
              onClick={closeSelector}
              className="ml-1 flex size-6 cursor-pointer items-center justify-center text-[#7f8792] transition-colors hover:text-[#333]"
            >
              <X size={14} strokeWidth={1.6} />
            </button>
          </div>
        </div>

        <div className="relative">
          <Search
            size={13}
            strokeWidth={1.6}
            aria-hidden="true"
            className="pointer-events-none absolute left-2.5 top-1/2 z-10 -translate-y-1/2 text-[#a0a6ad]"
          />
          <Input
            size="small"
            variant="outlined"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="搜索节点类型"
            className="h-7 rounded-[3px] border-[#dcdee3] pl-8 text-xs"
          />
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-3 pb-2">
        {taskList}
      </div>
    </aside>
  );
}

export function WorkflowNodeSelector() {
  return <WorkflowNodePicker mode="sidebar" />;
}

export function WorkflowEdgeNodeSelector() {
  return <WorkflowNodePicker mode="popover" />;
}

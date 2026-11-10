import {
  useDeferredValue,
  useMemo,
  useState,
} from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Boxes,
  ChevronDown,
  ChevronRight,
  Code2,
  Database,
  FileCode2,
  Globe,
  Pin,
  Search,
  Terminal,
  Workflow,
  X,
} from "lucide-react";
import { Input } from "@yak-ops/yak-ui";

import {
  listWorkflowTaskTypes,
  type WorkflowTaskTypeRecord,
} from "@/service/workflow/task-type";

import { useDataDevelopmentWorkbench } from "../../workbench-context";
import { WORKFLOW_TASK_DRAG_TYPE } from "../constants";
import { useWorkflowStore } from "../store/workflow-store";

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

function selectorTitle(
  kind:
    | "add"
    | "upstream"
    | "downstream"
    | "edge",
) {
  switch (kind) {
    case "upstream":
      return "新建上游";
    case "downstream":
      return "新建下游";
    case "edge":
      return "插入节点";
    default:
      return "新建节点";
  }
}

function TaskTypeIcon({ taskType }: { taskType: string }) {
  const type = taskType.toUpperCase();

  let Icon = FileCode2;

  if (
    type === "SHELL" ||
    type === "REMOTESHELL" ||
    type === "HIVECLI"
  ) {
    Icon = Terminal;
  } else if (type === "SQL" || type === "PROCEDURE") {
    Icon = Database;
  } else if (type === "JAVA" || type === "PYTHON") {
    Icon = Code2;
  } else if (
    type === "SPARK" ||
    type === "FLINK" ||
    type === "FLINK_STREAM"
  ) {
    Icon = Workflow;
  } else if (type === "GRPC" || type === "HTTP") {
    Icon = Globe;
  } else if (type === "MR" || type === "DINKY") {
    Icon = Boxes;
  }

  return (
    <Icon
      size={16}
      strokeWidth={1.6}
      aria-hidden="true"
      className="shrink-0 text-[var(--yak-components-muted-text)]"
    />
  );
}

function TaskTypeItem({
  task,
  onSelect,
}: {
  task: WorkflowTaskTypeRecord;
  onSelect: () => void;
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
      className="group flex h-7 w-full cursor-grab items-center rounded-[2px] border border-[#dcdee3] bg-white px-1.5 text-left text-xs text-[var(--yak-components-panel-text)] transition-[border-color,box-shadow] hover:border-[#c4cbd1] hover:shadow-[0_2px_10px_#00000014] active:cursor-grabbing"
    >
      <span className="mr-2 flex size-4 shrink-0 items-center justify-center">
        <TaskTypeIcon taskType={task.taskType} />
      </span>
      <span className="min-w-0 truncate leading-4">
        {task.taskType}
      </span>
    </button>
  );
}

export function WorkflowNodeSelector({
  workflowCode,
}: {
  workflowCode: number;
}) {
  const request = useWorkflowStore(
    (state) => state.selectorRequest,
  );
  const closeSelector = useWorkflowStore(
    (state) => state.closeSelector,
  );
  const insertNode = useWorkflowStore(
    (state) => state.insertNodeFromSelector,
  );
  const { openDraftTask } = useDataDevelopmentWorkbench();

  const [search, setSearch] = useState("");
  const [collapsed, setCollapsed] = useState<Set<string>>(
    new Set(),
  );
  const deferredSearch = useDeferredValue(
    search.trim().toLowerCase(),
  );

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

  const handleSelectTaskType = (task: WorkflowTaskTypeRecord) => {
    const nodeId = insertNode(task.taskType);
    closeSelector();
    openDraftTask(
      workflowCode,
      nodeId,
      task.taskType,
      task.taskType + " 节点",
    );
  };

  if (!request) return null;

  return (
    <aside className="flex h-full w-[220px] shrink-0 flex-col overflow-hidden border-r border-[#dcdee3] bg-white">
      <div className="relative shrink-0 border-b border-[#e6e8eb] px-3 pb-1.5 pt-2 text-xs">
        <div className="relative mb-1 flex h-6 items-center pr-12 font-bold leading-6">
          <div className="flex min-w-0 items-center">
            <span className="truncate text-[var(--yak-color-primary)]">
              {selectorTitle(request.kind)}
            </span>
            <span className="mx-0.5 shrink-0 font-normal text-[#8a9199]">
              |
            </span>
            <span className="shrink-0 font-normal text-[#333]">
              导入节点
            </span>
          </div>

          <div className="absolute right-0 top-0 flex h-6 items-center">
            <button
              type="button"
              aria-label="固定节点选择器"
              title="固定"
              className="flex size-6 cursor-pointer items-center justify-center text-[#7f8792] transition-colors hover:text-[#333]"
            >
              <Pin size={13} strokeWidth={1.6} />
            </button>
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
            onChange={(event) =>
              setSearch(event.target.value)
            }
            placeholder="搜索节点类型"
            className="h-7 rounded-[3px] border-[#dcdee3] pl-8 text-xs"
          />
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-3 pb-2">
        {query.isPending ? (
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
            {grouped.map(([category, tasks], index) => {
              const isCollapsed =
                collapsed.has(category) &&
                deferredSearch.length === 0;

              return (
                <section
                  key={category}
                  className={index === 0 ? "pt-2" : "pt-2"}
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
                    className="relative flex h-7 w-full cursor-pointer items-center bg-[#f7f8fa] pl-5 pr-2 text-left text-xs font-semibold text-[#333] transition-colors hover:bg-[#f1f3f5]"
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
                      {CATEGORY_LABELS[category] ??
                        category}
                    </span>
                  </button>

                  {!isCollapsed ? (
                    <div className="pt-1">
                      <div className="grid gap-1">
                        {tasks.map((task) => (
                          <TaskTypeItem
                            key={
                              task.taskCategory +
                              ":" +
                              task.taskType
                            }
                            task={task}
                            onSelect={() =>
                              handleSelectTaskType(task)
                            }
                          />
                        ))}
                      </div>
                    </div>
                  ) : null}
                </section>
              );
            })}
          </div>
        )}
      </div>
    </aside>
  );
}

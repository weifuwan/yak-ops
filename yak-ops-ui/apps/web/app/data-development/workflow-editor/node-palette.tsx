import { useDeferredValue, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  ChevronDown,
  ChevronRight,
  Search,
  X,
} from "lucide-react";
import { Input } from "@yak-ops/yak-ui";

import {
  listWorkflowTaskTypes,
  type WorkflowTaskTypeRecord,
} from "@/service/workflow/task-type";

export const WORKFLOW_TASK_DRAG_TYPE =
  "application/x-yak-ops-workflow-task-type";

const CATEGORY_LABELS: Record<string, string> = {
  universal: "通用",
  cloud: "云服务",
  logic: "逻辑节点",
  dataIntegration: "数据集成",
  machineLearning: "机器学习",
  other: "其他",
};

const displayCategory = (category: string) =>
  CATEGORY_LABELS[category] ?? category;

function TaskTypeItem({ task }: { task: WorkflowTaskTypeRecord }) {
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
      className="flex h-8 w-full cursor-grab items-center rounded-sm border border-[var(--yak-components-border)] bg-white px-2 text-left text-xs text-[var(--yak-components-panel-text)] hover:border-[var(--yak-color-primary)] hover:bg-[var(--yak-color-hover)] active:cursor-grabbing"
    >
      <span className="truncate">{task.taskType}</span>
    </button>
  );
}

export function WorkflowNodePalette({
  onClose,
}: {
  onClose: () => void;
}) {
  const [search, setSearch] = useState("");
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const deferredSearch = useDeferredValue(search.trim().toLowerCase());

  const query = useQuery({
    queryKey: ["data-development", "workflow-task-types"],
    queryFn: ({ signal }) => listWorkflowTaskTypes(signal),
  });

  const grouped = useMemo(() => {
    const source = query.data ?? [];
    const filtered = deferredSearch
      ? source.filter((item) =>
          item.taskType.toLowerCase().includes(deferredSearch),
        )
      : source;

    const groups = new Map<string, WorkflowTaskTypeRecord[]>();
    for (const item of filtered) {
      const items = groups.get(item.taskCategory) ?? [];
      items.push(item);
      groups.set(item.taskCategory, items);
    }
    return Array.from(groups.entries());
  }, [deferredSearch, query.data]);

  const isExpanded = (category: string) =>
    deferredSearch.length > 0 || expanded.size === 0 || expanded.has(category);

  const toggleCategory = (category: string) => {
    setExpanded((current) => {
      const next = new Set(current);
      if (next.has(category)) next.delete(category);
      else next.add(category);
      return next;
    });
  };

  return (
    <aside className="flex w-[220px] shrink-0 flex-col border-r border-[var(--yak-components-border)] bg-white">
      <div className="flex h-9 shrink-0 items-center justify-between border-b border-[var(--yak-components-border)] px-3">
        <span className="text-xs font-semibold text-[var(--yak-color-primary)]">
          新建节点
        </span>
        <button
          type="button"
          aria-label="关闭节点面板"
          onClick={onClose}
          className="flex size-6 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)]"
        >
          <X size={13} />
        </button>
      </div>

      <div className="p-2">
        <div className="relative">
          <Search
            size={13}
            aria-hidden="true"
            className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--yak-components-muted-text)]"
          />
          <Input
            size="small"
            variant="outlined"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="搜索节点类型"
            className="pl-8"
          />
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-3">
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
          grouped.map(([category, tasks]) => {
            const opened = isExpanded(category);
            return (
              <div key={category} className="mb-2">
                <button
                  type="button"
                  onClick={() => toggleCategory(category)}
                  className="flex h-8 w-full cursor-pointer items-center gap-1 rounded-sm bg-[var(--yak-color-hover)] px-2 text-left text-xs font-medium"
                >
                  {opened ? (
                    <ChevronDown size={13} />
                  ) : (
                    <ChevronRight size={13} />
                  )}
                  <span>{displayCategory(category)}</span>
                </button>
                {opened ? (
                  <div className="grid gap-1 pt-1">
                    {tasks.map((task) => (
                      <TaskTypeItem
                        key={`${task.taskCategory}:${task.taskType}`}
                        task={task}
                      />
                    ))}
                  </div>
                ) : null}
              </div>
            );
          })
        )}
      </div>
    </aside>
  );
}

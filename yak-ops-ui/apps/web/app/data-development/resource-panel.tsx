import { useDeferredValue, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  ChevronDown,
  ChevronRight,
  RefreshCw,
  Search,
  Workflow,
  X,
} from "lucide-react";
import { Button, Input } from "@yak-ops/yak-ui";

import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import {
  listWorkflowDefinitions,
  listWorkflowTasks,
  type WorkflowDefinitionRecord,
  type WorkflowTaskRecord,
} from "@/service/workflow/definition";

import { useDataDevelopmentWorkbench } from "./workbench-context";

const PAGE_SIZE = 200;
const workflowExplorerKey = ["data-development", "workflow-explorer"] as const;

type WorkflowScope = "ALL" | "MINE";

function WorkflowTreeItem({
  workflow,
  workspaceId,
}: {
  workflow: WorkflowDefinitionRecord;
  workspaceId: string;
}) {
  const { activeTab, openWorkflow } = useDataDevelopmentWorkbench();
  const [expanded, setExpanded] = useState(false);

  const tasksQuery = useQuery({
    queryKey: [...workflowExplorerKey, workspaceId, "tasks", workflow.code],
    queryFn: ({ signal }) => listWorkflowTasks(workflow.code, signal),
    enabled: expanded,
  });

  const selected = activeTab?.workflowCode === workflow.code;

  const openTask = (task: WorkflowTaskRecord) => {
    openWorkflow(workflow, task);
  };

  return (
    <div>
      <div
        className={[
          "group flex h-8 items-center rounded-sm transition-colors",
          selected
            ? "bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]"
            : "hover:bg-[var(--yak-color-hover)]",
        ].join(" ")}
      >
        <button
          type="button"
          aria-label={expanded ? "折叠工作流" : "展开工作流"}
          aria-expanded={expanded}
          onClick={() => setExpanded((value) => !value)}
          className="flex size-7 shrink-0 cursor-pointer items-center justify-center"
        >
          {expanded ? (
            <ChevronDown size={13} aria-hidden="true" />
          ) : (
            <ChevronRight size={13} aria-hidden="true" />
          )}
        </button>
        <button
          type="button"
          onClick={() => openWorkflow(workflow)}
          className="flex min-w-0 flex-1 cursor-pointer items-center gap-2 py-1 pr-2 text-left text-[13px]"
        >
          <Workflow size={14} strokeWidth={1.6} aria-hidden="true" />
          <span className="truncate">{workflow.name}</span>
        </button>
      </div>

      {expanded ? (
        <div className="pb-1 pl-7">
          {tasksQuery.isPending ? (
            <div className="px-3 py-2 text-xs text-[var(--yak-components-muted-text)]">
              正在加载节点…
            </div>
          ) : tasksQuery.isError ? (
            <button
              type="button"
              onClick={() => void tasksQuery.refetch()}
              className="cursor-pointer px-3 py-2 text-left text-xs text-[var(--yak-color-danger)]"
            >
              节点加载失败，点击重试
            </button>
          ) : (tasksQuery.data?.length ?? 0) === 0 ? (
            <div className="px-3 py-2 text-xs text-[var(--yak-components-muted-text)]">
              暂无节点
            </div>
          ) : (
            tasksQuery.data?.map((task) => {
              const taskSelected =
                selected && activeTab?.selectedTask?.code === task.code;
              return (
                <button
                  key={task.code}
                  type="button"
                  onClick={() => openTask(task)}
                  className={[
                    "flex h-7 w-full cursor-pointer items-center gap-2 rounded-sm px-3 text-left text-xs transition-colors",
                    taskSelected
                      ? "bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]"
                      : "hover:bg-[var(--yak-color-hover)]",
                  ].join(" ")}
                >
                  <span className="min-w-0 flex-1 truncate">{task.name}</span>
                  <span className="shrink-0 text-[10px] text-[var(--yak-components-muted-text)]">
                    {task.taskType}
                  </span>
                </button>
              );
            })
          )}
        </div>
      ) : null}
    </div>
  );
}

export function DataDevelopmentResourcePanel({ width }: { width: number }) {
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState("");
  const [scope, setScope] = useState<WorkflowScope>("ALL");
  const deferredSearch = useDeferredValue(search.trim());
  const workspaceId = currentWorkspace?.id ?? "";
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

  const refresh = async () => {
    await queryClient.invalidateQueries({
      queryKey: [...workflowExplorerKey, workspaceId],
    });
  };

  return (
    <aside
      aria-label="工作流开发"
      className="flex shrink-0 flex-col bg-[var(--yak-components-panel-bg)]"
      style={{ width }}
    >
      <div className="flex h-10 shrink-0 items-center justify-between border-b border-[var(--yak-components-border)] px-3">
        <span className="text-sm font-semibold">工作流开发</span>
        <button
          type="button"
          aria-label="刷新工作流"
          title="刷新"
          onClick={() => void refresh()}
          className="flex size-7 cursor-pointer items-center justify-center rounded text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)] hover:text-[var(--yak-components-panel-text)]"
        >
          <RefreshCw
            size={14}
            className={workflowsQuery.isFetching ? "animate-spin" : ""}
          />
        </button>
      </div>

      <div className="px-3 pb-2 pt-3">
        <div className="relative">
          <Search
            size={14}
            aria-hidden="true"
            className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--yak-components-muted-text)]"
          />
          <Input
            size="small"
            variant="outlined"
            aria-label="搜索工作流"
            placeholder="搜索工作流"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            className="pl-8"
          />
          {search ? (
            <button
              type="button"
              aria-label="清空搜索"
              onClick={() => setSearch("")}
              className="absolute right-2 top-1/2 -translate-y-1/2 cursor-pointer rounded p-0.5 text-[var(--yak-components-muted-text)] hover:bg-[var(--yak-color-hover)]"
            >
              <X size={13} />
            </button>
          ) : null}
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

      <div className="min-h-0 flex-1 overflow-y-auto px-2 pb-3">
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
          <>
            {workflows.map((workflow) => (
              <WorkflowTreeItem
                key={workflow.code}
                workflow={workflow}
                workspaceId={workspaceId}
              />
            ))}
            {(workflowsQuery.data?.total ?? 0) > workflows.length ? (
              <div className="px-3 py-3 text-center text-[11px] text-[var(--yak-components-muted-text)]">
                当前显示前 {workflows.length} 个工作流
              </div>
            ) : null}
          </>
        )}
      </div>
    </aside>
  );
}

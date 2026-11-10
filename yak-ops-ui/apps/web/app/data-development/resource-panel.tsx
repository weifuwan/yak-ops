import { useDeferredValue, useEffect, useMemo, useState } from "react";
import { useQueries, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Code2,
  Database,
  FileCode2,
  RefreshCw,
  Search,
  Terminal,
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

import {
  VirtualTree,
  type VirtualTreeNode,
} from "./virtual-tree";
import { useDataDevelopmentWorkbench } from "./workbench-context";

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

function TaskTypeIcon({ taskType }: { taskType: string }) {
  const type = taskType.toUpperCase();

  if (type === "SQL" || type === "DATA_SYNC") {
    return (
      <Database
        size={13}
        strokeWidth={1.6}
        className="shrink-0 text-[var(--yak-components-muted-text)]"
      />
    );
  }

  if (
    type === "SHELL" ||
    type === "REMOTESHELL" ||
    type === "HIVECLI"
  ) {
    return (
      <Terminal
        size={13}
        strokeWidth={1.6}
        className="shrink-0 text-[var(--yak-components-muted-text)]"
      />
    );
  }

  if (
    type === "JAVA" ||
    type === "PYTHON" ||
    type === "PROCEDURE" ||
    type === "SPARK" ||
    type === "FLINK" ||
    type === "FLINK_STREAM"
  ) {
    return (
      <Code2
        size={13}
        strokeWidth={1.6}
        className="shrink-0 text-[var(--yak-components-muted-text)]"
      />
    );
  }

  return (
    <FileCode2
      size={13}
      strokeWidth={1.6}
      className="shrink-0 text-[var(--yak-components-muted-text)]"
    />
  );
}

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
        <Workflow size={14} strokeWidth={1.6} className="shrink-0" />
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
      <TaskTypeIcon taskType={item.task.taskType} />
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
  const { currentWorkspace } = useWorkspace();
  const { activeTab, openTask, openWorkflow } = useDataDevelopmentWorkbench();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState("");
  const [scope, setScope] = useState<WorkflowScope>("ALL");
  const [expandedCodes, setExpandedCodes] = useState<Set<number>>(
    () => new Set(),
  );
  const deferredSearch = useDeferredValue(search.trim());
  const workspaceId = currentWorkspace?.id ?? "";

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

      <div className="min-h-0 flex-1 px-2 pb-2">
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
            renderItem={(item) => <WorkflowTreeContent item={item} />}
          />
        )}
      </div>

      {(workflowsQuery.data?.total ?? 0) > workflows.length ? (
        <div className="shrink-0 border-t border-[var(--yak-components-border)] px-3 py-2 text-center text-[11px] text-[var(--yak-components-muted-text)]">
          当前显示前 {workflows.length} 个工作流
        </div>
      ) : null}
    </aside>
  );
}

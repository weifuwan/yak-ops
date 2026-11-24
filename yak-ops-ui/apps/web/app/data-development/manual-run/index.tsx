import { useMemo, useState } from "react";
import { Play, Search, Workflow } from "lucide-react";
import { PanelHeader } from "@yak-ops/yak-ui";
import { useQuery } from "@tanstack/react-query";

import { listWorkflowDefinitions } from "@/service/workflow/definition";
import type { WorkflowDefinitionRecord } from "@/service/workflow/definition";
import { useDataDevelopmentWorkbench } from "../workbench-context";
import { WorkflowRunDialog } from "./workflow-run-dialog";

type WorkflowGroupId = "today" | "yesterday" | "week" | "older";

type WorkflowGroup = {
  id: WorkflowGroupId;
  label: string;
  workflows: WorkflowDefinitionRecord[];
};

function startOfDay(value: Date) {
  return new Date(value.getFullYear(), value.getMonth(), value.getDate()).getTime();
}

function workflowGroupId(value?: string | null): WorkflowGroupId {
  if (!value) return "older";
  const normalized = value.includes("T") ? value : value.replace(" ", "T");
  const date = new Date(normalized);
  if (Number.isNaN(date.getTime())) return "older";

  const days = Math.floor(
    (startOfDay(new Date()) - startOfDay(date)) / 86_400_000,
  );
  if (days <= 0) return "today";
  if (days === 1) return "yesterday";
  if (days <= 7) return "week";
  return "older";
}

export function ManualRunPanel({ width }: { width: number }) {
  const [keyword, setKeyword] = useState("");
  const [selected, setSelected] = useState<WorkflowDefinitionRecord | null>(null);
  const { openWorkflow } = useDataDevelopmentWorkbench();

  const query = useQuery({
    queryKey: ["data-development", "manual-run", keyword],
    queryFn: ({ signal }) =>
      listWorkflowDefinitions(
        { pageNo: 1, pageSize: 100, searchVal: keyword.trim() || undefined },
        signal,
      ),
  });

  const workflows = query.data?.totalList ?? [];
  const groups = useMemo<WorkflowGroup[]>(() => {
    const definitions: WorkflowGroup[] = [
      { id: "today", label: "今天", workflows: [] },
      { id: "yesterday", label: "昨天", workflows: [] },
      { id: "week", label: "最近 7 天", workflows: [] },
      { id: "older", label: "更早", workflows: [] },
    ];
    const groupMap = new Map(definitions.map((group) => [group.id, group]));

    workflows.forEach((workflow) => {
      const group = groupMap.get(
        workflowGroupId(workflow.updateTime ?? workflow.createTime),
      );
      group?.workflows.push(workflow);
    });

    return definitions.filter((group) => group.workflows.length > 0);
  }, [workflows]);

  return (
    <aside
      aria-label="手动运行"
      className="flex h-full shrink-0 flex-col bg-[var(--yak-components-panel-bg)]"
      style={{ width }}
    >
      <PanelHeader title="手动运行" />
      <div className="border-b border-[#ececec] px-3 py-2">
        <div className="flex h-8 items-center gap-2 rounded-[4px] border border-[#dcdfe5] bg-white px-2 text-[#8a8f98]">
          <Search size={14} />
          <input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索工作流"
            className="min-w-0 flex-1 bg-transparent text-[12px] text-[#30343a] outline-none placeholder:text-[#a5aab2]"
          />
        </div>
      </div>

      <div
        className="mt-3 min-h-0 flex-1 overflow-y-auto px-2 pb-3 [scrollbar-width:thin]"
        style={{ scrollbarColor: "var(--yak-color-scrollbar) transparent" }}
      >
        <section aria-label="工作流列表">
          {query.isLoading ? (
            <div className="flex h-24 items-center justify-center text-[12px] text-[#9aa0a8]">
              加载中...
            </div>
          ) : groups.length === 0 ? (
            <p className="m-0 px-2 py-2 text-[11px] leading-5 text-[#999999]">
              暂无可运行工作流
            </p>
          ) : (
            <div className="flex flex-col gap-3">
              {groups.map((group) => (
                <section key={group.id} aria-label={group.label}>
                  <h3 className="mb-1 mt-0 px-2 text-xs font-medium tracking-[0.02em] text-[#999999]">
                    {group.label}
                  </h3>
                  <nav className="flex flex-col gap-0.5" aria-label={group.label}>
                    {group.workflows.map((workflow) => (
                      <div key={workflow.code} className="group relative">
                        <button
                          type="button"
                          title={workflow.name}
                          className="flex h-9 w-full cursor-pointer items-center gap-2 rounded-md px-2 pr-8 text-left text-[#666666] transition-colors hover:bg-[#e5e5e5] hover:text-[#20201e] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-[#888888]/30"
                          onClick={() => openWorkflow(workflow)}
                        >
                          <Workflow
                            size={13}
                            strokeWidth={1.7}
                            className="shrink-0 text-[#999999]"
                          />
                          <span className="min-w-0 flex-1 truncate text-xs leading-5">
                            {workflow.name}
                          </span>
                        </button>

                        <button
                          type="button"
                          className="absolute right-1 top-1.5 flex size-6 cursor-pointer items-center justify-center rounded-md text-[#4d6bfe] opacity-0 outline-none transition-[background-color,opacity] hover:bg-[#e5e5e5] hover:text-[#20201e] focus-visible:opacity-100 focus-visible:ring-2 focus-visible:ring-[#888888]/30 group-hover:opacity-100 group-focus-within:opacity-100"
                          title="运行"
                          aria-label={"运行 " + workflow.name}
                          onClick={() => setSelected(workflow)}
                        >
                          <Play size={13} fill="currentColor" />
                        </button>
                      </div>
                    ))}
                  </nav>
                </section>
              ))}
            </div>
          )}
        </section>
      </div>

      <WorkflowRunDialog
        workflow={selected}
        open={Boolean(selected)}
        onOpenChange={(open) => {
          if (!open) setSelected(null);
        }}
      />
    </aside>
  );
}

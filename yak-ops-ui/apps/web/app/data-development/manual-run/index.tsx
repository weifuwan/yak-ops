import { useState } from "react";
import { Play, Search } from "lucide-react";
import { PanelHeader } from "@yak-ops/yak-ui";
import { useQuery } from "@tanstack/react-query";

import { listWorkflowDefinitions } from "@/service/workflow/definition";
import type { WorkflowDefinitionRecord } from "@/service/workflow/definition";
import { useDataDevelopmentWorkbench } from "../workbench-context";
import { WorkflowRunDialog } from "./workflow-run-dialog";

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

      <div className="min-h-0 flex-1 overflow-y-auto py-1">
        {query.isLoading ? (
          <div className="flex h-24 items-center justify-center text-[12px] text-[#9aa0a8]">
            加载中...
          </div>
        ) : workflows.length === 0 ? (
          <div className="px-4 py-10 text-center text-[12px] text-[#9aa0a8]">
            暂无可运行工作流
          </div>
        ) : (
          workflows.map((workflow) => (
            <div
              key={workflow.code}
              className="group flex min-h-10 items-center gap-2 px-3 hover:bg-[#f6f8fb]"
            >
              <button
                type="button"
                className="min-w-0 flex-1 cursor-pointer truncate py-2 text-left text-[12px] text-[#30343a]"
                onClick={() => openWorkflow(workflow)}
                title={workflow.name}
              >
                {workflow.name}
              </button>
              <button
                type="button"
                className="flex size-7 shrink-0 cursor-pointer items-center justify-center rounded-[4px] text-[#4d6bfe] opacity-0 transition-opacity hover:bg-[#edf2ff] group-hover:opacity-100"
                title="运行"
                aria-label={"运行 " + workflow.name}
                onClick={() => setSelected(workflow)}
              >
                <Play size={13} fill="currentColor" />
              </button>
            </div>
          ))
        )}
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

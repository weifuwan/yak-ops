import { useState } from "react";
import { GitBranch, Search, Workflow } from "lucide-react";
import { Input, PanelHeader } from "@yak-ops/yak-ui";
import { useQuery } from "@tanstack/react-query";

import {
  listWorkflowDefinitions,
  type WorkflowDefinitionRecord,
} from "@/service/workflow/definition";
import { useDataDevelopmentWorkbench } from "../workbench-context";

export function WorkflowLineagePanel({ width }: { width: number }) {
  const [keyword, setKeyword] = useState("");
  const { activeTabId, openWorkflowLineage } = useDataDevelopmentWorkbench();

  const query = useQuery({
    queryKey: ["data-development", "workflow-lineage-panel", keyword],
    queryFn: ({ signal }) =>
      listWorkflowDefinitions(
        { pageNo: 1, pageSize: 100, searchVal: keyword.trim() || undefined },
        signal,
      ),
  });

  const workflows = query.data?.totalList ?? [];

  return (
    <aside
      aria-label="依赖分析"
      className="flex h-full shrink-0 flex-col bg-[var(--yak-components-panel-bg)]"
      style={{ width }}
    >
      <PanelHeader title="依赖分析" />
      <div className="border-b border-[#ececec] px-3 py-2">
        <div className="relative">
          <Search
            size={14}
            className="pointer-events-none absolute left-2.5 top-1/2 z-10 -translate-y-1/2 text-[var(--yak-components-input-icon)]"
          />
          <Input
            size="small"
            variant="outlined"
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索工作流"
            className="h-7 pl-8"
          />
        </div>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto px-2 py-2">
        {query.isLoading ? (
          <div className="flex h-24 items-center justify-center text-xs text-[#9aa0a8]">
            加载中...
          </div>
        ) : workflows.length === 0 ? (
          <div className="px-3 py-10 text-center text-xs text-[#9aa0a8]">
            暂无工作流
          </div>
        ) : (
          workflows.map((workflow: WorkflowDefinitionRecord) => {
            const active = activeTabId === "workflow-lineage:" + workflow.code;
            return (
              <button
                key={workflow.code}
                type="button"
                onClick={() => openWorkflowLineage(workflow)}
                className={[
                  "mb-0.5 flex w-full cursor-pointer items-center gap-2 rounded-md px-2 py-2 text-left transition-colors",
                  active
                    ? "bg-[#dfe7ff] text-[#20201e]"
                    : "text-[#666] hover:bg-[#e9e9e9] hover:text-[#20201e]",
                ].join(" ")}
              >
                <Workflow size={14} className="shrink-0 text-[#8a9099]" />
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-xs font-medium">{workflow.name}</span>
                  <span className="mt-0.5 block text-[10px] text-[#9aa0a8]">
                    #{workflow.code}
                  </span>
                </span>
                <GitBranch size={13} className="shrink-0 text-[#7f8ba1]" />
              </button>
            );
          })
        )}
      </div>
    </aside>
  );
}

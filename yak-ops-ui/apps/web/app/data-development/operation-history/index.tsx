import { useState } from "react";
import { ClipboardList, Search } from "lucide-react";
import { PanelHeader } from "@yak-ops/yak-ui";
import { useQuery } from "@tanstack/react-query";

import { listAuditLogs } from "@/service/audit";
import type { AuditRecord } from "@/service/audit";
import { useDataDevelopmentWorkbench } from "../workbench-context";

export function OperationHistoryPanel({ width }: { width: number }) {
  const [keyword, setKeyword] = useState("");
  const { openAuditRecord } = useDataDevelopmentWorkbench();

  const query = useQuery({
    queryKey: ["data-development", "operation-history", keyword],
    queryFn: ({ signal }) =>
      listAuditLogs(
        { pageNo: 1, pageSize: 100, modelName: keyword.trim() || undefined },
        signal,
      ),
  });

  const records: AuditRecord[] = query.data?.totalList ?? [];

  return (
    <aside
      aria-label="操作历史"
      className="flex h-full shrink-0 flex-col bg-[var(--yak-components-panel-bg)]"
      style={{ width }}
    >
      <PanelHeader title="操作历史" />
      <div className="border-b border-[#ececec] px-3 py-2">
        <div className="flex h-8 items-center gap-2 rounded-[4px] border border-[#dcdfe5] bg-white px-2 text-[#8a8f98]">
          <Search size={14} />
          <input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索对象"
            className="min-w-0 flex-1 bg-transparent text-[12px] text-[#30343a] outline-none placeholder:text-[#a5aab2]"
          />
        </div>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto">
        {query.isLoading ? (
          <div className="flex h-24 items-center justify-center text-[12px] text-[#9aa0a8]">加载中...</div>
        ) : records.length === 0 ? (
          <div className="px-4 py-10 text-center text-[12px] text-[#9aa0a8]">暂无操作记录</div>
        ) : (
          records.map((record, index) => (
            <button
              key={(record.createTime || "") + "-" + index}
              type="button"
              onClick={() => openAuditRecord(record, record.modelName || "操作记录")}
              className="flex w-full cursor-pointer items-start gap-2 border-b border-[#f0f1f3] px-3 py-2.5 text-left hover:bg-[#f7f8fa]"
            >
              <ClipboardList size={14} className="mt-0.5 shrink-0 text-[#7a8290]" />
              <span className="min-w-0 flex-1">
                <span className="block truncate text-[12px] font-medium text-[#30343a]">
                  {record.operation || "操作"}
                </span>
                <span className="mt-0.5 block truncate text-[11px] text-[#8a909a]">
                  {record.modelName || "-"} · {record.userName || "-"}
                </span>
              </span>
            </button>
          ))
        )}
      </div>
    </aside>
  );
}

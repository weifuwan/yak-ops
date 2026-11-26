import { useQuery } from "@tanstack/react-query";
import { Button } from "@yak-ops/yak-ui";
import { History, RefreshCw, X } from "lucide-react";

import { dimensionModelingApi, type DorisPublishRecord } from "@/service/data-modeling/models/dimension-modeling";

type Props = {
  projectCode: string;
  tableId: string;
  onClose: () => void;
};

function statusLabel(status?: string) {
  if (status === "SUCCESS") return "发布成功";
  if (status === "FAILED") return "DDL 执行失败";
  if (status === "VERIFY_FAILED") return "结构校验异常";
  if (status === "RUNNING") return "发布中";
  if (status === "PENDING") return "等待执行";
  if (status === "CONFLICT") return "目标表已存在";
  return status || "未知状态";
}

function statusClassName(status?: string) {
  if (status === "SUCCESS") return "text-[var(--yak-color-success)]";
  if (status === "FAILED" || status === "CONFLICT") return "text-[var(--yak-components-danger-text)]";
  if (status === "VERIFY_FAILED") return "text-amber-700";
  if (status === "RUNNING" || status === "PENDING") return "text-[var(--yak-color-primary)]";
  return "text-[var(--yak-components-muted-text)]";
}

export function DorisPublishHistoryPanel({ projectCode, tableId, onClose }: Props) {
  const historyQuery = useQuery({
    queryKey: ["modeling", "doris-publish-history", projectCode, tableId],
    queryFn: () => dimensionModelingApi.dorisPublishHistory(projectCode, tableId),
    enabled: Boolean(projectCode && tableId),
  });
  const records = historyQuery.data || [];

  return (
    <section className="flex h-full min-h-0 w-[340px] shrink-0 flex-col border-l border-[var(--yak-components-panel-border)] bg-white">
      <header className="flex h-11 shrink-0 items-center justify-between border-b border-[var(--yak-components-panel-border)] px-3">
        <div className="flex items-center gap-2 text-sm font-semibold">
          <History size={14} />
          发布记录
        </div>
        <div className="flex items-center gap-1">
          <Button size="sm" variant="ghost" className="!size-7 !p-0" isDisabled={historyQuery.isFetching} onClick={() => void historyQuery.refetch()}>
            <RefreshCw size={13} />
          </Button>
          <Button size="sm" variant="ghost" className="!size-7 !p-0" onClick={onClose}>
            <X size={14} />
          </Button>
        </div>
      </header>
      <div className="min-h-0 flex-1 space-y-2 overflow-y-auto p-3">
        {historyQuery.isPending ? (
          <p className="py-8 text-center text-xs text-[var(--yak-components-muted-text)]">正在加载发布记录...</p>
        ) : historyQuery.isError ? (
          <div className="py-8 text-center">
            <p className="text-xs text-[var(--yak-components-danger-text)]">发布记录加载失败</p>
            <Button size="sm" variant="secondary" className="mt-2" onClick={() => void historyQuery.refetch()}>重新加载</Button>
          </div>
        ) : records.length ? (
          records.map((item: DorisPublishRecord, index: number) => (
            <article key={item.id || item.ddlHash || index} className="rounded-md border border-[var(--yak-components-panel-border)] p-3">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <div className="truncate text-xs font-medium text-[var(--yak-components-panel-text)]">
                    {(item.targetDatabase || "-") + "." + (item.targetTable || "-")}
                  </div>
                  <div className="mt-1 text-[11px] text-[var(--yak-components-muted-text)]">
                    {item.datasourceName || "Doris 数据源"} · V{item.versionNumber ?? "-"}
                  </div>
                  <div className="mt-1 text-[11px] text-[var(--yak-components-muted-text)]">
                    {item.tableModel === "UNIQUE" ? "主键表（UNIQUE KEY）" : item.tableModel === "DUPLICATE" ? "明细表（DUPLICATE KEY）" : "历史发布"}
                    {" · "}
                    {item.publishMode === "DROP_RECREATE" ? "删除重建" : item.publishMode === "INCREMENTAL" ? "增量发布" : "旧版记录"}
                  </div>
                  {item.publishRemark ? (
                    <p className="mb-0 mt-1 line-clamp-2 break-words text-[11px] text-[var(--yak-components-muted-text)]">
                      {item.publishRemark}
                    </p>
                  ) : null}
                </div>
                <span className={"shrink-0 text-xs font-medium " + statusClassName(item.status)}>
                  {statusLabel(item.status)}
                </span>
              </div>
              <div className="mt-2 flex items-center justify-between gap-2 text-[11px] text-[var(--yak-components-muted-text)]">
                <span>{item.createTime || item.startedAt || "-"}</span>
                {item.executionDurationMs != null ? <span>{item.executionDurationMs} ms</span> : null}
              </div>
              {item.status === "VERIFY_FAILED" ? (
                <p className="mb-0 mt-2 break-words text-xs text-amber-700">
                  DDL 已执行，物理结构校验未通过。请先检查实际结构，不要直接重复删除重建。
                </p>
              ) : null}
              {item.errorMessage ? (
                <p className={"mb-0 mt-2 break-words text-xs " + (item.status === "VERIFY_FAILED" ? "text-amber-700" : "text-[var(--yak-components-danger-text)]")}>{item.errorMessage}</p>
              ) : null}
              {item.physicalSchema ? (
                <details className="mt-2 rounded-md bg-[var(--yak-components-muted-bg)] px-2 py-1.5">
                  <summary className="cursor-pointer text-[11px] text-[var(--yak-components-muted-text)]">
                    查看实际物理结构
                  </summary>
                  <pre className="mb-0 mt-2 max-h-48 overflow-auto whitespace-pre-wrap break-words text-[10px] leading-4 text-[var(--yak-components-panel-text)]">
                    {item.physicalSchema}
                  </pre>
                </details>
              ) : null}
            </article>
          ))
        ) : (
          <div className="py-10 text-center text-xs text-[var(--yak-components-muted-text)]">暂无发布记录</div>
        )}
      </div>
    </section>
  );
}

import {
  Button,
  Select,
  SelectContent,
  SelectItem,
  SelectItemText,
  SelectItemIndicator,
  SelectTrigger,
  SelectValue,
  Spinner,
} from "@yak-ops/yak-ui";
import { RefreshCw } from "lucide-react";
import {
  ExecutionStatusIcon,
  dataSyncTriggerText,
  dataSyncDurationText,
} from "../execution/execution-detail";
import type { DataSyncInstanceSummary, DataSyncInstanceStatus } from "@/service/data-sync";

const PAGE_SIZE = 20;

export const INSTANCE_STATUS_ITEMS: Record<DataSyncInstanceStatus | "ALL", string> = {
  ALL: "全部状态",
  SUBMITTED: "等待",
  LAUNCHING: "启动中",
  RUNNING: "运行中",
  CANCELLING: "正在停止",
  RECONCILING: "待确认",
  FINISHED: "成功",
  FAILED: "失败",
  CANCELED: "已停止",
};

interface Props {
  realtime: boolean;
  status: DataSyncInstanceStatus | "ALL";
  onStatusChange: (value: DataSyncInstanceStatus | "ALL") => void;
  instances: DataSyncInstanceSummary[];
  instancesLoading: boolean;
  listError: boolean;
  selectedId: string | null;
  onSelect: (id: string) => void;
  total: number;
  pageNo: number;
  onPageChange: (page: number) => void;
  onRefresh: () => void;
}

export function ExecutionSidebar({
  realtime,
  status,
  onStatusChange,
  instances,
  instancesLoading,
  listError,
  selectedId,
  onSelect,
  total,
  pageNo,
  onPageChange,
  onRefresh,
}: Props) {
  const statusItems = { ...INSTANCE_STATUS_ITEMS, CANCELED: realtime ? "已停止" : "已取消" };
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const executionId = selectedId;
  const selectInstance = onSelect;
  const changePage = onPageChange;
  const changeStatus = onStatusChange;
  const loadInstances = onRefresh;
  return (
    <aside className="flex max-h-64 w-full shrink-0 flex-col overflow-hidden border-b border-[var(--yak-components-panel-border)] bg-white md:max-h-none md:w-[300px] md:border-r md:border-b-0">
      <div className="shrink-0 border-b border-[var(--yak-components-control-border)] px-4 py-3">
        <div className="flex items-center justify-between">
          <span className="text-sm font-semibold text-[var(--yak-components-panel-text)]">
            执行记录
          </span>
          <Button
            size="small"
            variant="ghost"
            className="sync-execution-icon-button h-7 w-7"
            aria-label="刷新执行记录"
            title="刷新"
            disabled={instancesLoading}
            onClick={() => void loadInstances()}
          >
            <RefreshCw size={14} />
          </Button>
        </div>
        <Select
          size="small"
          items={statusItems}
          value={status}
          onValueChange={(value) =>
            changeStatus(String(value || "ALL") as DataSyncInstanceStatus | "ALL")
          }
        >
          <SelectTrigger variant="outlined" className="mt-3">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {Object.entries(statusItems).map(([value, label]) => (
              <SelectItem key={value} value={value}>
                <SelectItemText>{label}</SelectItemText>
                <SelectItemIndicator />
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto p-2">
        {listError ? (
          <div className="flex min-h-32 flex-col items-center justify-center gap-2 text-sm">
            执行记录加载失败
            <Button size="small" onClick={onRefresh}>
              重试
            </Button>
          </div>
        ) : instancesLoading && instances.length === 0 ? (
          <div className="flex min-h-32 items-center justify-center">
            <Spinner size="medium" label="加载执行记录" />
          </div>
        ) : instances.length === 0 ? (
          <div className="flex min-h-32 items-center justify-center text-sm text-[var(--yak-components-muted-text)]">
            暂无执行记录
          </div>
        ) : (
          <div className="space-y-1">
            {instances.map((instance) => {
              const isSelected = executionId === instance.id;
              return (
                <button
                  key={instance.id}
                  type="button"
                  aria-current={isSelected ? "true" : undefined}
                  className={
                    isSelected
                      ? "w-full cursor-pointer rounded-md bg-[var(--yak-color-primary-subtle)] px-3 py-2.5 text-left"
                      : "w-full cursor-pointer rounded-md bg-white px-3 py-2.5 text-left hover:bg-[var(--yak-color-hover)]"
                  }
                  onClick={() => selectInstance(instance.id)}
                >
                  <div className="flex items-center justify-between gap-2">
                    <ExecutionStatusIcon
                      status={instance.status}
                      realtime={instance.syncType === "REALTIME"}
                    />
                    <span className="truncate text-xs text-[var(--yak-components-muted-text)]">
                      {instance.startTime || instance.createTime || "-"}
                    </span>
                  </div>
                  <div className="mt-2 truncate text-xs text-[var(--yak-components-collapse-section-icon)]">
                    {dataSyncTriggerText(instance.triggerType)} · v{instance.taskVersion} ·{" "}
                    {dataSyncDurationText(instance)}
                  </div>
                </button>
              );
            })}
          </div>
        )}
      </div>

      {total > PAGE_SIZE ? (
        <div className="flex shrink-0 items-center justify-between border-t border-[var(--yak-components-control-border)] px-3 py-2">
          <Button
            size="small"
            variant="ghost"
            disabled={pageNo <= 1 || instancesLoading}
            onClick={() => changePage(pageNo - 1)}
          >
            上一页
          </Button>
          <span className="text-xs text-[var(--yak-components-muted-text)]">
            {pageNo} / {totalPages}
          </span>
          <Button
            size="small"
            variant="ghost"
            disabled={pageNo >= totalPages || instancesLoading}
            onClick={() => changePage(pageNo + 1)}
          >
            下一页
          </Button>
        </div>
      ) : null}
    </aside>
  );
}

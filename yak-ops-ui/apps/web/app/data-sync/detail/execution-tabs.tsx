import { Button, Spinner, Switch, Tabs, TabsList, TabsPanel, TabsTab } from "@yak-ops/yak-ui";
import { RefreshCw } from "lucide-react";
import { canCancelTaskInstance } from "@/service/data-sync/execution";
import type { DataSyncInstanceRecord } from "@/service/data-sync";
import {
  DataSyncExecutionDetailContent,
  isActiveDataSyncInstance,
} from "../execution/execution-detail";
import { TaskInstanceLogPanel } from "./log/task-instance-log-panel";

interface Props {
  activeTab: string;
  onChangeTab: (value: string) => void;
  selectedInstance?: DataSyncInstanceRecord;
  selectedLoading: boolean;
  instanceCount: number;
  autoRefresh: boolean;
  onAutoRefreshChange: (value: boolean) => void;
  refreshing: boolean;
  onRefresh: () => void;
  canceling: boolean;
  onCancel: () => void;
  onViewScheduleHistory: (id?: string) => void;
}
export function ExecutionTabs({
  activeTab,
  onChangeTab,
  selectedInstance,
  selectedLoading,
  instanceCount,
  autoRefresh,
  onAutoRefreshChange,
  refreshing,
  onRefresh,
  canceling,
  onCancel,
  onViewScheduleHistory,
}: Props) {
  const changeTab = onChangeTab;
  const setAutoRefresh = onAutoRefreshChange;
  const refreshExecution = onRefresh;
  const cancel = onCancel;
  const instances = { length: instanceCount };
  return (
    <Tabs value={activeTab} onValueChange={changeTab}>
      <TabsList>
        <TabsTab value="status">执行状态</TabsTab>
        <TabsTab value="log">运行日志</TabsTab>
      </TabsList>

      <TabsPanel value="status" className="pt-4">
        {selectedInstance ? (
          <DataSyncExecutionDetailContent
            record={selectedInstance}
            onViewScheduleHistory={() => {
              onViewScheduleHistory(selectedInstance.scheduleId || undefined);
            }}
            controls={
              <>
                <label className="flex items-center gap-2 text-xs text-[var(--yak-components-collapse-section-icon)]">
                  自动刷新
                  <Switch size="small" checked={autoRefresh} onCheckedChange={setAutoRefresh} />
                </label>
                <Button
                  size="small"
                  className="sync-execution-icon-button h-7 w-7"
                  aria-label="刷新执行状态"
                  title="刷新"
                  loading={refreshing}
                  onClick={() => void refreshExecution()}
                >
                  <RefreshCw size={14} />
                </Button>
                {canCancelTaskInstance(selectedInstance.status) ? (
                  <Button
                    size="small"
                    variant="danger"
                    loading={canceling}
                    onClick={() => void cancel()}
                  >
                    停止
                  </Button>
                ) : null}
              </>
            }
          />
        ) : selectedLoading ? (
          <div className="flex min-h-40 items-center justify-center">
            <Spinner size="medium" label="加载实例详情" />
          </div>
        ) : (
          <div className="py-12 text-center text-sm text-[var(--yak-components-muted-text)]">
            {instances.length === 0 ? "暂无执行记录" : "选择左侧执行记录"}
          </div>
        )}
      </TabsPanel>

      <TabsPanel value="log" className="pt-4">
        {selectedInstance && activeTab === "log" ? (
          <TaskInstanceLogPanel
            key={selectedInstance.id}
            instanceId={selectedInstance.id}
            active={isActiveDataSyncInstance(selectedInstance)}
            embedded
          />
        ) : selectedLoading ? (
          <div className="flex min-h-40 items-center justify-center">
            <Spinner size="medium" label="加载实例详情" />
          </div>
        ) : !selectedInstance ? (
          <div className="py-12 text-center text-sm text-[var(--yak-components-muted-text)]">
            选择左侧执行记录
          </div>
        ) : null}
      </TabsPanel>
    </Tabs>
  );
}

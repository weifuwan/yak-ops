import { Button, Spinner, Switch, Tabs, TabsList, TabsPanel, TabsTab } from "@yak-ops/yak-ui";
import { RefreshCw } from "lucide-react";
import { canCancelTaskInstance } from "@/service/data-sync/execution";
import { workflowStatusText, type DataSyncInstanceRecord,
  type DataSyncWorkflowInstance, type WorkflowExecutionStatus } from "@/service/data-sync";
import {
  DataSyncExecutionDetailContent,
  isActiveDataSyncInstance,
} from "../execution/execution-detail";
import { TaskInstanceLogPanel } from "./log/task-instance-log-panel";

interface Props {
  workflow?: DataSyncWorkflowInstance;
  pendingWorkflowId?: number;
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
  workflow,
  pendingWorkflowId,
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
  const workflowHistory = (() => {
    if (!workflow?.stateHistory) return [];
    try {
      return JSON.parse(workflow.stateHistory) as Array<{
        time: string;
        state: WorkflowExecutionStatus;
      }>;
    } catch {
      return [];
    }
  })();
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
                <Button variant="secondary"
                  size="sm"
                  className="sync-execution-icon-button h-7 w-7"
                  aria-label="刷新执行状态"
                  title="刷新"
                  isPending={refreshing}
                  onClick={() => void refreshExecution()}
                >
                  <RefreshCw size={14} />
                </Button>
                {canCancelTaskInstance(selectedInstance.status) ? (
                  <Button
                    size="sm"
                    variant="danger"
                    isPending={canceling}
                    onClick={() => void cancel()}
                  >
                    停止
                  </Button>
                ) : null}
              </>
            }
          />
        ) : pendingWorkflowId ? (
          <div className="rounded-lg border border-[var(--yak-components-panel-border)] p-5">
            <div className="text-sm font-medium">
              工作流 #{pendingWorkflowId} · {workflow ? workflowStatusText(workflow.state) : "启动中"}
            </div>
            <div className="mt-2 text-xs text-[var(--yak-components-muted-text)]">
              {workflow?.state === "SERIAL_WAIT"
                ? "等待串行调度，尚未创建任务执行实例"
                : "尚未创建任务执行实例"}
            </div>
            {workflowHistory.length > 0 ? (
              <div className="mt-4 space-y-2 border-t border-[var(--yak-components-panel-border)] pt-3">
                {workflowHistory.map((event, index) => (
                  <div key={index} className="flex justify-between gap-3 text-xs">
                    <span>{workflowStatusText(event.state)}</span>
                    <span className="text-[var(--yak-components-muted-text)]">{event.time}</span>
                  </div>
                ))}
              </div>
            ) : null}
          </div>
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
            {pendingWorkflowId
              ? "任务尚未派发到 Worker，暂无运行日志"
              : "选择左侧执行记录"}
          </div>
        ) : null}
      </TabsPanel>
    </Tabs>
  );
}

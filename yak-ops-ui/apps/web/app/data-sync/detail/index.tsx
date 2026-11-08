import { Button, PageHeader, Spinner } from "@yak-ops/yak-ui";
import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import { ScheduleDialog } from "@/app/schedule/dialog/schedule-dialog";
import { useTaskSchedules } from "@/app/schedule/use-task-schedules";
import { canManageTaskDefinition } from "@/service/data-sync/execution";
import {
  canStopWorkflow, getDataSyncWorkflowInstance, isWorkflowActive, workflowStatusText,
} from "@/service/data-sync";
import { useActiveTaskInstance } from "./use-active-task-instance";
import { ExecutionSidebar } from "./execution-sidebar";
import { TaskSummary } from "./task-summary";
import { ExecutionTabs } from "./execution-tabs";
import { useTaskInformation, useDetailScope } from "./use-task-information";
import { useExecutionHistory } from "./use-execution-history";
import { useExecutionActions } from "./use-execution-actions";

export function DataSyncTaskDetailPage() {
  const basePath = "/data-sync";
  const { id: taskId } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const scope = useDetailScope();
  const { task, taskLoading, dataSources, updatedBy } = useTaskInformation(taskId, scope);
  const realtime = task?.syncType === "REALTIME";
  const supportsScheduling = task?.syncType === "OFFLINE" && !task?.archived;
  const [scheduleOpen, setScheduleOpen] = useState(false);
  const [historyScheduleId, setHistoryScheduleId] = useState<string>();
  const schedules = useTaskSchedules(supportsScheduling && taskId ? [taskId] : []);
  const hasEnabledSchedule = Boolean(taskId && schedules.records.get(taskId)?.enabled);
  const {
    workflow,
    activeInstance,
    ready: instancesReady,
    refresh: refreshActiveInstances,
  } = useActiveTaskInstance(scope, taskId, task?.workflowCode);
  const activeWorkflow = Boolean(workflow && isWorkflowActive(workflow.state));
  const canStart =
    !task?.archived && !task?.configurationError && !activeWorkflow &&
    canManageTaskDefinition(instancesReady, activeInstance);
  const {
    executionId,
    workflowInstanceId,
    activeTab,
    instances,
    instancesLoading,
    status,
    pageNo,
    total,
    selectedInstance,
    selectedLoading,
    autoRefresh,
    setAutoRefresh,
    refreshing,
    loadInstances,
    refreshExecution,
    selectInstance,
    changeTab,
    changePage,
    changeStatus,
    selectSubmitted,
    selectWorkflowSubmitted,
    refreshAfterCancel,
    listError,
  } = useExecutionHistory(scope, taskId, activeInstance, hasEnabledSchedule, activeWorkflow);
  const workflowDetailQuery = useQuery({
    queryKey: ["data-sync-workflow-detail", scope, workflowInstanceId],
    queryFn: ({ signal }) => getDataSyncWorkflowInstance(Number(workflowInstanceId), signal),
    enabled: Boolean(scope && workflowInstanceId),
    refetchInterval: (result) =>
      result.state.data && isWorkflowActive(result.state.data.state) ? 2000 : false,
  });
  const selectedWorkflow = workflowDetailQuery.data ??
    (workflow && String(workflow.id) === workflowInstanceId ? workflow : undefined);
  const { run, cancel, copyTaskId, running, canceling, pendingWorkflowInstanceId } = useExecutionActions({
    taskId,
    task,
    canStart,
    selectedInstance,
    activeInstance,
    workflow,
    refreshActiveInstances,
    refreshAfterCancel,
    selectSubmitted,
    selectWorkflowSubmitted,
  });
  useEffect(() => {
    if (workflowInstanceId && activeInstance?.workflowInstanceId === Number(workflowInstanceId)) {
      selectSubmitted(activeInstance.id);
    }
  }, [workflowInstanceId, activeInstance?.workflowInstanceId, activeInstance?.id, selectSubmitted]);

  if (taskLoading || !task) {
    return (
      <div className="flex h-full items-center justify-center bg-white">
        <Spinner size="large" label="加载任务详情" />
      </div>
    );
  }

  return (
    <div className="flex h-full min-h-0 flex-col overflow-hidden bg-white text-[var(--yak-components-panel-text)] md:flex-row">
      <ExecutionSidebar
        realtime={realtime}
        status={status}
        onStatusChange={changeStatus}
        instances={instances}
        instancesLoading={instancesLoading}
        listError={listError}
        selectedId={executionId}
        onSelect={selectInstance}
        total={total}
        pageNo={pageNo}
        onPageChange={changePage}
        onRefresh={() => void loadInstances()}
      />
      <div className="flex min-h-0 min-w-0 flex-1 flex-col">
        <PageHeader
          title={task.name}
          bordered
          className="shrink-0 bg-white px-6 max-md:px-4"
          extra={
            <>
              {supportsScheduling ? (
                <Button variant="secondary"
                  size="sm"
                  isDisabled={Boolean(task.configurationError)}
                  onClick={() => {
                    setHistoryScheduleId(undefined);
                    setScheduleOpen(true);
                  }}
                >
                  调度
                </Button>
              ) : null}
              {task.archived ? (
                <span className="text-sm text-[var(--yak-components-muted-text)]">已归档 · 仅可查看历史</span>
              ) : task.status === "ONLINE" ? (
                activeWorkflow || activeInstance || pendingWorkflowInstanceId ? (
                  <div className="flex items-center gap-2">
                    <span className="text-sm text-[var(--yak-components-muted-text)]">
                      {activeInstance?.status === "RUNNING_EXECUTION"
                        ? "运行中"
                        : workflow
                          ? workflowStatusText(workflow.state)
                          : "启动中"}
                    </span>
                    {workflow && canStopWorkflow(workflow.state) ? (
                      <Button variant="secondary" size="sm" isPending={canceling} onClick={() => void cancel()}>
                        停止
                      </Button>
                    ) : null}
                  </div>
                ) : (
                  <Button variant="secondary"
                    size="sm"
                    isPending={running}
                    isDisabled={!canStart}
                    onClick={() => void run()}
                  >
                    {realtime ? "启动" : "运行"}
                  </Button>
                )
              ) : (
                <Button variant="secondary"
                  size="sm"
                  isDisabled={!canStart}
                  onClick={() => navigate(basePath + "/" + task.id + "/edit")}
                >
                  编辑
                </Button>
              )}
              <Button variant="secondary" size="sm" onClick={() => navigate(basePath)}>
                返回任务列表
              </Button>
            </>
          }
        />

        <div className="min-h-0 flex-1 overflow-y-auto px-6 pb-5 pt-5 max-md:px-4">
          {task.configurationError ? (
            <div className="mb-4 text-sm text-[var(--yak-components-danger-text)]">
              任务配置无法执行：{task.configurationError}
            </div>
          ) : null}
          <div className="space-y-8">
            <TaskSummary
              task={task}
              updatedBy={updatedBy}
              dataSources={dataSources}
              onCopyTaskId={() => void copyTaskId()}
            />
            <ExecutionTabs
              workflow={workflowInstanceId ? selectedWorkflow : undefined}
              pendingWorkflowId={workflowInstanceId ? Number(workflowInstanceId) : undefined}
              activeTab={activeTab}
              onChangeTab={changeTab}
              selectedInstance={selectedInstance}
              selectedLoading={selectedLoading}
              instanceCount={instances.length}
              autoRefresh={autoRefresh}
              onAutoRefreshChange={setAutoRefresh}
              refreshing={refreshing}
              onRefresh={() => void refreshExecution()}
              canceling={canceling}
              onCancel={() => void cancel()}
              onViewScheduleHistory={(id) => {
                setHistoryScheduleId(id);
                setScheduleOpen(true);
              }}
            />
          </div>
        </div>
      </div>
      {supportsScheduling || Boolean(historyScheduleId) ? (
        <ScheduleDialog
          targetId={task.id}
          published={task.status === "ONLINE" && !task.configurationError}
          open={scheduleOpen}
          historyScheduleId={historyScheduleId}
          onClose={() => setScheduleOpen(false)}
          onChanged={schedules.refresh}
          onViewInstance={(id) => {
            selectSubmitted(id);
          }}
        />
      ) : null}
    </div>
  );
}

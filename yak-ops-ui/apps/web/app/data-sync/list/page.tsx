import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";

import { Button, Modal, PageHeader } from "@yak-ops/yak-ui";
import { CreateSyncTaskDialog } from "../create/dialog";
import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { canCancelTaskInstance, canManageTaskDefinition } from "@/service/data-sync/execution";
import { canStopWorkflow, isWorkflowActive } from "@/service/data-sync";
import type { DataSyncTaskRecord } from "@/service/data-sync";
import { TaskFilters } from "./task-filters";\nimport { RunDataSyncTaskDialog } from "./run-dialog";
import { TaskTable } from "./task-table";
import { useTaskListFilters } from "./use-list-filters";
import {
  useTaskPage,
  useListDataSources,
  useListUpdateUsers,
  useListInstances,
} from "./use-list-queries";
import { useTaskListActions, type TaskCommand } from "./use-task-actions";

export function DataSyncPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const scope = currentUser?.id && currentWorkspace?.id ? currentUser.id + ":" + currentWorkspace.id : "";
  const filters = useTaskListFilters();
  const page = useTaskPage(scope, filters);
  const sources = useListDataSources(scope);
  const users = useListUpdateUsers(scope, page.validRecords);
  const instances = useListInstances(scope, page.validRecords, page.safeToAct);
  const commands = useTaskListActions(scope);

  const [createOpen, setCreateOpen] = useState(
    new URLSearchParams(location.search).get("create") === "1",
  );
  const [pendingDelete, setPendingDelete] = useState<DataSyncTaskRecord>();\n  const [pendingRun, setPendingRun] = useState<DataSyncTaskRecord>();

  const isWorkflowActiveForTask = (taskId: string) => {
    const workflow = instances.workflowsByTask.get(taskId);
    const submittedId = commands.submittedByTask[taskId];
    return Boolean(
      (submittedId && (!workflow || workflow.id < submittedId)) ||
      (workflow && isWorkflowActive(workflow.state)),
    );
  };

  const canManage = (task: DataSyncTaskRecord) =>
    page.safeToAct &&
    instances.ready &&
    !isWorkflowActiveForTask(task.id) &&
    canManageTaskDefinition(instances.ready, instances.activeByTask.get(task.id));

  const command = (value: TaskCommand) => {
    if (!scope || commands.pending || !page.safeToAct) return;
    if (value.kind === "stop") {
      const workflow = instances.workflowsByTask.get(value.task.id);
      const active = instances.activeByTask.get(value.task.id);
      if (!instances.ready || !(
        (workflow && workflow.id === value.workflowInstanceId && canStopWorkflow(workflow.state)) ||
        (active && active.workflowInstanceId === value.workflowInstanceId && canCancelTaskInstance(active.status))
      )) return;
    } else if (!canManage(value.task)) {
      return;
    }

    if (value.kind === "run" && !value.options) {
      setPendingRun(value.task);
      return;
    }

    commands.execute(value, {
      onSuccess: () => {
        if (value.kind === "delete") setPendingDelete(undefined);
        if (value.kind === "run") setPendingRun(undefined);
      },
    });
  };

  return (
    <>
      <div className="flex min-h-full flex-col bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
        <PageHeader title="数据同步" bordered className="bg-white px-6 max-md:px-4" />
        <div className="flex min-h-0 min-w-0 flex-1 px-6 pb-4 pt-5 max-md:px-4">
          <div className="flex min-h-0 min-w-0 flex-1 flex-col bg-white p-4">
            <TaskFilters
              filters={filters}
              keyword={filters.keywordInput}
              onKeywordChange={filters.setKeywordInput}
              onFilterChange={filters.update}
              onCreate={() => setCreateOpen(true)}
            />
            {page.error ? (
              <div className="mt-3 flex items-center gap-2 text-sm text-[var(--yak-components-danger-text)]">
                任务列表加载失败
                <Button variant="secondary" size="sm" onClick={page.retry}>
                  重试
                </Button>
              </div>
            ) : null}
            {instances.error ? (
              <div className="mt-3 text-sm text-[var(--yak-components-danger-text)]">
                执行状态获取失败，请刷新任务列表后重试
              </div>
            ) : null}
            {page.invalidCount > 0 ? (
              <div className="mt-3 text-sm text-[var(--yak-components-danger-text)]">
                {page.invalidCount} 个任务配置无法解析，请查看对应行
              </div>
            ) : null}
            <div className="mt-4 min-h-0 min-w-0 flex-1">
              <TaskTable
                records={page.records}
                pageNo={filters.pageNo}
                total={page.total}
                loading={page.loading}
                failed={page.error}
                data={{
                  dataSourceMap: sources.dataSourceMap,
                  dataSourcesLoading: sources.loading,
                  updateUserMap: users.users,
                  updateUsersLoading: users.loading,
                  latestByTask: instances.latestByTask,
                  activeByTask: instances.activeByTask,
                  workflowsByTask: instances.workflowsByTask,
                  submittedByTask: commands.submittedByTask,
                  instancesLoading: instances.loading,
                  instancesReady: instances.ready,
                  actionKey: commands.actionKey,
                }}
                actions={{
                  command,
                  delete: (task) => {
                    if (canManage(task)) setPendingDelete(task);
                  },
                  page: (pageNo) => filters.update({ pageNo }),
                }}
              />
            </div>
          </div>
        </div>
      </div>

      {pendingRun ? (
        <RunDataSyncTaskDialog
          key={pendingRun.id}
          task={pendingRun}
          pending={commands.actionKey === pendingRun.id + ":run"}
          onClose={() => {
            if (!commands.pending) setPendingRun(undefined);
          }}
          onRun={(options) => {
            if (canManage(pendingRun)) {
              command({ kind: "run", task: pendingRun, options });
            }
          }}
        />
      ) : null}

      {createOpen ? (
        <CreateSyncTaskDialog
          onClose={() => {
            setCreateOpen(false);
            const query = new URLSearchParams(location.search);
            if (query.has("create")) {
              query.delete("create");
              navigate({ pathname: "/data-sync", search: query.toString() }, { replace: true });
            }
          }}
        />
      ) : null}

      <Modal
        open={Boolean(pendingDelete)}
        centered
        width={420}
        title="删除同步任务"
        onClose={() => {
          if (!commands.pending) setPendingDelete(undefined);
        }}
        footer={
          <>
            <Button variant="secondary"
              size="sm"
              isDisabled={commands.pending}
              onClick={() => setPendingDelete(undefined)}
            >
              取消
            </Button>
            <Button
              size="sm"
              variant="danger"
              isPending={commands.actionKey === (pendingDelete?.id || "") + ":delete"}
              onClick={() => {
                if (pendingDelete && canManage(pendingDelete))
                  command({ kind: "delete", task: pendingDelete });
              }}
            >
              删除
            </Button>
          </>
        }
      >
        <div className="text-sm leading-6 text-[var(--yak-components-collapse-section-icon)]">
          确定删除任务“{pendingDelete?.name}”吗？历史任务实例不会在这里删除。
        </div>
      </Modal>
    </>
  );
}

export default DataSyncPage;

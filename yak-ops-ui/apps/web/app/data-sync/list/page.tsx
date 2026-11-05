import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { Button, Modal, PageHeader } from "@yak-ops/yak-ui";

import { CreateSyncTaskDialog } from "../create/dialog";
import { ScheduleDialog } from "@/app/schedule/dialog/schedule-dialog";
import { useAuth } from "@/hooks/use-auth";
import { canCancelTaskInstance, canManageTaskDefinition } from "@/service/data-sync/execution";
import type { DataSyncTaskRecord } from "@/service/data-sync";

import { TaskFilters } from "./task-filters";
import { TaskTable } from "./task-table";
import { useTaskListFilters } from "./use-list-filters";
import {
  useTaskPage,
  useListDataSources,
  useListUpdateUsers,
  useListSchedules,
  useListInstances,
} from "./use-list-queries";
import { useTaskListActions, type TaskCommand } from "./use-task-actions";
import { listQueryKeys } from "./query-keys";

export function DataSyncPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const { currentUser } = useAuth();
  const scope = currentUser?.id ?? "";

  const filters = useTaskListFilters();
  const page = useTaskPage(scope, filters);
  const sources = useListDataSources(scope);
  const users = useListUpdateUsers(scope, page.validRecords);
  const schedules = useListSchedules(scope, page.validRecords);
  const instances = useListInstances(scope, page.validRecords, page.safeToAct);
  const commands = useTaskListActions(scope);

  const [createOpen, setCreateOpen] = useState(
    new URLSearchParams(location.search).get("create") === "1",
  );
  const [scheduleTask, setScheduleTask] = useState<DataSyncTaskRecord>();
  const [pendingDelete, setPendingDelete] = useState<DataSyncTaskRecord>();
  const scheduledStatus =
    page.validRecords.find((task) => task.id === scheduleTask?.id)?.status ?? scheduleTask?.status;

  const canManage = (task: DataSyncTaskRecord) =>
    page.safeToAct &&
    instances.ready &&
    canManageTaskDefinition(instances.ready, instances.activeByTask.get(task.id));

  const command = (value: TaskCommand) => {
    if (!scope || commands.pending || !page.safeToAct) return;
    if (value.kind === "schedule") {
      if (!schedules.schedules.has(value.task.id)) return;
    } else if (value.kind === "stop") {
      if (!instances.ready || !canCancelTaskInstance(value.instance.status)) return;
    } else if (!canManage(value.task)) {
      return;
    }
    commands.execute(value, {
      onSuccess: () => {
        if (value.kind === "delete") setPendingDelete(undefined);
      },
    });
  };

  const refreshSchedules = () =>
    void queryClient.invalidateQueries({ queryKey: listQueryKeys.schedules(scope) });

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
                <Button size="small" onClick={page.retry}>
                  重试
                </Button>
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
                  schedules: {
                    records: schedules.schedules,
                    loading: schedules.loading,
                    error: schedules.error,
                  },
                  latestByTask: instances.latestByTask,
                  activeByTask: instances.activeByTask,
                  instancesLoading: instances.loading,
                  instancesReady: instances.ready,
                  actionKey: commands.actionKey,
                }}
                actions={{
                  configureSchedule: setScheduleTask,
                  toggleSchedule: (task, enabled) => {
                    const schedule = schedules.schedules.get(task.id);
                    if (schedule) command({ kind: "schedule", task, schedule, enabled });
                  },
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
      {scheduleTask ? (
        <ScheduleDialog
          targetId={scheduleTask.id}
          published={scheduledStatus === "PUBLISHED"}
          open
          onClose={() => setScheduleTask(undefined)}
          onChanged={refreshSchedules}
          onViewInstance={(id) =>
            navigate(
              "/data-sync/" + scheduleTask.id + "/detail?executionId=" + encodeURIComponent(id),
            )
          }
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
            <Button
              size="small"
              disabled={commands.pending}
              onClick={() => setPendingDelete(undefined)}
            >
              取消
            </Button>
            <Button
              size="small"
              variant="danger"
              loading={commands.actionKey === (pendingDelete?.id || "") + ":delete"}
              onClick={() => {
                if (pendingDelete && canManage(pendingDelete)) {
                  command({ kind: "delete", task: pendingDelete });
                }
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

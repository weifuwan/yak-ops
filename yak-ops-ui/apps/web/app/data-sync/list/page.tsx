import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Modal, PageHeader } from "@yak-ops/yak-ui";
import { CreateSyncTaskDialog } from "../create/dialog";
import { useAuth } from "@/hooks/use-auth";
import { canCancelTaskInstance, canManageTaskDefinition } from "@/service/data-sync/execution";
import type { DataSyncTaskRecord } from "@/service/data-sync";
import {
  listDataSyncProjects,
  selectedDataSyncProject,
  selectDataSyncProject,
} from "@/service/data-sync/project";
import { TaskFilters } from "./task-filters";
import { TaskTable } from "./task-table";
import { DataSyncInstanceTable } from "./instance-table";
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
  const queryClient = useQueryClient();
  const { currentUser } = useAuth();
  const projectQuery = useQuery({
    queryKey: ["data-sync-projects", currentUser?.id],
    queryFn: listDataSyncProjects,
    enabled: Boolean(currentUser?.id),
  });
  const [projectCode, setProjectCode] = useState(selectedDataSyncProject());
  const [tab, setTab] = useState<"tasks" | "instances">("tasks");

  useEffect(() => {
    const available = projectQuery.data ?? [];
    if (available.length && !available.some((project) => project.code === projectCode)) {
      selectDataSyncProject(available[0].code);
      setProjectCode(available[0].code);
    }
  }, [projectCode, projectQuery.data]);

  const scope = currentUser?.id && projectCode ? currentUser.id + ":" + projectCode : "";
  const filters = useTaskListFilters();
  const page = useTaskPage(scope, filters);
  const sources = useListDataSources(scope);
  const users = useListUpdateUsers(scope, page.validRecords);
  const instances = useListInstances(scope, page.validRecords, page.safeToAct);
  const commands = useTaskListActions(scope);

  const [createOpen, setCreateOpen] = useState(
    new URLSearchParams(location.search).get("create") === "1",
  );
  const [pendingDelete, setPendingDelete] = useState<DataSyncTaskRecord>();

  const canManage = (task: DataSyncTaskRecord) =>
    page.safeToAct &&
    instances.ready &&
    canManageTaskDefinition(instances.ready, instances.activeByTask.get(task.id));

  const command = (value: TaskCommand) => {
    if (!scope || commands.pending || !page.safeToAct) return;
    if (value.kind === "stop") {
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

  return (
    <>
      <div className="flex min-h-full flex-col bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
        <PageHeader title="数据同步" bordered className="bg-white px-6 max-md:px-4" />
        <div className="flex min-h-0 min-w-0 flex-1 px-6 pb-4 pt-5 max-md:px-4">
          <div className="flex min-h-0 min-w-0 flex-1 flex-col bg-white p-4">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
              <div className="flex gap-5 text-sm">
                <button
                  type="button"
                  onClick={() => setTab("tasks")}
                  className={tab === "tasks" ? "font-medium text-[var(--yak-color-primary)]" : ""}
                >
                  任务定义
                </button>
                <button
                  type="button"
                  onClick={() => setTab("instances")}
                  className={
                    tab === "instances" ? "font-medium text-[var(--yak-color-primary)]" : ""
                  }
                >
                  执行实例
                </button>
              </div>
              <select
                value={projectCode}
                onChange={(event) => {
                  selectDataSyncProject(event.target.value);
                  setProjectCode(event.target.value);
                  void queryClient.invalidateQueries({ queryKey: ["data-sync-list"] });
                }}
                className="h-8 min-w-40 rounded border border-[var(--yak-components-border)] bg-white px-2 text-sm"
              >
                {(projectQuery.data ?? []).map((project) => (
                  <option key={project.code} value={project.code}>
                    {project.name}
                  </option>
                ))}
              </select>
            </div>
            {tab === "tasks" ? (
              <>
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
                      latestByTask: instances.latestByTask,
                      activeByTask: instances.activeByTask,
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
              </>
            ) : (
              <DataSyncInstanceTable scope={scope} />
            )}
          </div>
        </div>
      </div>

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

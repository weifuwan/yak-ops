import { useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Info } from "lucide-react";
import { toast } from "@yak-ops/yak-ui";

import { useWorkspace } from "@/hooks/use-workspace";
import {
  isTaskGroupEnabled,
  listTaskGroupsByProjectCode,
} from "@/service/task-group";
import { updateWorkflowTaskGroupSettings } from "@/service/workflow/definition";
import type { WorkflowSidePanelController } from "../hooks/useWorkflowSidePanelController";

export function WorkflowTaskGroupSection({
  controller,
}: {
  controller: WorkflowSidePanelController;
}) {
  const { workflowCode, dag, scheduleIsOnline } = controller;
  const queryClient = useQueryClient();
  const { currentWorkspace } = useWorkspace();
  const projectCode = currentWorkspace?.id ?? "";
  const tasks = dag.taskDefinitionList ?? [];
  const firstTask = tasks[0];
  const hasMixedSettings = tasks.some(
    (task) =>
      (task.taskGroupId ?? 0) !== (firstTask?.taskGroupId ?? 0) ||
      (task.taskGroupPriority ?? 0) !== (firstTask?.taskGroupPriority ?? 0),
  );
  const initial = useMemo(
    () => ({
      taskGroupId: firstTask?.taskGroupId && firstTask.taskGroupId > 0
        ? firstTask.taskGroupId
        : 0,
      taskGroupPriority: String(firstTask?.taskGroupPriority ?? 0),
    }),
    [firstTask?.taskGroupId, firstTask?.taskGroupPriority],
  );
  const [taskGroupId, setTaskGroupId] = useState(initial.taskGroupId);
  const [priority, setPriority] = useState(initial.taskGroupPriority);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setTaskGroupId(initial.taskGroupId);
    setPriority(initial.taskGroupPriority);
  }, [initial]);

  const groupsQuery = useQuery({
    queryKey: ["data-development", "task-groups", projectCode],
    queryFn: ({ signal }) =>
      listTaskGroupsByProjectCode(projectCode, { pageNo: 1, pageSize: 1000 }, signal),
    enabled: Boolean(projectCode),
    staleTime: 30_000,
  });
  const groups = groupsQuery.data?.totalList ?? [];
  const selectedGroup = groups.find((group) => group.id === taskGroupId);

  const handleSave = async () => {
    if (scheduleIsOnline) {
      toast.warning("请先下线调度，再修改工作流任务组配置");
      return;
    }
    const numericPriority = Number(priority || 0);
    if (!Number.isSafeInteger(numericPriority) || numericPriority < 0) {
      toast.warning("组内优先级必须为大于等于 0 的整数");
      return;
    }
    if (taskGroupId > 0) {
      if (groupsQuery.isPending || groupsQuery.isError) {
        toast.warning("任务组列表尚未加载完成，请刷新后重试");
        return;
      }
      if (!selectedGroup) {
        toast.warning("所选任务组已不存在，请重新选择");
        return;
      }
      if (!isTaskGroupEnabled(selectedGroup.status)) {
        toast.warning("所选任务组已停用，请启用任务组或重新选择");
        return;
      }
    }

    setSaving(true);
    try {
      await updateWorkflowTaskGroupSettings(workflowCode, taskGroupId, taskGroupId > 0 ? numericPriority : 0);
      await queryClient.invalidateQueries({
        queryKey: ["data-development", "workflow-definition"],
      });
      await queryClient.invalidateQueries({
        queryKey: ["data-development", "workflow-explorer"],
      });
      toast.success("工作流任务组配置已应用到全部节点");
    } catch (error) {
      toast.error("保存工作流任务组配置失败", {
        description: error instanceof Error ? error.message : "请稍后重试",
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-2.5 border-t border-[#edf0f3] pt-3">
      <div className="flex items-center gap-2 text-xs font-semibold text-[#303846]">
        <span>TaskGroup</span>
        <span title="工作流统一设置会应用到当前工作流的全部节点">
          <Info size={13} className="text-[#8b93a1]" />
        </span>
        {hasMixedSettings ? (
          <span className="ml-auto text-[11px] font-normal text-[#c47a22]">
            当前节点配置不一致
          </span>
        ) : null}
      </div>
      <label className="grid grid-cols-[92px_minmax(0,1fr)] items-center gap-3">
        <span className="text-xs text-[#687385]">任务组</span>
        <select
          className="h-8 min-w-0 rounded-md border border-[#d9dee7] bg-white px-2 text-xs text-[#303846] outline-none transition-colors focus:border-[#315efb]"
          value={String(taskGroupId)}
          disabled={groupsQuery.isPending || groupsQuery.isError}
          onChange={(event) => {
            const next = Number(event.target.value);
            setTaskGroupId(next);
            if (next === 0) setPriority("0");
          }}
        >
          <option value="0">不使用任务组</option>
          {groups.map((group) => (
            <option
              key={group.id}
              value={String(group.id)}
              disabled={!isTaskGroupEnabled(group.status) && group.id !== taskGroupId}
            >
              {group.name} · {group.useSize ?? 0}/{group.groupSize}
              {!isTaskGroupEnabled(group.status) ? " · 已停用" : ""}
            </option>
          ))}
        </select>
      </label>
      <label className="grid grid-cols-[92px_minmax(0,1fr)] items-center gap-3">
        <span className="text-xs text-[#687385]">组内优先级</span>
        <input
          type="number"
          min={0}
          step={1}
          className="h-8 min-w-0 rounded-md border border-[#d9dee7] bg-white px-2 text-xs text-[#303846] outline-none transition-colors focus:border-[#315efb] disabled:bg-[#f5f6f8] disabled:text-[#a0a7b2]"
          value={priority}
          disabled={taskGroupId <= 0}
          onChange={(event) => setPriority(event.target.value)}
        />
      </label>
      <div className="flex items-center justify-between gap-2">
        <span className="text-[11px] leading-5 text-[#8b93a1]">
          {taskGroupId > 0 && selectedGroup
            ? `并发容量 ${selectedGroup.useSize ?? 0}/${selectedGroup.groupSize}`
            : "保存后统一应用到工作流内全部节点"}
        </span>
        <button
          type="button"
          disabled={saving || scheduleIsOnline || groupsQuery.isFetching || tasks.length === 0}
          onClick={() => void handleSave()}
          className="h-7 shrink-0 rounded-md bg-[#315efb] px-3 text-xs font-medium text-white transition-colors hover:bg-[#244fe0] disabled:cursor-not-allowed disabled:opacity-50"
        >
          {saving ? "保存中..." : scheduleIsOnline ? "请先下线调度" : "应用到全部节点"}
        </button>
      </div>
      {groupsQuery.isError ? (
        <button
          type="button"
          onClick={() => void groupsQuery.refetch()}
          className="text-[11px] text-[#315efb] hover:underline"
        >
          任务组加载失败，点击重试
        </button>
      ) : null}
    </div>
  );
}

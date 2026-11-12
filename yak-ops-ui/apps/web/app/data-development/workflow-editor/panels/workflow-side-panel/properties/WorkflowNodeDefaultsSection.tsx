import { useEffect, useMemo, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "@yak-ops/yak-ui";

import {
  getTaskGroupValidationError, normalizeTaskCommonSettings, parseTaskCommonSettings,
  TaskCommonSettingsSection, useTaskGroups, type TaskCommonSettings,
} from "../../../../task-editor/task-common-settings-section";
import { updateWorkflowTaskCommonSettings } from "@/service/workflow/definition";
import type { WorkflowSidePanelController } from "../hooks/useWorkflowSidePanelController";

export function WorkflowNodeDefaultsSection({
  controller,
}: {
  controller: WorkflowSidePanelController;
}) {
  const queryClient = useQueryClient();
  const { workflowCode, dag, workflowIsOnline } = controller;
  const tasks = dag.taskDefinitionList ?? [];
  const firstTask = tasks[0];
  const initial = useMemo(() => parseTaskCommonSettings(firstTask), [firstTask]);
  const [settings, setSettings] = useState<TaskCommonSettings>(initial);
  const [saving, setSaving] = useState(false);
  const { projectCode, taskGroups, taskGroupsQuery } = useTaskGroups();

  useEffect(() => setSettings(initial), [initial]);

  const hasMixedSettings = useMemo(
    () => tasks.some(task => JSON.stringify(parseTaskCommonSettings(task)) !== JSON.stringify(initial)),
    [tasks, initial],
  );

  const applyToAllNodes = async () => {
    if (workflowIsOnline) {
      toast.warning("请先下线工作流，再修改节点默认配置");
      return;
    }
    let patch;
    try {
      patch = normalizeTaskCommonSettings(settings);
    } catch (error) {
      toast.warning(error instanceof Error ? error.message : "请检查节点通用配置");
      return;
    }
    const groupError = getTaskGroupValidationError(settings, {
      projectCode, taskGroups, isPending: taskGroupsQuery.isPending, isError: taskGroupsQuery.isError,
    });
    if (groupError) {
      toast.warning(groupError);
      return;
    }

    setSaving(true);
    try {
      await updateWorkflowTaskCommonSettings(workflowCode, patch);
      await queryClient.invalidateQueries({ queryKey: ["data-development", "workflow-definition"] });
      await queryClient.invalidateQueries({ queryKey: ["data-development", "workflow-explorer"] });
      toast.success("节点默认配置已应用到全部已有节点");
    } catch (error) {
      toast.error("应用节点默认配置失败", {
        description: error instanceof Error ? error.message : "请稍后重试",
      });
    } finally {
      setSaving(false);
    }
  };

  const description = tasks.length === 0
    ? "当前工作流还没有节点，添加节点后即可批量应用通用配置。"
    : hasMixedSettings
      ? "当前节点的通用配置不一致。应用后会统一覆盖当前工作流的全部已有节点，单个节点之后仍可单独调整。"
      : "将这组通用配置批量应用到当前工作流的全部已有节点；单个节点之后仍可单独调整。新建节点自动继承默认值需要后端增加工作流级字段，当前暂未实现。";

  return (
    <TaskCommonSettingsSection
      id="workflow-node-default-settings"
      compact
      title="节点默认配置"
      settings={settings}
      onChange={setSettings}
      disabled={workflowIsOnline || tasks.length === 0}
      description={description}
      footer={
        <button
          type="button"
          disabled={saving || workflowIsOnline || tasks.length === 0}
          onClick={() => void applyToAllNodes()}
          className="h-7 shrink-0 rounded-md bg-[#315efb] px-3 text-xs font-medium text-white transition-colors hover:bg-[#244fe0] disabled:cursor-not-allowed disabled:opacity-50"
        >
          {saving ? "应用中..." : workflowIsOnline ? "请先下线工作流" : "应用到全部节点"}
        </button>
      }
    />
  );
}

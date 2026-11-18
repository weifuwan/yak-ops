import { useEffect, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "@yak-ops/yak-ui";

import { getWorkflowDefinition } from "@/service/workflow/definition";
import { listWorkflowResources } from "@/service/resources";
import {
  startWorkflowInstance,
  stopWorkflowInstance,
} from "@/service/workflow/execution";

import {
  buildShellTaskParams,
  parseShellTaskForm,
} from "./adapter";
import { buildResourceTreeOptions } from "./resource-adapter";
import {
  getTaskGroupValidationError,
  normalizeTaskCommonSettings,
  parseTaskCommonSettings,
  useTaskGroups,
  type TaskCommonSettings,
} from "../task-common-settings-section";
import type {
  ShellConfigTab,
  ShellEditorView,
  ShellLocalParam,
  ShellTaskEditorProps,
  ShellTaskForm,
} from "./types";

export function useShellTaskEditor({
  workflowCode,
  task,
  isDraft,
  readOnly,
  canRun,
  onApplyTask,
  onDirtyChange,
}: ShellTaskEditorProps) {
  const queryClient = useQueryClient();
  const { projectCode, taskGroups, taskGroupsQuery } = useTaskGroups();
  const initialForm = useMemo(
    () => parseShellTaskForm(task?.taskParams),
    [task?.taskParams],
  );
  const initialCommonSettings = useMemo(() => parseTaskCommonSettings(task), [task]);
  const [form, setForm] = useState<ShellTaskForm>(initialForm);
  const [baselineForm, setBaselineForm] = useState<ShellTaskForm>(initialForm);
  const [commonSettings, setCommonSettings] = useState<TaskCommonSettings>(initialCommonSettings);
  const [baselineCommonSettings, setBaselineCommonSettings] = useState<TaskCommonSettings>(initialCommonSettings);
  const [currentTask, setCurrentTask] = useState(task);
  const [activeView, setActiveView] = useState<ShellEditorView>("form");
  const [activeConfigTab, setActiveConfigTab] = useState<ShellConfigTab>("parameters");
  const [saving, setSaving] = useState(false);
  const [running, setRunning] = useState(false);
  const [stopping, setStopping] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [lastWorkflowInstanceId, setLastWorkflowInstanceId] = useState<number | null>(null);

  useEffect(() => {
    setForm(initialForm);
    setBaselineForm(initialForm);
    setCommonSettings(initialCommonSettings);
    setBaselineCommonSettings(initialCommonSettings);
    setCurrentTask(task);
  }, [initialForm, initialCommonSettings, task]);

  const resourceQuery = useQuery({
    queryKey: ["data-development", "shell-task-resources"],
    queryFn: ({ signal }) => listWorkflowResources(signal),
    staleTime: 60_000,
  });

  const resourceTree = useMemo(
    () => buildResourceTreeOptions(resourceQuery.data ?? []),
    [resourceQuery.data],
  );

  const updateField = <K extends keyof ShellTaskForm>(
    field: K,
    value: ShellTaskForm[K],
  ) => {
    setForm((current) => ({ ...current, [field]: value }));
  };

  const updateLocalParam = (
    index: number,
    patch: Partial<ShellLocalParam>,
  ) => {
    setForm((current) => ({
      ...current,
      localParams: current.localParams.map((item, itemIndex) =>
        itemIndex === index ? { ...item, ...patch } : item,
      ),
    }));
  };

  const jsonText = useMemo(() => {
    let commonFields: Record<string, unknown>;
    try {
      commonFields = normalizeTaskCommonSettings(commonSettings);
    } catch {
      commonFields = commonSettings;
    }

    return JSON.stringify({
      taskType: "SHELL",
      taskParams: buildShellTaskParams(form, currentTask.taskParams),
      ...commonFields,
    }, null, 2);
  }, [commonSettings, currentTask.taskParams, form]);

  const isDirty =
    JSON.stringify(form) !== JSON.stringify(baselineForm) ||
    JSON.stringify(commonSettings) !== JSON.stringify(baselineCommonSettings);

  useEffect(() => {
    onDirtyChange(isDirty);
  }, [isDirty, onDirtyChange]);

  useEffect(() => () => onDirtyChange(false), [onDirtyChange]);

  const handleSave = async (): Promise<boolean> => {
    if (!currentTask || readOnly) return false;

    if (!form.rawScript.trim()) {
      toast.warning("请先编写 Shell 脚本");
      return false;
    }

    const paramNames = form.localParams
      .map((item) => item.prop.trim())
      .filter(Boolean);
    if (new Set(paramNames).size !== paramNames.length) {
      toast.warning("自定义参数名称不能重复");
      return false;
    }

    let commonPatch;
    try {
      commonPatch = normalizeTaskCommonSettings(commonSettings);
    } catch (error) {
      toast.warning(error instanceof Error ? error.message : "请检查节点通用配置");
      return false;
    }

    const taskGroupError = getTaskGroupValidationError(commonSettings, {
      projectCode,
      taskGroups,
      isPending: taskGroupsQuery.isPending,
      isError: taskGroupsQuery.isError,
    });
    if (taskGroupError) {
      toast.warning(taskGroupError);
      return false;
    }

    setSaving(true);
    try {
      const taskParams = buildShellTaskParams(form, currentTask.taskParams);
      const nextTask = { ...currentTask, taskParams, ...commonPatch };
      if (!onApplyTask(nextTask)) return false;
      setCurrentTask(nextTask);
      setBaselineForm(form);
      setBaselineCommonSettings(commonSettings);
      toast.success("Shell 节点配置已应用，请保存工作流");
      return true;
    } catch {
      toast.error("保存 Shell 节点配置失败，请稍后重试");
      return false;
    } finally {
      setSaving(false);
    }
  };

  const handleRun = async () => {
    if (!currentTask) return;
    if (isDraft || !canRun) {
      toast.warning("请先保存并发布工作流，再运行节点");
      return;
    }
    if (isDirty) {
      toast.warning("当前节点有未保存修改，请先保存工作流");
      return;
    }
    if (!form.rawScript.trim()) {
      toast.warning("请先编写 Shell 脚本");
      return;
    }

    setRunning(true);
    try {
      const result = await startWorkflowInstance(String(workflowCode), {
        startNodeList: currentTask.code,
        taskDependType: "TASK_ONLY",
      });
      setLastWorkflowInstanceId(result.workflowInstanceId);
      toast.success("单节点运行已提交", {
        description: "工作流实例 ID：" + result.workflowInstanceId,
      });
    } catch (error) {
      toast.error("Shell 节点运行失败", {
        description: error instanceof Error ? error.message : "请稍后重试。",
      });
    } finally {
      setRunning(false);
    }
  };

  const handleStop = async () => {
    if (!lastWorkflowInstanceId) return;

    setStopping(true);
    try {
      await stopWorkflowInstance(lastWorkflowInstanceId);
      toast.success("节点停止请求已提交");
      setLastWorkflowInstanceId(null);
    } catch (error) {
      toast.error("停止节点失败", {
        description: error instanceof Error ? error.message : "请稍后重试。",
      });
    } finally {
      setStopping(false);
    }
  };

  const handleRefresh = async () => {
    if (isDirty) {
      toast.warning("当前配置有未保存修改，请先保存后再刷新");
      return;
    }
    if (isDraft) {
      toast.info("新建节点尚未保存到后端，暂无可刷新的服务端配置");
      return;
    }

    setRefreshing(true);
    try {
      const refreshTasks: Array<Promise<unknown>> = [resourceQuery.refetch()];
      if (currentTask) {
        refreshTasks.push(
          getWorkflowDefinition(workflowCode).then((dag) => {
            const latestTask = dag.taskDefinitionList.find(
              (item) => item.code === currentTask.code,
            );
            if (!latestTask) throw new Error("节点不存在或已被删除");

            const nextForm = parseShellTaskForm(latestTask.taskParams);
            const nextCommonSettings = parseTaskCommonSettings(latestTask);
            if (!onApplyTask(latestTask)) return;

            setCurrentTask(latestTask);
            setForm(nextForm);
            setBaselineForm(nextForm);
            setCommonSettings(nextCommonSettings);
            setBaselineCommonSettings(nextCommonSettings);
          }),
        );
      }

      await Promise.all(refreshTasks);
      await queryClient.invalidateQueries({
        queryKey: ["data-development", "workflow-definition"],
      });
      toast.success("Shell 节点配置已刷新");
    } catch (error) {
      toast.error("刷新失败", {
        description: error instanceof Error ? error.message : "请稍后重试。",
      });
    } finally {
      setRefreshing(false);
    }
  };

  return {
    currentTask,
    form,
    commonSettings,
    setCommonSettings,
    activeView,
    setActiveView,
    activeConfigTab,
    setActiveConfigTab,
    saving,
    running,
    stopping,
    refreshing,
    lastWorkflowInstanceId,
    resourceQuery,
    resourceTree,
    jsonText,
    isDirty,
    handleSave,
    handleRun,
    handleStop,
    handleRefresh,
    updateField,
    updateLocalParam,
  };
}

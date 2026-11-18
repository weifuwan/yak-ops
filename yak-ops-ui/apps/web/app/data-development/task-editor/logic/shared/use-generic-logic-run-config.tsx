import { useEffect, useMemo, useState } from "react";

import type { WorkflowTaskRecord } from "@/service/workflow/definition";

import {
  getTaskGroupValidationError,
  normalizeTaskCommonSettings,
  parseTaskCommonSettings,
  TaskCommonSettingsSection,
  useTaskGroups,
  type TaskCommonSettings,
} from "../../task-common-settings-section";
import type { LogicTaskRunConfigController } from "./logic-task-run-config";

export function useGenericLogicTaskRunConfig(
  task: WorkflowTaskRecord,
  disabled: boolean,
): LogicTaskRunConfigController {
  const { projectCode, taskGroups, taskGroupsQuery } = useTaskGroups();
  const initialSettings = useMemo(() => parseTaskCommonSettings(task), [task]);
  const [settings, setSettings] = useState<TaskCommonSettings>(initialSettings);
  const [baselineSettings, setBaselineSettings] = useState<TaskCommonSettings>(initialSettings);

  useEffect(() => {
    setSettings(initialSettings);
    setBaselineSettings(initialSettings);
  }, [initialSettings]);

  const isDirty = JSON.stringify(settings) !== JSON.stringify(baselineSettings);

  return {
    isDirty,
    content: (
      <TaskCommonSettingsSection
        id="generic-logic-run-config"
        settings={settings}
        onChange={setSettings}
        disabled={disabled}
        compact
      />
    ),
    getPatch: () => normalizeTaskCommonSettings(settings),
    getPreviewPatch: () => {
      try {
        return normalizeTaskCommonSettings(settings) as unknown as Record<string, unknown>;
      } catch {
        return settings as unknown as Record<string, unknown>;
      }
    },
    validate: () => getTaskGroupValidationError(settings, {
      projectCode,
      taskGroups,
      isPending: taskGroupsQuery.isPending,
      isError: taskGroupsQuery.isError,
    }),
    markSaved: () => setBaselineSettings(settings),
    refreshFromTask: (latestTask) => {
      const nextSettings = parseTaskCommonSettings(latestTask);
      setSettings(nextSettings);
      setBaselineSettings(nextSettings);
    },
  };
}

import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { toast } from "@yak-ops/yak-ui";

import { listAlertGroups } from "@/service/alert";
import {
  createWorkflowSchedule,
  getWorkflowSchedule,
  offlineWorkflowSchedule,
  previewWorkflowSchedule,
  publishWorkflowSchedule,
  updateWorkflowSchedule,
  type WorkflowScheduleOptions,
} from "@/service/workflow/management";
import {
  defaultScheduleForm,
  scheduleRecordToForm,
  toScheduleParam,
} from "../shared/utils";
import type {
  PanelKey,
  ScheduleSection,
  WorkflowScheduleForm,
} from "../shared/types";

interface UseWorkflowScheduleOptions {
  workflowCode: number;
  workspaceId: string;
  activePanel: PanelKey | null;
  workflowIsOnline: boolean;
}

export function useWorkflowSchedule({
  workflowCode,
  workspaceId,
  activePanel,
  workflowIsOnline,
}: UseWorkflowScheduleOptions) {
  const [scheduleSection, setScheduleSection] = useState<ScheduleSection>("strategy");
  const [scheduleCollapsed, setScheduleCollapsed] = useState<Record<ScheduleSection, boolean>>({
    strategy: false,
    time: false,
  });
  const [scheduleForm, setScheduleForm] = useState<WorkflowScheduleForm>(defaultScheduleForm);
  const [scheduleSaving, setScheduleSaving] = useState(false);
  const [scheduleToggling, setScheduleToggling] = useState(false);
  const [schedulePreviewing, setSchedulePreviewing] = useState(false);
  const [previewTimes, setPreviewTimes] = useState<string[]>([]);

  const scheduleQuery = useQuery({
    queryKey: ["workflow-management", "schedule", workspaceId, workflowCode],
    queryFn: ({ signal }) => getWorkflowSchedule(workflowCode, signal),
    enabled: Boolean(workspaceId && activePanel === "schedule"),
    refetchOnWindowFocus: false,
  });

  useEffect(() => {
    if (scheduleQuery.data) {
      setScheduleForm(scheduleRecordToForm(scheduleQuery.data));
    } else if (scheduleQuery.isSuccess) {
      setScheduleForm(defaultScheduleForm());
    }
  }, [scheduleQuery.data, scheduleQuery.isSuccess]);

  const alertGroupsQuery = useQuery({
    queryKey: ["workflow-management", "alert-groups"],
    queryFn: () => listAlertGroups(),
    enabled: Boolean(
      workspaceId &&
        activePanel === "schedule" &&
        scheduleForm.warningType !== "NONE",
    ),
  });

  const updateScheduleField = <K extends keyof WorkflowScheduleForm>(
    key: K,
    value: WorkflowScheduleForm[K],
  ) => {
    setScheduleForm((current) => ({ ...current, [key]: value }));
    setPreviewTimes([]);
  };

  const currentSchedule = scheduleQuery.data;
  const scheduleIsOnline = currentSchedule?.releaseState === "ONLINE";
  const alertGroups = alertGroupsQuery.data ?? [];
  const configuredNotificationGroupExists = alertGroups.some(
    (group) => group.id === scheduleForm.warningGroupId,
  );
  const notificationGroupId = configuredNotificationGroupExists
    ? scheduleForm.warningGroupId
    : alertGroups[0]?.id ?? scheduleForm.warningGroupId;
  const notificationGroupMissing =
    scheduleForm.warningType !== "NONE" &&
    alertGroupsQuery.isSuccess &&
    alertGroups.length === 0;

  const buildScheduleOptions = (): WorkflowScheduleOptions => ({
    warningType: scheduleForm.warningType,
    warningGroupId: notificationGroupId,
    failureStrategy: scheduleForm.failureStrategy,
    workflowInstancePriority: scheduleForm.workflowInstancePriority,
    workerGroup: scheduleForm.workerGroup.trim() || "default",
    tenantCode: scheduleForm.tenantCode.trim() || "default",
    environmentCode: -1,
  });

  const validateScheduleForm = (validateNotification = true): boolean => {
    if (!scheduleForm.crontab.trim()) {
      toast.error("请配置 Cron 表达式");
      return false;
    }
    if (!scheduleForm.startTime || !scheduleForm.endTime) {
      toast.error("请填写调度生效时间");
      return false;
    }
    if (scheduleForm.endTime <= scheduleForm.startTime) {
      toast.error("调度结束时间必须晚于开始时间");
      return false;
    }
    if (validateNotification && notificationGroupMissing) {
      toast.error("当前没有可用的通知组，请先创建通知组");
      return false;
    }
    if (
      validateNotification &&
      scheduleForm.warningType !== "NONE" &&
      (alertGroupsQuery.isPending ||
        alertGroupsQuery.isFetching ||
        alertGroupsQuery.isError)
    ) {
      toast.error("通知组尚未加载完成，请重试后再保存");
      return false;
    }
    return true;
  };

  const saveSchedule = async () => {
    if (scheduleIsOnline) {
      toast.error("请先下线调度，再修改配置");
      return;
    }
    if (!currentSchedule && !workflowIsOnline) {
      toast.error("请先发布工作流，再创建调度");
      return;
    }
    if (!validateScheduleForm()) return;

    setScheduleSaving(true);
    try {
      const scheduleParam = toScheduleParam(scheduleForm);
      const options = buildScheduleOptions();
      if (currentSchedule) {
        await updateWorkflowSchedule(currentSchedule.id, scheduleParam, options);
      } else {
        await createWorkflowSchedule(workflowCode, scheduleParam, options);
      }
      await scheduleQuery.refetch();
      toast.success(currentSchedule ? "调度配置已更新" : "工作流调度已创建");
    } catch (error) {
      toast.error("保存调度配置失败", {
        description: error instanceof Error ? error.message : "请稍后重试",
      });
    } finally {
      setScheduleSaving(false);
    }
  };

  const previewSchedule = async () => {
    if (!validateScheduleForm(false)) return;
    setSchedulePreviewing(true);
    try {
      const result = await previewWorkflowSchedule(toScheduleParam(scheduleForm));
      setPreviewTimes(result ?? []);
    } catch (error) {
      toast.error("预览调度时间失败", {
        description: error instanceof Error ? error.message : "请检查 Cron 和生效时间",
      });
    } finally {
      setSchedulePreviewing(false);
    }
  };

  const toggleSchedule = async () => {
    if (!currentSchedule) return;
    if (!scheduleIsOnline && !workflowIsOnline) {
      toast.error("请先发布工作流，再上线调度");
      return;
    }

    setScheduleToggling(true);
    try {
      if (scheduleIsOnline) {
        await offlineWorkflowSchedule(currentSchedule.id);
        toast.success("调度已下线");
      } else {
        await publishWorkflowSchedule(currentSchedule.id);
        toast.success("调度已上线");
      }
      await scheduleQuery.refetch();
    } catch (error) {
      toast.error(scheduleIsOnline ? "下线调度失败" : "上线调度失败", {
        description: error instanceof Error ? error.message : "请稍后重试",
      });
    } finally {
      setScheduleToggling(false);
    }
  };

  return {
    scheduleSection,
    setScheduleSection,
    scheduleCollapsed,
    setScheduleCollapsed,
    scheduleForm,
    setScheduleForm,
    scheduleSaving,
    scheduleToggling,
    schedulePreviewing,
    previewTimes,
    scheduleQuery,
    alertGroupsQuery,
    currentSchedule,
    scheduleIsOnline,
    alertGroups,
    notificationGroupId,
    notificationGroupMissing,
    updateScheduleField,
    saveSchedule,
    previewSchedule,
    toggleSchedule,
  };
}

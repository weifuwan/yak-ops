import { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "@yak-ops/yak-ui";

import { listAlertGroups } from "@/service/alert";
import type { WorkflowDagRecord } from "@/service/workflow/definition";
import { updateWorkflowDefinitionSettings } from "@/service/workflow/definition";
import { useWorkspace } from "@/hooks/use-workspace";
import {
  createWorkflowSchedule,
  getWorkflowInstanceDetail,
  getWorkflowInstanceTasks,
  getWorkflowSchedule,
  listWorkflowDefinitionVersions,
  listWorkflowInstances,
  offlineWorkflowSchedule,
  previewWorkflowSchedule,
  publishWorkflowSchedule,
  switchWorkflowDefinitionVersion,
  updateWorkflowSchedule,
  type WorkflowScheduleOptions,
} from "@/service/workflow/management";

import { useDataDevelopmentWorkbench } from "../../../../workbench-context";
import {
  defaultScheduleForm,
  parseGlobalParameters,
  scheduleRecordToForm,
  toScheduleParam,
} from "../shared/utils";
import type {
  ExecutionType,
  GlobalParameter,
  PanelKey,
  ScheduleSection,
  WorkflowPropertyForm,
  WorkflowScheduleForm,
} from "../shared/types";

interface UseWorkflowSidePanelControllerOptions {
  workflowCode: number;
  dag: WorkflowDagRecord;
  activePanel: PanelKey | null;
  onVersionSwitched: () => void;
}

export function useWorkflowSidePanelController({
  workflowCode,
  dag,
  activePanel,
  onVersionSwitched,
}: UseWorkflowSidePanelControllerOptions) {
  const [scheduleSection, setScheduleSection] = useState<ScheduleSection>("params");
  const [scheduleCollapsed, setScheduleCollapsed] = useState<Record<ScheduleSection, boolean>>({
    params: false,
    strategy: false,
    time: false,
  });
  const [propertyForm, setPropertyForm] = useState<WorkflowPropertyForm>({
    name: dag.workflowDefinition.name,
    description: dag.workflowDefinition.description ?? "",
    timeout: String(dag.workflowDefinition.timeout ?? 0),
    executionType: (dag.workflowDefinition.executionType ?? "PARALLEL") as ExecutionType,
  });
  const [globalParameters, setGlobalParameters] = useState<GlobalParameter[]>(
    () => parseGlobalParameters(dag.workflowDefinition.globalParams),
  );
  const [savedGlobalParameterCount, setSavedGlobalParameterCount] = useState(
    () => parseGlobalParameters(dag.workflowDefinition.globalParams).length,
  );
  const [propertySaving, setPropertySaving] = useState(false);
  const [globalParametersSaving, setGlobalParametersSaving] = useState(false);
  const [scheduleForm, setScheduleForm] = useState<WorkflowScheduleForm>(defaultScheduleForm);
  const [scheduleSaving, setScheduleSaving] = useState(false);
  const [scheduleToggling, setScheduleToggling] = useState(false);
  const [schedulePreviewing, setSchedulePreviewing] = useState(false);
  const [previewTimes, setPreviewTimes] = useState<string[]>([]);
  const [selectedInstanceId, setSelectedInstanceId] = useState<number | null>(null);
  const [confirmVersion, setConfirmVersion] = useState<number | null>(null);
  const [switchingVersion, setSwitchingVersion] = useState(false);

  const queryClient = useQueryClient();
  const { currentWorkspace } = useWorkspace();
  const workspaceId = currentWorkspace?.id ?? "";
  const { updateTabTitle } = useDataDevelopmentWorkbench();
  const definition = dag.workflowDefinition;
  const workflowIsOnline = String(definition.releaseState).toUpperCase() === "ONLINE";

  useEffect(() => {
    setPropertyForm({
      name: definition.name,
      description: definition.description ?? "",
      timeout: String(definition.timeout ?? 0),
      executionType: (definition.executionType ?? "PARALLEL") as ExecutionType,
    });
    const nextParameters = parseGlobalParameters(definition.globalParams);
    setGlobalParameters(nextParameters);
    setSavedGlobalParameterCount(nextParameters.length);
  }, [
    workflowCode,
    definition.version,
    definition.name,
    definition.description,
    definition.timeout,
    definition.executionType,
    definition.globalParams,
  ]);

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

  const versionsQuery = useQuery({
    queryKey: ["workflow-management", "versions", workspaceId, workflowCode],
    queryFn: ({ signal }) => listWorkflowDefinitionVersions(workflowCode, signal),
    enabled: Boolean(workspaceId && activePanel === "versions"),
  });

  const instancesQuery = useQuery({
    queryKey: ["workflow-management", "instances", workspaceId, workflowCode],
    queryFn: ({ signal }) => listWorkflowInstances(workflowCode, signal),
    enabled: Boolean(
      workspaceId && (activePanel === "history" || activePanel === "ops"),
    ),
  });

  const selectedInstanceQuery = useQuery({
    queryKey: [
      "workflow-management",
      "instance-detail",
      workspaceId,
      selectedInstanceId,
    ],
    queryFn: ({ signal }) => getWorkflowInstanceDetail(selectedInstanceId!, signal),
    enabled: Boolean(
      workspaceId &&
        selectedInstanceId !== null &&
        (activePanel === "history" || activePanel === "ops"),
    ),
  });

  const instanceTasksQuery = useQuery({
    queryKey: [
      "workflow-management",
      "instance-tasks",
      workspaceId,
      selectedInstanceId,
    ],
    queryFn: ({ signal }) => getWorkflowInstanceTasks(selectedInstanceId!, signal),
    enabled: Boolean(
      workspaceId &&
        selectedInstanceId !== null &&
        (activePanel === "history" || activePanel === "ops"),
    ),
  });

  const alertGroupsQuery = useQuery({
    queryKey: ["workflow-management", "alert-groups"],
    queryFn: () => listAlertGroups(),
    enabled: Boolean(
      workspaceId &&
        activePanel === "schedule" &&
        scheduleForm.warningType !== "NONE",
    ),
  });

  const invalidateDefinition = async () => {
    await queryClient.invalidateQueries({
      queryKey: ["data-development", "workflow-definition", workspaceId, workflowCode],
    });
    await queryClient.invalidateQueries({
      queryKey: ["data-development", "workflow-explorer"],
    });
    await queryClient.invalidateQueries({
      queryKey: ["workflow-management", "versions", workspaceId, workflowCode],
    });
  };

  const saveProperties = async () => {
    const name = propertyForm.name.trim();
    const timeout = Number(propertyForm.timeout);
    if (!name) {
      toast.error("工作流名称不能为空");
      return;
    }
    if (!Number.isInteger(timeout) || timeout < 0) {
      toast.error("超时时间必须是大于或等于 0 的整数");
      return;
    }

    setPropertySaving(true);
    try {
      await updateWorkflowDefinitionSettings(workflowCode, {
        name,
        description: propertyForm.description,
        timeout,
        executionType: propertyForm.executionType,
      });
      updateTabTitle("workflow:" + workflowCode, name);
      await invalidateDefinition();
      toast.success("工作流属性已保存");
    } catch (error) {
      toast.error("保存工作流属性失败", {
        description: error instanceof Error ? error.message : "请稍后重试",
      });
    } finally {
      setPropertySaving(false);
    }
  };

  const saveGlobalParameters = async () => {
    if (savedGlobalParameterCount > 0 && globalParameters.length === 0) {
      toast.error("当前后端暂不支持清空全部全局参数");
      return;
    }
    const keys = globalParameters.map((item) => item.prop.trim());
    const originalSensitiveKeys = new Set(
      parseGlobalParameters(definition.globalParams)
        .filter((item) => item.sensitive && item.value === "******")
        .map((item) => item.prop),
    );
    if (
      globalParameters.some(
        (item) =>
          item.sensitive &&
          item.value === "******" &&
          !originalSensitiveKeys.has(item.prop.trim()),
      )
    ) {
      toast.error("新增或重命名敏感参数时，请重新填写实际参数值");
      return;
    }
    if (keys.some((key) => !key)) {
      toast.error("请填写所有全局参数名称");
      return;
    }
    if (new Set(keys).size !== keys.length) {
      toast.error("全局参数名称不能重复");
      return;
    }

    setGlobalParametersSaving(true);
    try {
      await updateWorkflowDefinitionSettings(workflowCode, {
        globalParams: JSON.stringify(
          globalParameters.map((item) => ({ ...item, prop: item.prop.trim() })),
        ),
      });
      setSavedGlobalParameterCount(globalParameters.length);
      await invalidateDefinition();
      toast.success("全局参数已保存");
    } catch (error) {
      toast.error("保存全局参数失败", {
        description: error instanceof Error ? error.message : "请稍后重试",
      });
    } finally {
      setGlobalParametersSaving(false);
    }
  };

  const updateGlobalParameter = (
    index: number,
    patch: Partial<GlobalParameter>,
  ) => {
    setGlobalParameters((current) =>
      current.map((item, itemIndex) =>
        itemIndex === index ? { ...item, ...patch } : item,
      ),
    );
  };

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

  const switchVersion = async (version: number) => {
    setSwitchingVersion(true);
    try {
      await switchWorkflowDefinitionVersion(workflowCode, version);
      setConfirmVersion(null);
      await invalidateDefinition();
      await queryClient.invalidateQueries({
        queryKey: ["workflow-management", "schedule", workspaceId, workflowCode],
      });
      await versionsQuery.refetch();
      onVersionSwitched();
      toast.success("已切换到版本 V" + version);
    } catch (error) {
      toast.error("切换版本失败", {
        description: error instanceof Error ? error.message : "请稍后重试",
      });
    } finally {
      setSwitchingVersion(false);
    }
  };

  const instances = instancesQuery.data?.totalList ?? [];
  const versions = versionsQuery.data?.totalList ?? [];
  const selectedInstance = instances.find((item) => item.id === selectedInstanceId);
  const tasks = instanceTasksQuery.data?.taskList ?? [];
  const activeInstanceCount = instances.filter((item) =>
    ["SUBMITTED_SUCCESS", "RUNNING_EXECUTION", "READY_PAUSE", "READY_STOP", "SERIAL_WAIT"].includes(
      item.state,
    ),
  ).length;
  const failedInstanceCount = instances.filter((item) => item.state === "FAILURE").length;
  const successInstanceCount = instances.filter((item) => item.state === "SUCCESS").length;


  const refreshActivePanel = async () => {
    if (activePanel === "properties") {
      setPropertyForm({
        name: definition.name,
        description: definition.description ?? "",
        timeout: String(definition.timeout ?? 0),
        executionType: (definition.executionType ?? "PARALLEL") as ExecutionType,
      });
      await invalidateDefinition();
      return;
    }
    if (activePanel === "schedule") {
      await scheduleQuery.refetch();
      return;
    }
    if (activePanel === "history" || activePanel === "ops") {
      await instancesQuery.refetch();
      return;
    }
    if (activePanel === "versions") {
      await versionsQuery.refetch();
    }
  };


  return {
    workflowCode,
    definition,
    workflowIsOnline,
    scheduleSection,
    setScheduleSection,
    scheduleCollapsed,
    setScheduleCollapsed,
    propertyForm,
    setPropertyForm,
    globalParameters,
    setGlobalParameters,
    savedGlobalParameterCount,
    propertySaving,
    globalParametersSaving,
    scheduleForm,
    setScheduleForm,
    scheduleSaving,
    scheduleToggling,
    schedulePreviewing,
    previewTimes,
    selectedInstanceId,
    setSelectedInstanceId,
    confirmVersion,
    setConfirmVersion,
    switchingVersion,
    scheduleQuery,
    versionsQuery,
    instancesQuery,
    selectedInstanceQuery,
    instanceTasksQuery,
    alertGroupsQuery,
    saveProperties,
    saveGlobalParameters,
    updateGlobalParameter,
    updateScheduleField,
    currentSchedule,
    scheduleIsOnline,
    alertGroups,
    notificationGroupId,
    notificationGroupMissing,
    saveSchedule,
    previewSchedule,
    toggleSchedule,
    switchVersion,
    instances,
    versions,
    selectedInstance,
    tasks,
    activeInstanceCount,
    failedInstanceCount,
    successInstanceCount,
    refreshActivePanel,
  };
}

export type WorkflowSidePanelController = ReturnType<
  typeof useWorkflowSidePanelController
>;

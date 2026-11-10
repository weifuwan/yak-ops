import { useEffect, useState, type ChangeEvent, type ReactNode } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  AlertCircle,
  Clock3,
  Plus,
  RefreshCw,
  Save,
  Trash2,
  X,
} from "lucide-react";
import { CronSchedulerPicker, toast } from "@yak-ops/yak-ui";

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
  type WorkflowDefinitionVersionRecord,
  type WorkflowFailureStrategy,
  type WorkflowInstanceSummary,
  type WorkflowMissedFirePolicy,
  type WorkflowPriority,
  type WorkflowScheduleOptions,
  type WorkflowScheduleParam,
  type WorkflowScheduleRecord,
  type WorkflowWarningType,
} from "@/service/workflow/management";

import { useDataDevelopmentWorkbench } from "../../workbench-context";

type PanelKey = "properties" | "schedule" | "history" | "versions" | "ops";
type ScheduleSection = "params" | "strategy" | "time";
type ExecutionType = "PARALLEL" | "SERIAL_WAIT" | "SERIAL_DISCARD" | "SERIAL_PRIORITY";
type GlobalParameterType =
  | "VARCHAR"
  | "INTEGER"
  | "LONG"
  | "FLOAT"
  | "DOUBLE"
  | "DATE"
  | "TIME"
  | "TIMESTAMP"
  | "BOOLEAN"
  | "LIST"
  | "FILE";

interface GlobalParameter {
  prop: string;
  direct: "IN" | "OUT";
  type: GlobalParameterType;
  value: string;
  sensitive: boolean;
}

interface WorkflowPropertyForm {
  name: string;
  description: string;
  timeout: string;
  executionType: ExecutionType;
}

interface WorkflowScheduleForm {
  startTime: string;
  endTime: string;
  timezoneId: string;
  crontab: string;
  missedFirePolicy: WorkflowMissedFirePolicy;
  failureStrategy: WorkflowFailureStrategy;
  warningType: WorkflowWarningType;
  warningGroupId: number;
  workflowInstancePriority: WorkflowPriority;
  workerGroup: string;
  tenantCode: string;
}

const CONTROL_CLASS =
  "h-8 w-full min-w-0 rounded-md border border-[#dfe3e8] bg-white px-2 text-xs text-[#252a32] outline-none transition-colors placeholder:text-[#a0a7b2] focus:border-[#8ba9ff] disabled:cursor-not-allowed disabled:bg-[#f7f8fa] disabled:text-[#9ca3af]";
const TEXTAREA_CLASS =
  "w-full min-w-0 resize-y rounded-md border border-[#dfe3e8] bg-white px-2 py-2 text-xs text-[#252a32] outline-none transition-colors placeholder:text-[#a0a7b2] focus:border-[#8ba9ff] disabled:cursor-not-allowed disabled:bg-[#f7f8fa] disabled:text-[#9ca3af]";
const PROPERTY_CONTROL_CLASS = CONTROL_CLASS.replace("h-8", "h-7");
const PROPERTY_TEXTAREA_CLASS = TEXTAREA_CLASS.replace("py-2", "py-1.5") + " min-h-[48px]";
const LABEL_CLASS = "mb-1.5 block text-xs font-medium text-[#596273]";
const MUTED_CLASS = "text-[11px] leading-5 text-[#8b93a1]";

const GLOBAL_PARAMETER_TYPES: GlobalParameterType[] = [
  "VARCHAR",
  "INTEGER",
  "LONG",
  "FLOAT",
  "DOUBLE",
  "DATE",
  "TIME",
  "TIMESTAMP",
  "BOOLEAN",
  "LIST",
  "FILE",
];

const EXECUTION_TYPES: Array<{ value: ExecutionType; label: string }> = [
  { value: "PARALLEL", label: "并行执行" },
  { value: "SERIAL_WAIT", label: "串行等待" },
  { value: "SERIAL_DISCARD", label: "串行丢弃" },
  { value: "SERIAL_PRIORITY", label: "串行优先" },
];

const WORKFLOW_STATES: Record<string, string> = {
  SUBMITTED_SUCCESS: "启动中",
  RUNNING_EXECUTION: "运行中",
  READY_PAUSE: "暂停中",
  PAUSE: "已暂停",
  READY_STOP: "停止中",
  STOP: "已停止",
  FAILURE: "运行失败",
  SUCCESS: "已完成",
  SERIAL_WAIT: "排队中",
  FAILOVER: "恢复中",
};

const TASK_STATES: Record<string, string> = {
  SUBMITTED_SUCCESS: "已提交",
  RUNNING_EXECUTION: "运行中",
  DELAY_EXECUTION: "等待执行",
  READY_PAUSE: "暂停中",
  PAUSE: "已暂停",
  READY_STOP: "停止中",
  STOP: "已停止",
  FAILURE: "运行失败",
  SUCCESS: "已完成",
  NEED_FAULT_TOLERANCE: "容错中",
  KILL: "已终止",
  WAITING_THREAD: "等待线程",
};

const WARNING_TYPES: Array<{ value: WorkflowWarningType; label: string }> = [
  { value: "NONE", label: "不通知" },
  { value: "FAILURE", label: "仅失败时通知" },
  { value: "SUCCESS", label: "仅成功时通知" },
  { value: "ALL", label: "成功或失败都通知" },
];

const FAILURE_STRATEGIES: Array<{ value: WorkflowFailureStrategy; label: string }> = [
  { value: "END", label: "结束工作流" },
  { value: "CONTINUE", label: "继续执行" },
];

const PRIORITIES: Array<{ value: WorkflowPriority; label: string }> = [
  { value: "HIGHEST", label: "最高" },
  { value: "HIGH", label: "高" },
  { value: "MEDIUM", label: "中" },
  { value: "LOW", label: "低" },
  { value: "LOWEST", label: "最低" },
];

const MISSED_FIRE_POLICIES: Array<{ value: WorkflowMissedFirePolicy; label: string }> = [
  { value: "SKIP_MISSED", label: "跳过错过的调度" },
  { value: "FIRE_ONCE_NOW", label: "立即补触发一次" },
  { value: "FIRE_ALL_MISSED", label: "补触发所有错过的调度" },
];

const PANEL_ITEMS: Array<{
  key: PanelKey;
  label: string;
}> = [
  { key: "properties", label: "属性" },
  { key: "schedule", label: "调度配置" },
  { key: "history", label: "运行历史" },
  { key: "versions", label: "版本" },
  { key: "ops", label: "运维" },
];

function parseGlobalParameters(value?: string | null): GlobalParameter[] {
  if (!value) return [];
  try {
    const parsed: unknown = JSON.parse(value);
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((item) => item && typeof item === "object")
      .map((item) => {
        const record = item as Partial<GlobalParameter>;
        return {
          prop: String(record.prop ?? ""),
          direct: record.direct === "OUT" ? "OUT" : "IN",
          type: GLOBAL_PARAMETER_TYPES.includes(record.type as GlobalParameterType)
            ? (record.type as GlobalParameterType)
            : "VARCHAR",
          value: String(record.value ?? ""),
          sensitive: Boolean(record.sensitive),
        };
      });
  } catch {
    return [];
  }
}

function formatDateTimeInput(value?: string | null): string {
  if (!value) return "";
  const matched = /^(\d{4}-\d{2}-\d{2})[ T](\d{2}:\d{2})/.exec(value);
  if (matched) return matched[1] + "T" + matched[2];

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (part: number) => String(part).padStart(2, "0");
  return (
    date.getFullYear() +
    "-" +
    pad(date.getMonth() + 1) +
    "-" +
    pad(date.getDate()) +
    "T" +
    pad(date.getHours()) +
    ":" +
    pad(date.getMinutes())
  );
}

function toApiDateTime(value: string): string {
  return value.replace("T", " ") + (value.length === 16 ? ":00" : "");
}

function defaultDateTimeInput(): string {
  const date = new Date();
  const pad = (part: number) => String(part).padStart(2, "0");
  return (
    date.getFullYear() +
    "-" +
    pad(date.getMonth() + 1) +
    "-" +
    pad(date.getDate()) +
    "T" +
    pad(date.getHours()) +
    ":" +
    pad(date.getMinutes())
  );
}

function defaultScheduleForm(): WorkflowScheduleForm {
  return {
    startTime: defaultDateTimeInput(),
    endTime: "2099-12-31T23:59",
    timezoneId: "Asia/Shanghai",
    crontab: "",
    missedFirePolicy: "FIRE_ALL_MISSED",
    failureStrategy: "CONTINUE",
    warningType: "NONE",
    warningGroupId: 1,
    workflowInstancePriority: "MEDIUM",
    workerGroup: "default",
    tenantCode: "default",
  };
}

function scheduleRecordToForm(record: WorkflowScheduleRecord): WorkflowScheduleForm {
  return {
    startTime: formatDateTimeInput(record.startTime),
    endTime: formatDateTimeInput(record.endTime),
    timezoneId: record.timezoneId || "Asia/Shanghai",
    crontab: record.crontab || "",
    missedFirePolicy: record.missedFirePolicy || "FIRE_ALL_MISSED",
    failureStrategy: record.failureStrategy || "END",
    warningType: record.warningType || "NONE",
    warningGroupId: record.warningGroupId || 1,
    workflowInstancePriority: record.workflowInstancePriority || "MEDIUM",
    workerGroup: record.workerGroup || "default",
    tenantCode: record.tenantCode || "default",
  };
}

function toScheduleParam(form: WorkflowScheduleForm): WorkflowScheduleParam {
  return {
    startTime: toApiDateTime(form.startTime),
    endTime: toApiDateTime(form.endTime),
    timezoneId: form.timezoneId,
    crontab: form.crontab.trim(),
    missedFirePolicy: form.missedFirePolicy,
  };
}

function formatTime(value?: string | null): string {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString("zh-CN", { hour12: false });
}

function statusLabel(state?: string | null): string {
  if (!state) return "未知";
  return WORKFLOW_STATES[state] ?? TASK_STATES[state] ?? state;
}

function statusClass(state?: string | null): string {
  if (state === "SUCCESS") return "bg-[#edf8f1] text-[#25834b]";
  if (state === "FAILURE" || state === "KILL") return "bg-[#fff0ef] text-[#cf4a43]";
  if (
    state === "RUNNING_EXECUTION" ||
    state === "SUBMITTED_SUCCESS" ||
    state === "SERIAL_WAIT" ||
    state === "READY_PAUSE" ||
    state === "READY_STOP"
  ) {
    return "bg-[#edf3ff] text-[#3568c8]";
  }
  return "bg-[#f1f3f5] text-[#687282]";
}

function PanelNotice({
  children,
  tone = "info",
}: {
  children: ReactNode;
  tone?: "info" | "warning";
}) {
  return (
    <div
      className={[
        "flex items-start gap-2 rounded-md border px-3 py-2.5 text-xs leading-5",
        tone === "warning"
          ? "border-[#f2dfbd] bg-[#fffaf0] text-[#8a6424]"
          : "border-[#e3eaf7] bg-[#f6f9ff] text-[#63738e]",
      ].join(" ")}
    >
      <AlertCircle size={14} className="mt-0.5 shrink-0" />
      <div>{children}</div>
    </div>
  );
}

function PanelButton({
  children,
  onClick,
  disabled = false,
  variant = "primary",
  title,
}: {
  children: React.ReactNode;
  onClick: () => void;
  disabled?: boolean;
  variant?: "primary" | "secondary" | "danger";
  title?: string;
}) {
  const style =
    variant === "primary"
      ? "border-[#315efb] bg-[#315efb] text-white hover:bg-[#244fe0]"
      : variant === "danger"
        ? "border-[#f0d5d3] bg-white text-[#c64a43] hover:bg-[#fff5f4]"
        : "border-[#dfe3e8] bg-white text-[#465064] hover:bg-[#f7f8fa]";
  return (
    <button
      type="button"
      title={title}
      disabled={disabled}
      onClick={onClick}
      className={[
        "inline-flex h-8 items-center justify-center gap-1.5 rounded-md border px-3 text-xs font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-45",
        style,
      ].join(" ")}
    >
      {children}
    </button>
  );
}

function EmptyState({ title, description }: { title: string; description?: string }) {
  return (
    <div className="flex min-h-[150px] flex-col items-center justify-center gap-1.5 rounded-lg border border-dashed border-[#e3e7ed] bg-[#fbfcfd] px-4 text-center">
      <span className="text-xs font-medium text-[#606b7a]">{title}</span>
      {description ? <span className={MUTED_CLASS}>{description}</span> : null}
    </div>
  );
}

export function WorkflowSidePanel({
  workflowCode,
  dag,
  onVersionSwitched,
}: {
  workflowCode: number;
  dag: WorkflowDagRecord;
  onVersionSwitched: () => void;
}) {
  const [activePanel, setActivePanel] = useState<PanelKey | null>("properties");
  const [scheduleSection, setScheduleSection] = useState<ScheduleSection>("params");
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

  const renderInstanceDetail = () => {
    if (selectedInstanceId === null) return null;
    const detail = selectedInstanceQuery.data;
    return (
      <section className="mt-3 rounded-lg border border-[#e5e9ef] bg-[#fbfcfe] p-3">
        <div className="mb-2 flex items-center justify-between gap-2">
          <div className="min-w-0">
            <div className="text-xs font-semibold text-[#303846]">
              实例 #{selectedInstanceId}
            </div>
            <div className={MUTED_CLASS}>
              {detail?.name || selectedInstance?.name || "工作流实例"}
            </div>
          </div>
          <button
            type="button"
            title="关闭实例详情"
            onClick={() => setSelectedInstanceId(null)}
            className="flex size-6 shrink-0 items-center justify-center rounded text-[#8992a0] hover:bg-[#eef1f5] hover:text-[#303846]"
          >
            <X size={14} />
          </button>
        </div>
        {selectedInstanceQuery.isPending ? (
          <div className={MUTED_CLASS}>正在加载实例详情...</div>
        ) : (
          <div className="grid grid-cols-2 gap-x-3 gap-y-2">
            <div>
              <div className={MUTED_CLASS}>运行状态</div>
              <span className={"mt-1 inline-flex rounded px-2 py-0.5 text-[11px] " + statusClass(detail?.state)}>
                {statusLabel(detail?.state)}
              </span>
            </div>
            <div>
              <div className={MUTED_CLASS}>执行人</div>
              <div className="mt-1 truncate text-xs text-[#4c5666]">
                {detail?.executorName || "-"}
              </div>
            </div>
            <div>
              <div className={MUTED_CLASS}>开始时间</div>
              <div className="mt-1 text-xs text-[#4c5666]">
                {formatTime(detail?.startTime)}
              </div>
            </div>
            <div>
              <div className={MUTED_CLASS}>结束时间</div>
              <div className="mt-1 text-xs text-[#4c5666]">
                {formatTime(detail?.endTime)}
              </div>
            </div>
            <div>
              <div className={MUTED_CLASS}>执行方式</div>
              <div className="mt-1 text-xs text-[#4c5666]">
                {detail?.commandType || "-"}
              </div>
            </div>
            <div>
              <div className={MUTED_CLASS}>Worker Group</div>
              <div className="mt-1 truncate text-xs text-[#4c5666]">
                {detail?.workerGroup || "-"}
              </div>
            </div>
          </div>
        )}
        <div className="mt-3 border-t border-[#e8ebf0] pt-3">
          <div className="mb-2 flex items-center justify-between">
            <span className="text-xs font-semibold text-[#3b4554]">节点执行明细</span>
            <span className={MUTED_CLASS}>{tasks.length} 个节点</span>
          </div>
          {instanceTasksQuery.isPending ? (
            <div className={MUTED_CLASS}>正在加载节点...</div>
          ) : instanceTasksQuery.isError ? (
            <div className="flex items-center justify-between gap-2 text-xs text-[#b34d47]">
              <span>节点明细加载失败</span>
              <button
                type="button"
                onClick={() => void instanceTasksQuery.refetch()}
                className="text-[#315efb] hover:underline"
              >
                重试
              </button>
            </div>
          ) : tasks.length === 0 ? (
            <div className={MUTED_CLASS}>暂无节点执行记录</div>
          ) : (
            <div className="space-y-1.5">
              {tasks.map((task) => (
                <div
                  key={task.id}
                  className="flex items-center justify-between gap-2 rounded-md bg-white px-2.5 py-2"
                >
                  <div className="min-w-0">
                    <div className="truncate text-xs text-[#3d4654]">{task.name}</div>
                    <div className={MUTED_CLASS}>{task.taskType}</div>
                  </div>
                  <span className={"shrink-0 rounded px-2 py-0.5 text-[10px] " + statusClass(task.state)}>
                    {statusLabel(task.state)}
                  </span>
                </div>
              ))}
            </div>
          )}
        </div>
      </section>
    );
  };

  const renderProperties = () => (
    <div className="px-4 py-3">
      <div className="space-y-1">
        <div className="grid grid-cols-[84px_minmax(0,1fr)] items-center gap-3 py-2.5">
          <label htmlFor="workflow-property-name" className="text-xs text-[#687385]">
            名称
          </label>
          <input
            id="workflow-property-name"
            className={PROPERTY_CONTROL_CLASS}
            value={propertyForm.name}
            disabled={workflowIsOnline}
            maxLength={100}
            onChange={(event: ChangeEvent<HTMLInputElement>) =>
              setPropertyForm((current) => ({ ...current, name: event.target.value }))
            }
          />
        </div>

        <div className="grid grid-cols-[84px_minmax(0,1fr)] items-start gap-3 py-2.5">
          <label htmlFor="workflow-property-description" className="pt-2 text-xs text-[#687385]">
            描述
          </label>
          <textarea
            id="workflow-property-description"
            className={PROPERTY_TEXTAREA_CLASS}
            rows={2}
            maxLength={255}
            value={propertyForm.description}
            disabled={workflowIsOnline}
            onChange={(event: ChangeEvent<HTMLTextAreaElement>) =>
              setPropertyForm((current) => ({
                ...current,
                description: event.target.value,
              }))
            }
          />
        </div>

        <div className="grid grid-cols-[84px_minmax(0,1fr)] items-center gap-3 py-2.5">
          <label htmlFor="workflow-property-timeout" className="text-xs text-[#687385]">
            超时时间（分钟）
          </label>
          <input
            id="workflow-property-timeout"
            className={PROPERTY_CONTROL_CLASS}
            type="number"
            min={0}
            step={1}
            value={propertyForm.timeout}
            disabled={workflowIsOnline}
            onChange={(event: ChangeEvent<HTMLInputElement>) =>
              setPropertyForm((current) => ({ ...current, timeout: event.target.value }))
            }
          />
        </div>

        <div className="grid grid-cols-[84px_minmax(0,1fr)] items-center gap-3 py-2.5">
          <label htmlFor="workflow-property-execution-type" className="text-xs text-[#687385]">
            执行方式
          </label>
          <select
            id="workflow-property-execution-type"
            className={PROPERTY_CONTROL_CLASS}
            value={propertyForm.executionType}
            disabled={workflowIsOnline}
            onChange={(event: ChangeEvent<HTMLSelectElement>) =>
              setPropertyForm((current) => ({
                ...current,
                executionType: event.target.value as ExecutionType,
              }))
            }
          >
            {EXECUTION_TYPES.map((item) => (
              <option key={item.value} value={item.value}>
                {item.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="flex justify-end pt-4">
        <PanelButton
          onClick={() => void saveProperties()}
          disabled={workflowIsOnline || propertySaving}
        >
          <Save size={13} />
          {propertySaving ? "保存中..." : "保存属性"}
        </PanelButton>
      </div>
    </div>
  );

  const renderSchedulePanel = () => {
    if (scheduleQuery.isPending) {
      return <div className="p-4 text-xs text-[#8b93a1]">正在加载调度配置...</div>;
    }
    if (scheduleQuery.isError) {
      return (
        <div className="space-y-3 p-4">
          <PanelNotice tone="warning">调度配置加载失败，请检查工作空间和接口状态。</PanelNotice>
          <PanelButton onClick={() => void scheduleQuery.refetch()} variant="secondary">
            <RefreshCw size={13} />
            重新加载
          </PanelButton>
        </div>
      );
    }

    return (
      <div className="flex min-h-0 flex-1 flex-col">
        <div className="space-y-3 overflow-y-auto p-4">
          <div className="flex items-center justify-between gap-2">
            <div className="flex items-center gap-2">
              <span
                className={[
                  "size-2 rounded-full",
                  scheduleIsOnline ? "bg-[#2da866]" : "bg-[#aab1bc]",
                ].join(" ")}
              />
              <span className="text-xs font-medium text-[#475162]">
                {currentSchedule
                  ? scheduleIsOnline
                    ? "调度已上线"
                    : "调度已下线"
                  : "尚未配置调度"}
              </span>
            </div>
            <button
              type="button"
              onClick={() => void scheduleQuery.refetch()}
              className="inline-flex items-center gap-1 text-[11px] text-[#6d7787] hover:text-[#315efb]"
            >
              <RefreshCw size={12} />
              刷新
            </button>
          </div>
          {scheduleIsOnline ? (
            <PanelNotice tone="warning">
              调度上线期间不能修改调度参数。请先下线调度，修改并保存后再重新上线。
            </PanelNotice>
          ) : !currentSchedule ? (
            <PanelNotice tone={workflowIsOnline ? "info" : "warning"}>
              {workflowIsOnline
                ? "首次保存会创建工作流级调度，创建后默认为下线状态。"
                : "后端只允许为已发布的工作流创建调度，请先发布工作流。"}
            </PanelNotice>
          ) : null}
          <div className="flex items-center gap-1 border-b border-[#e8ebf0]">
            {[
              { key: "params" as const, label: "调度参数" },
              { key: "strategy" as const, label: "调度策略" },
              { key: "time" as const, label: "调度时间" },
            ].map((item) => (
              <button
                key={item.key}
                type="button"
                onClick={() => setScheduleSection(item.key)}
                className={[
                  "relative h-8 px-2 text-xs transition-colors",
                  scheduleSection === item.key
                    ? "font-semibold text-[#315efb]"
                    : "text-[#7b8492] hover:text-[#465064]",
                ].join(" ")}
              >
                {item.label}
                {scheduleSection === item.key ? (
                  <span className="absolute inset-x-2 bottom-0 h-0.5 rounded-full bg-[#315efb]" />
                ) : null}
              </button>
            ))}
          </div>

          {scheduleSection === "params" ? (
            <div className="space-y-3">
              <div className="text-xs font-semibold text-[#303846]">全局参数</div>
              <div className={MUTED_CLASS}>
                这里管理工作流定义级参数。参数会随工作流版本保存，不是单次运行的临时变量。
              </div>
              {workflowIsOnline ? (
                <PanelNotice tone="warning">
                  工作流上线期间不能修改全局参数，请先下线工作流。
                </PanelNotice>
              ) : null}
              {globalParameters.length === 0 ? (
                <EmptyState
                  title="暂无全局参数"
                  description="添加全局参数后，可在工作流运行时使用。"
                />
              ) : (
                <div className="space-y-2">
                  {globalParameters.map((parameter, index) => (
                    <div
                      key={index}
                      className="rounded-lg border border-[#e5e9ef] bg-white p-3"
                    >
                      <div className="mb-2 flex items-center justify-between">
                        <span className="text-xs font-medium text-[#3f4a5a]">
                          {parameter.prop || "未命名参数"}
                        </span>
                        <button
                          type="button"
                          title="删除参数"
                          disabled={
                            workflowIsOnline ||
                            (savedGlobalParameterCount > 0 && globalParameters.length <= 1)
                          }
                          onClick={() =>
                            setGlobalParameters((current) =>
                              current.filter((_item, itemIndex) => itemIndex !== index),
                            )
                          }
                          className="text-[#9aa2ad] hover:text-[#cf4a43] disabled:opacity-35"
                        >
                          <Trash2 size={13} />
                        </button>
                      </div>
                      <div className="grid grid-cols-2 gap-2">
                        <label>
                          <span className={LABEL_CLASS}>名称</span>
                          <input
                            className={CONTROL_CLASS}
                            value={parameter.prop}
                            disabled={workflowIsOnline}
                            onChange={(event: ChangeEvent<HTMLInputElement>) =>
                              updateGlobalParameter(index, { prop: event.target.value })
                            }
                          />
                        </label>
                        <label>
                          <span className={LABEL_CLASS}>类型</span>
                          <select
                            className={CONTROL_CLASS}
                            value={parameter.type}
                            disabled={workflowIsOnline}
                            onChange={(event: ChangeEvent<HTMLSelectElement>) =>
                              updateGlobalParameter(index, {
                                type: event.target.value as GlobalParameterType,
                              })
                            }
                          >
                            {GLOBAL_PARAMETER_TYPES.map((type) => (
                              <option key={type} value={type}>
                                {type}
                              </option>
                            ))}
                          </select>
                        </label>
                      </div>
                      <label className="mt-2 block">
                        <span className={LABEL_CLASS}>参数方向</span>
                        <select
                          className={CONTROL_CLASS}
                          value={parameter.direct}
                          disabled={workflowIsOnline}
                          onChange={(event: ChangeEvent<HTMLSelectElement>) =>
                            updateGlobalParameter(index, {
                              direct: event.target.value as "IN" | "OUT",
                            })
                          }
                        >
                          <option value="IN">输入（IN）</option>
                          <option value="OUT">输出（OUT）</option>
                        </select>
                      </label>
                      <label className="mt-2 block">
                        <span className={LABEL_CLASS}>参数值</span>
                        <input
                          className={CONTROL_CLASS}
                          type={parameter.sensitive ? "password" : "text"}
                          value={parameter.value}
                          disabled={workflowIsOnline}
                          onChange={(event: ChangeEvent<HTMLInputElement>) =>
                            updateGlobalParameter(index, { value: event.target.value })
                          }
                        />
                      </label>
                      <label className="mt-2 inline-flex items-center gap-2 text-xs text-[#7a8492]">
                        <input
                          type="checkbox"
                          checked={parameter.sensitive}
                          disabled={workflowIsOnline}
                          onChange={(event: ChangeEvent<HTMLInputElement>) => {
                            if (
                              !event.target.checked &&
                              parameter.sensitive &&
                              parameter.value === "******"
                            ) {
                              toast.error("敏感参数值已脱敏，请先重新填写实际值");
                              return;
                            }
                            updateGlobalParameter(index, { sensitive: event.target.checked });
                          }}
                        />
                        敏感参数
                      </label>
                    </div>
                  ))}
                </div>
              )}
              <div className="flex flex-wrap justify-between gap-2">
                <PanelButton
                  variant="secondary"
                  disabled={workflowIsOnline}
                  onClick={() =>
                    setGlobalParameters((current) => [
                      ...current,
                      {
                        prop: "PARAM_" + (current.length + 1),
                        direct: "IN",
                        type: "VARCHAR",
                        value: "",
                        sensitive: false,
                      },
                    ])
                  }
                >
                  <Plus size={13} />
                  添加参数
                </PanelButton>
                <PanelButton
                  onClick={() => void saveGlobalParameters()}
                  disabled={
                    workflowIsOnline ||
                    globalParametersSaving ||
                    (globalParameters.length === 0 && savedGlobalParameterCount === 0)
                  }
                >
                  <Save size={13} />
                  {globalParametersSaving ? "保存中..." : "保存参数"}
                </PanelButton>
              </div>
              {savedGlobalParameterCount > 0 && globalParameters.length === 1 ? (
                <div className={MUTED_CLASS}>
                  当前后端暂不支持清空全部已保存参数，请至少保留一项。
                </div>
              ) : null}
            </div>
          ) : null}

          {scheduleSection === "strategy" ? (
            <div className="space-y-3">
              <label className="block">
                <span className={LABEL_CLASS}>失败策略</span>
                <select
                  className={CONTROL_CLASS}
                  value={scheduleForm.failureStrategy}
                  disabled={scheduleIsOnline}
                  onChange={(event: ChangeEvent<HTMLSelectElement>) =>
                    updateScheduleField(
                      "failureStrategy",
                      event.target.value as WorkflowFailureStrategy,
                    )
                  }
                >
                  {FAILURE_STRATEGIES.map((item) => (
                    <option key={item.value} value={item.value}>
                      {item.label}
                    </option>
                  ))}
                </select>
              </label>
              <label className="block">
                <span className={LABEL_CLASS}>错过调度处理</span>
                <select
                  className={CONTROL_CLASS}
                  value={scheduleForm.missedFirePolicy}
                  disabled={scheduleIsOnline}
                  onChange={(event: ChangeEvent<HTMLSelectElement>) =>
                    updateScheduleField(
                      "missedFirePolicy",
                      event.target.value as WorkflowMissedFirePolicy,
                    )
                  }
                >
                  {MISSED_FIRE_POLICIES.map((item) => (
                    <option key={item.value} value={item.value}>
                      {item.label}
                    </option>
                  ))}
                </select>
              </label>
              <label className="block">
                <span className={LABEL_CLASS}>实例优先级</span>
                <select
                  className={CONTROL_CLASS}
                  value={scheduleForm.workflowInstancePriority}
                  disabled={scheduleIsOnline}
                  onChange={(event: ChangeEvent<HTMLSelectElement>) =>
                    updateScheduleField(
                      "workflowInstancePriority",
                      event.target.value as WorkflowPriority,
                    )
                  }
                >
                  {PRIORITIES.map((item) => (
                    <option key={item.value} value={item.value}>
                      {item.label}
                    </option>
                  ))}
                </select>
              </label>
              <label className="block">
                <span className={LABEL_CLASS}>通知策略</span>
                <select
                  className={CONTROL_CLASS}
                  value={scheduleForm.warningType}
                  disabled={scheduleIsOnline}
                  onChange={(event: ChangeEvent<HTMLSelectElement>) =>
                    updateScheduleField(
                      "warningType",
                      event.target.value as WorkflowWarningType,
                    )
                  }
                >
                  {WARNING_TYPES.map((item) => (
                    <option key={item.value} value={item.value}>
                      {item.label}
                    </option>
                  ))}
                </select>
              </label>
              {scheduleForm.warningType !== "NONE" ? (
                <label className="block">
                  <span className={LABEL_CLASS}>通知组</span>
                  <select
                    className={CONTROL_CLASS}
                    value={String(notificationGroupId)}
                    disabled={scheduleIsOnline || alertGroupsQuery.isPending}
                    onChange={(event: ChangeEvent<HTMLSelectElement>) =>
                      updateScheduleField("warningGroupId", Number(event.target.value))
                    }
                  >
                    {alertGroups.map((group) => (
                      <option key={group.id} value={String(group.id)}>
                        {group.groupName}
                      </option>
                    ))}
                  </select>
                  {alertGroupsQuery.isPending ? (
                    <span className={MUTED_CLASS}>正在加载通知组...</span>
                  ) : notificationGroupMissing ? (
                    <span className="mt-1 block text-[11px] text-[#c64a43]">
                      暂无通知组，请先在告警中心创建
                    </span>
                  ) : alertGroupsQuery.isError ? (
                    <button
                      type="button"
                      onClick={() => void alertGroupsQuery.refetch()}
                      className="mt-1 text-[11px] text-[#315efb] hover:underline"
                    >
                      通知组加载失败，点击重试
                    </button>
                  ) : null}
                </label>
              ) : null}
              <div className="grid grid-cols-2 gap-2">
                <label>
                  <span className={LABEL_CLASS}>Worker Group</span>
                  <input
                    className={CONTROL_CLASS}
                    value={scheduleForm.workerGroup}
                    disabled={scheduleIsOnline}
                    onChange={(event: ChangeEvent<HTMLInputElement>) =>
                      updateScheduleField("workerGroup", event.target.value)
                    }
                  />
                </label>
                <label>
                  <span className={LABEL_CLASS}>Tenant Code</span>
                  <input
                    className={CONTROL_CLASS}
                    value={scheduleForm.tenantCode}
                    disabled={scheduleIsOnline}
                    onChange={(event: ChangeEvent<HTMLInputElement>) =>
                      updateScheduleField("tenantCode", event.target.value)
                    }
                  />
                </label>
              </div>
              <div className={MUTED_CLASS}>
                失败策略、错过触发策略、优先级、通知组、Worker Group 和 Tenant Code 均由现有工作流调度接口保存。
              </div>
            </div>
          ) : null}

          {scheduleSection === "time" ? (
            <div className="space-y-3">
              <label className="block">
                <span className={LABEL_CLASS}>Cron 表达式</span>
                <CronSchedulerPicker
                  value={scheduleForm.crontab}
                  disabled={scheduleIsOnline}
                  allowClear
                  placeholder="点击配置 Cron"
                  size="small"
                  variant="outlined"
                  onValueChange={(value) => updateScheduleField("crontab", value)}
                />
              </label>
              <label className="block">
                <span className={LABEL_CLASS}>调度时区</span>
                <select
                  className={CONTROL_CLASS}
                  value={scheduleForm.timezoneId}
                  disabled={scheduleIsOnline}
                  onChange={(event: ChangeEvent<HTMLSelectElement>) =>
                    updateScheduleField("timezoneId", event.target.value)
                  }
                >
                  {Array.from(
                    new Set([
                      "Asia/Shanghai",
                      "Asia/Singapore",
                      "Asia/Hong_Kong",
                      "Asia/Tokyo",
                      "UTC",
                      "Europe/London",
                      "America/New_York",
                      scheduleForm.timezoneId,
                    ]),
                  ).map((zone) => (
                    <option key={zone} value={zone}>
                      {zone}
                    </option>
                  ))}
                </select>
              </label>
              <label className="block">
                <span className={LABEL_CLASS}>生效开始时间</span>
                <input
                  className={CONTROL_CLASS}
                  type="datetime-local"
                  value={scheduleForm.startTime}
                  disabled={scheduleIsOnline}
                  onChange={(event: ChangeEvent<HTMLInputElement>) =>
                    updateScheduleField("startTime", event.target.value)
                  }
                />
              </label>
              <label className="block">
                <span className={LABEL_CLASS}>生效结束时间</span>
                <input
                  className={CONTROL_CLASS}
                  type="datetime-local"
                  value={scheduleForm.endTime}
                  disabled={scheduleIsOnline}
                  onChange={(event: ChangeEvent<HTMLInputElement>) =>
                    updateScheduleField("endTime", event.target.value)
                  }
                />
              </label>
              {previewTimes.length > 0 ? (
                <div className="rounded-md border border-[#e5e9ef] bg-[#fbfcfe] p-3">
                  <div className="mb-2 text-xs font-semibold text-[#455064]">
                    接下来 {previewTimes.length} 次触发时间
                  </div>
                  <div className="space-y-1">
                    {previewTimes.map((time, index) => (
                      <div key={time + index} className="text-xs text-[#687384]">
                        {index + 1}. {time}
                      </div>
                    ))}
                  </div>
                </div>
              ) : null}
            </div>
          ) : null}
        </div>
        {scheduleSection !== "params" ? (
        <div className="shrink-0 border-t border-[#e8ebf0] bg-white p-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <PanelButton
              variant="secondary"
              onClick={() => void previewSchedule()}
              disabled={schedulePreviewing || !scheduleForm.crontab.trim()}
            >
              <Clock3 size={13} />
              预览
            </PanelButton>
            <div className="flex items-center gap-2">
              {currentSchedule ? (
                <PanelButton
                  variant={scheduleIsOnline ? "danger" : "secondary"}
                  onClick={() => void toggleSchedule()}
                  disabled={
                    scheduleToggling ||
                    (!scheduleIsOnline && !workflowIsOnline)
                  }
                  title={
                    !scheduleIsOnline && !workflowIsOnline
                      ? "请先发布工作流"
                      : undefined
                  }
                >
                  {scheduleToggling
                    ? "处理中..."
                    : scheduleIsOnline
                      ? "下线调度"
                      : "上线调度"}
                </PanelButton>
              ) : null}
              <PanelButton
                onClick={() => void saveSchedule()}
                disabled={
                  scheduleSaving ||
                  scheduleIsOnline ||
                  (!currentSchedule && !workflowIsOnline) ||
                  !scheduleForm.crontab.trim() ||
                  (scheduleForm.warningType !== "NONE" &&
                    (alertGroupsQuery.isPending ||
                      alertGroupsQuery.isFetching ||
                      alertGroupsQuery.isError ||
                      notificationGroupMissing))
                }
              >
                <Save size={13} />
                {scheduleSaving ? "保存中..." : "保存调度"}
              </PanelButton>
            </div>
          </div>
        </div>
        ) : null}
      </div>
    );
  };

  const renderInstanceList = (items: WorkflowInstanceSummary[]) => (
    <div className="space-y-2">
      {items.map((instance) => (
        <button
          key={instance.id}
          type="button"
          onClick={() => setSelectedInstanceId(instance.id)}
          className={[
            "w-full rounded-lg border p-3 text-left transition-colors",
            selectedInstanceId === instance.id
              ? "border-[#a9bcff] bg-[#f5f8ff]"
              : "border-[#e6eaf0] bg-white hover:border-[#cbd5e3] hover:bg-[#fbfcfd]",
          ].join(" ")}
        >
          <div className="flex items-center justify-between gap-2">
            <span className="min-w-0 truncate text-xs font-medium text-[#374151]">
              {instance.name || "工作流实例 #" + instance.id}
            </span>
            <span className={"shrink-0 rounded px-2 py-0.5 text-[10px] " + statusClass(instance.state)}>
              {statusLabel(instance.state)}
            </span>
          </div>
          <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1 text-[11px] text-[#8a93a1]">
            <span>ID: {instance.id}</span>
            <span>开始：{formatTime(instance.startTime)}</span>
            <span>耗时：{instance.duration || "-"}</span>
          </div>
        </button>
      ))}
    </div>
  );

  const renderHistory = () => (
    <div className="space-y-3 p-4">
      <div className="flex items-center justify-between gap-2">
        <div>
          <div className="text-xs font-semibold text-[#303846]">工作流运行记录</div>
          <div className={MUTED_CLASS}>最近 20 条实例记录</div>
        </div>
        <button
          type="button"
          onClick={() => void instancesQuery.refetch()}
          className="inline-flex items-center gap-1 text-[11px] text-[#6d7787] hover:text-[#315efb]"
        >
          <RefreshCw size={12} />
          刷新
        </button>
      </div>
      {instancesQuery.isPending ? (
        <div className={MUTED_CLASS}>正在加载运行记录...</div>
      ) : instancesQuery.isError ? (
        <div className="space-y-2">
          <PanelNotice tone="warning">运行记录加载失败。</PanelNotice>
          <PanelButton variant="secondary" onClick={() => void instancesQuery.refetch()}>
            <RefreshCw size={13} />
            重试
          </PanelButton>
        </div>
      ) : instances.length === 0 ? (
        <EmptyState title="暂无运行记录" description="工作流执行后，实例记录会显示在这里。" />
      ) : (
        renderInstanceList(instances)
      )}
      {renderInstanceDetail()}
    </div>
  );

  const renderOperations = () => {
    const attentionInstances = instances.filter((item) =>
      item.state === "FAILURE" ||
      ["SUBMITTED_SUCCESS", "RUNNING_EXECUTION", "READY_PAUSE", "READY_STOP", "SERIAL_WAIT"].includes(
        item.state,
      ),
    );
    return (
      <div className="space-y-3 p-4">
        <div className="flex items-center justify-between gap-2">
          <div>
            <div className="text-xs font-semibold text-[#303846]">运行运维概览</div>
            <div className={MUTED_CLASS}>统计范围：最近 20 条工作流实例</div>
          </div>
          <button
            type="button"
            onClick={() => void instancesQuery.refetch()}
            className="inline-flex items-center gap-1 text-[11px] text-[#6d7787] hover:text-[#315efb]"
          >
            <RefreshCw size={12} />
            刷新
          </button>
        </div>
        {instancesQuery.isPending ? (
          <div className={MUTED_CLASS}>正在加载实例...</div>
        ) : instancesQuery.isError ? (
          <PanelNotice tone="warning">实例信息加载失败，请重试。</PanelNotice>
        ) : (
          <>
            <div className="grid grid-cols-3 gap-2">
              <div className="rounded-lg border border-[#e4e9f0] bg-white p-2.5">
                <div className={MUTED_CLASS}>进行中</div>
                <div className="mt-1 text-xl font-semibold tabular-nums text-[#3568c8]">
                  {activeInstanceCount}
                </div>
              </div>
              <div className="rounded-lg border border-[#e4e9f0] bg-white p-2.5">
                <div className={MUTED_CLASS}>失败</div>
                <div className="mt-1 text-xl font-semibold tabular-nums text-[#cf4a43]">
                  {failedInstanceCount}
                </div>
              </div>
              <div className="rounded-lg border border-[#e4e9f0] bg-white p-2.5">
                <div className={MUTED_CLASS}>成功</div>
                <div className="mt-1 text-xl font-semibold tabular-nums text-[#25834b]">
                  {successInstanceCount}
                </div>
              </div>
            </div>
            <div className="text-xs font-semibold text-[#455064]">需要关注的实例</div>
            {attentionInstances.length > 0 ? (
              renderInstanceList(attentionInstances)
            ) : (
              <EmptyState title="暂无异常或运行中的实例" />
            )}
          </>
        )}
        {renderInstanceDetail()}
      </div>
    );
  };

  const renderVersions = () => (
    <div className="space-y-3 p-4">
      <div className="flex items-center justify-between gap-2">
        <div>
          <div className="text-xs font-semibold text-[#303846]">工作流版本</div>
          <div className={MUTED_CLASS}>当前版本 V{definition.version} · 最近 20 个版本</div>
        </div>
        <button
          type="button"
          onClick={() => void versionsQuery.refetch()}
          className="inline-flex items-center gap-1 text-[11px] text-[#6d7787] hover:text-[#315efb]"
        >
          <RefreshCw size={12} />
          刷新
        </button>
      </div>
      {workflowIsOnline ? (
        <PanelNotice tone="warning">
          工作流上线期间不能切换版本，请先下线工作流。
        </PanelNotice>
      ) : null}
      {versionsQuery.isPending ? (
        <div className={MUTED_CLASS}>正在加载版本...</div>
      ) : versionsQuery.isError ? (
        <div className="space-y-2">
          <PanelNotice tone="warning">版本列表加载失败。</PanelNotice>
          <PanelButton variant="secondary" onClick={() => void versionsQuery.refetch()}>
            <RefreshCw size={13} />
            重试
          </PanelButton>
        </div>
      ) : versions.length === 0 ? (
        <EmptyState title="暂无历史版本" />
      ) : (
        <div className="space-y-2">
          {versions.map((version: WorkflowDefinitionVersionRecord) => {
            const isCurrent = version.version === definition.version;
            return (
              <div
                key={version.version}
                className={[
                  "rounded-lg border p-3",
                  isCurrent ? "border-[#b8c8ff] bg-[#f7f9ff]" : "border-[#e5e9ef] bg-white",
                ].join(" ")}
              >
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-semibold text-[#303846]">
                      V{version.version}
                    </span>
                    {isCurrent ? (
                      <span className="rounded bg-[#e9efff] px-1.5 py-0.5 text-[10px] text-[#315efb]">
                        当前版本
                      </span>
                    ) : null}
                  </div>
                  <span className="text-[10px] text-[#8b93a1]">
                    {version.releaseState === "ONLINE" ? "已上线" : "未上线"}
                  </span>
                </div>
                <div className="mt-2 truncate text-xs text-[#4e5969]">
                  {version.name || definition.name}
                </div>
                {version.description ? (
                  <div className="mt-1 line-clamp-2 text-[11px] leading-5 text-[#8b93a1]">
                    {version.description}
                  </div>
                ) : null}
                <div className="mt-2 flex flex-wrap gap-x-3 gap-y-1 text-[10px] text-[#969eaa]">
                  <span>操作人：{version.userName || version.operator || version.modifyBy || "-"}</span>
                  <span>时间：{formatTime(version.operateTime)}</span>
                </div>
                {!isCurrent ? (
                  <div className="mt-3">
                    {confirmVersion === version.version ? (
                      <div className="flex items-center justify-between gap-2 rounded-md bg-[#fff9ed] px-2.5 py-2">
                        <span className="text-[11px] text-[#8a6424]">
                          确认切换到 V{version.version}？
                        </span>
                        <div className="flex gap-1.5">
                          <button
                            type="button"
                            onClick={() => setConfirmVersion(null)}
                            className="rounded px-2 py-1 text-[11px] text-[#6c7480] hover:bg-white"
                          >
                            取消
                          </button>
                          <button
                            type="button"
                            disabled={switchingVersion || workflowIsOnline}
                            onClick={() => void switchVersion(version.version)}
                            className="rounded bg-[#315efb] px-2 py-1 text-[11px] text-white disabled:opacity-50"
                          >
                            {switchingVersion ? "切换中..." : "确认切换"}
                          </button>
                        </div>
                      </div>
                    ) : (
                      <button
                        type="button"
                        disabled={workflowIsOnline || switchingVersion}
                        onClick={() => setConfirmVersion(version.version)}
                        className="text-xs font-medium text-[#315efb] hover:underline disabled:cursor-not-allowed disabled:text-[#aab2bf]"
                      >
                        切换到此版本
                      </button>
                    )}
                  </div>
                ) : null}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );

  const activeTitle = PANEL_ITEMS.find((item) => item.key === activePanel)?.label;

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

  return (
    <aside className="flex h-full min-h-0 shrink-0 border-l border-[#e5e7eb] bg-white">
      {activePanel ? (
        <section className="flex h-full min-h-0 w-[360px] max-w-[calc(100vw-64px)] flex-col">
          <header className="flex h-12 shrink-0 items-center justify-between border-b border-[#e5e7eb] px-4">
            <div className="flex h-6 items-center">
              <span className="text-sm font-semibold text-[#303846]">{activeTitle}</span>
            </div>
            <div className="inline-flex items-center gap-1">
              <button
                type="button"
                title="刷新当前面板"
                onClick={() => void refreshActivePanel()}
                className="inline-flex items-center gap-1 rounded px-1 py-1 text-xs text-[#687385] transition-colors hover:bg-[#f3f4f6] hover:text-[#303846]"
              >
                <RefreshCw size={13} />
                刷新
              </button>
              <span aria-hidden="true" className="mx-1 h-4 w-px bg-[#cbd0d8]" />
              <button
                type="button"
                title="收起面板"
                aria-label="收起面板"
                onClick={() => setActivePanel(null)}
                className="flex size-6 items-center justify-center rounded text-[#8b93a1] transition-colors hover:bg-[#f0f2f5] hover:text-[#303846]"
              >
                <X size={14} />
              </button>
            </div>
          </header>
          {activePanel === "properties" ? (
            <div className="min-h-0 flex-1 overflow-y-auto">{renderProperties()}</div>
          ) : null}
          {activePanel === "schedule" ? renderSchedulePanel() : null}
          {activePanel === "history" ? (
            <div className="min-h-0 flex-1 overflow-y-auto">{renderHistory()}</div>
          ) : null}
          {activePanel === "versions" ? (
            <div className="min-h-0 flex-1 overflow-y-auto">{renderVersions()}</div>
          ) : null}
          {activePanel === "ops" ? (
            <div className="min-h-0 flex-1 overflow-y-auto">{renderOperations()}</div>
          ) : null}
        </section>
      ) : null}
      <nav
        aria-label="工作流配置面板"
        className="flex h-full w-9 shrink-0 flex-col border-l border-[#e5e7eb] bg-white"
      >
        {PANEL_ITEMS.map((item) => {
          const selected = activePanel === item.key;
          return (
            <button
              key={item.key}
              type="button"
              title={item.label}
              aria-label={item.label}
              aria-pressed={selected}
              onClick={() =>
                setActivePanel((current) => (current === item.key ? null : item.key))
              }
              className={[
                "relative flex w-full shrink-0 cursor-pointer items-center justify-center border-b border-[#e5e7eb] px-0 py-2 text-xs leading-[18px] transition-colors first:border-t first:border-[#e5e7eb] hover:bg-[#f7f9fc] hover:text-[#315efb]",
                selected ? "bg-white text-[#315efb]" : "bg-white text-[#7c8796]",
              ].join(" ")}
            >
              {selected ? (
                <span
                  aria-hidden="true"
                  className="absolute inset-y-0 left-0 z-[1] w-0.5 bg-[#315efb]"
                />
              ) : null}
              <span className="[writing-mode:vertical-rl] [text-orientation:upright] [word-break:keep-all] whitespace-nowrap tracking-[2px]">
                {item.label}
              </span>
            </button>
          );
        })}
      </nav>
    </aside>
  );
}

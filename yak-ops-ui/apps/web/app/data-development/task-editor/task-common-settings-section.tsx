import { useMemo, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import {
  Button, CollapseSection, Input, Select, SelectContent, SelectItem,
  SelectItemIndicator, SelectItemText, SelectTrigger, SelectValue,
} from "@yak-ops/yak-ui";

import { useWorkspace } from "@/hooks/use-workspace";
import {
  isTaskGroupEnabled, listTaskGroupsByProjectCode, type TaskGroupRecord,
} from "@/service/task-group";
import type {
  WorkflowTaskCommonSettingsPatch, WorkflowTaskRecord,
} from "@/service/workflow/definition";

export type TaskPriority = "HIGHEST" | "HIGH" | "MEDIUM" | "LOW" | "LOWEST";
export type TaskTimeoutFlag = "OPEN" | "CLOSE";
export type TaskTimeoutStrategy = "WARN" | "FAILED" | "WARNFAILED";
export type TaskExecuteType = "BATCH" | "STREAM";

export type TaskCommonSettings = {
  workerGroup: string;
  environmentCode: string;
  taskPriority: TaskPriority;
  failRetryTimes: string;
  failRetryInterval: string;
  timeoutFlag: TaskTimeoutFlag;
  timeoutNotifyStrategy: TaskTimeoutStrategy;
  timeout: string;
  delayTime: string;
  taskGroupId: number;
  taskGroupPriority: string;
  cpuQuota: string;
  memoryMax: string;
  taskExecuteType: TaskExecuteType;
};

const TASK_PRIORITIES: Array<{ value: TaskPriority; label: string }> = [
  { value: "HIGHEST", label: "最高" }, { value: "HIGH", label: "高" },
  { value: "MEDIUM", label: "中" }, { value: "LOW", label: "低" },
  { value: "LOWEST", label: "最低" },
];
const TIMEOUT_STRATEGIES: Array<{ value: TaskTimeoutStrategy; label: string }> = [
  { value: "WARN", label: "仅告警" }, { value: "FAILED", label: "超时失败" },
  { value: "WARNFAILED", label: "告警并失败" },
];
const EXECUTE_TYPES: Array<{ value: TaskExecuteType; label: string }> = [
  { value: "BATCH", label: "批处理" }, { value: "STREAM", label: "流式处理" },
];
const FIELD_CLASS = "grid grid-cols-[116px_minmax(0,1fr)] items-center gap-3 max-md:grid-cols-1";
const FIELD_LABEL_CLASS = "text-xs text-[#687385]";

function numberText(value: number | null | undefined, fallback: number) {
  return value === null || value === undefined ? String(fallback) : String(value);
}

export function parseTaskCommonSettings(task?: WorkflowTaskRecord): TaskCommonSettings {
  const taskPriority = TASK_PRIORITIES.some(item => item.value === task?.taskPriority)
    ? task!.taskPriority as TaskPriority : "MEDIUM";
  const timeoutFlag = task?.timeoutFlag === "OPEN" ? "OPEN" : "CLOSE";
  const timeoutNotifyStrategy = TIMEOUT_STRATEGIES.some(item => item.value === task?.timeoutNotifyStrategy)
    ? task!.timeoutNotifyStrategy as TaskTimeoutStrategy : "WARNFAILED";
  return {
    workerGroup: task?.workerGroup?.trim() || "default",
    environmentCode: numberText(task?.environmentCode, -1),
    taskPriority,
    failRetryTimes: numberText(task?.failRetryTimes, 0),
    failRetryInterval: numberText(task?.failRetryInterval, 1),
    timeoutFlag,
    timeoutNotifyStrategy,
    timeout: numberText(task?.timeout, 0),
    delayTime: numberText(task?.delayTime, 0),
    taskGroupId: task?.taskGroupId && task.taskGroupId > 0 ? task.taskGroupId : 0,
    taskGroupPriority: numberText(task?.taskGroupPriority, 0),
    cpuQuota: numberText(task?.cpuQuota, -1),
    memoryMax: numberText(task?.memoryMax, -1),
    taskExecuteType: task?.taskExecuteType === "STREAM" ? "STREAM" : "BATCH",
  };
}

function parseInteger(value: string, label: string, minimum: number) {
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < minimum) {
    throw new Error(label + "必须是大于等于 " + minimum + " 的整数");
  }
  return parsed;
}

export function normalizeTaskCommonSettings(settings: TaskCommonSettings): WorkflowTaskCommonSettingsPatch {
  const taskGroupId = parseInteger(String(settings.taskGroupId), "TaskGroup", 0);
  return {
    workerGroup: settings.workerGroup.trim() || "default",
    environmentCode: parseInteger(settings.environmentCode || "-1", "Environment Code", -1),
    taskPriority: settings.taskPriority,
    failRetryTimes: parseInteger(settings.failRetryTimes, "失败重试次数", 0),
    failRetryInterval: parseInteger(settings.failRetryInterval, "重试间隔", 1),
    timeoutFlag: settings.timeoutFlag,
    timeoutNotifyStrategy: settings.timeoutNotifyStrategy,
    timeout: parseInteger(settings.timeout, "超时时间", 0),
    delayTime: parseInteger(settings.delayTime, "延迟执行时间", 0),
    taskGroupId,
    taskGroupPriority: taskGroupId > 0
      ? parseInteger(settings.taskGroupPriority, "组内优先级", 0) : 0,
    cpuQuota: parseInteger(settings.cpuQuota, "CPU 配额", -1),
    memoryMax: parseInteger(settings.memoryMax, "最大内存", -1),
    taskExecuteType: settings.taskExecuteType,
  };
}

export function getTaskGroupValidationError(
  settings: TaskCommonSettings,
  options: { projectCode: string; taskGroups: TaskGroupRecord[]; isPending: boolean; isError: boolean },
): string | undefined {
  if (settings.taskGroupId <= 0) return undefined;
  if (!options.projectCode) return "请先选择工作空间";
  if (options.isPending) return "任务组正在加载，请稍后再保存";
  if (options.isError) return "任务组列表加载失败，请刷新后重试";
  const selected = options.taskGroups.find(group => group.id === settings.taskGroupId);
  if (!selected) return "所选任务组已不存在，请重新选择";
  if (!isTaskGroupEnabled(selected.status)) return "所选任务组已停用，请启用任务组或重新选择";
  return undefined;
}

export function useTaskGroups() {
  const { currentWorkspace } = useWorkspace();
  const projectCode = currentWorkspace?.id ?? "";
  const taskGroupsQuery = useQuery({
    queryKey: ["data-development", "task-groups", projectCode],
    queryFn: ({ signal }) => listTaskGroupsByProjectCode(
      projectCode, { pageNo: 1, pageSize: 1000 }, signal,
    ),
    enabled: Boolean(projectCode),
    staleTime: 30_000,
    refetchOnMount: "always",
  });
  return {
    projectCode,
    taskGroups: taskGroupsQuery.data?.totalList ?? [],
    taskGroupsQuery,
  };
}

function SettingField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className={FIELD_CLASS}>
      <span className={FIELD_LABEL_CLASS}>{label}</span>
      <div className="min-w-0">{children}</div>
    </label>
  );
}

function SettingsSelect({
  value, options, disabled, onChange, placeholder,
}: {
  value: string;
  options: Array<{ value: string; label: string; disabled?: boolean }>;
  disabled?: boolean;
  onChange: (value: string) => void;
  placeholder?: string;
}) {
  const items = useMemo(() => Object.fromEntries(options.map(item => [item.value, item.label])), [options]);
  return (
    <Select<string> size="small" items={items} value={value} disabled={disabled} onValueChange={next => onChange(next ?? "")}>
      <SelectTrigger variant="outlined" className="w-full">
        <SelectValue placeholder={placeholder ?? "请选择"} />
      </SelectTrigger>
      <SelectContent emptyContent="暂无可选项">
        {options.map(option => (
          <SelectItem key={option.value} value={option.value} disabled={option.disabled}>
            <SelectItemText>{option.label}</SelectItemText>
            <SelectItemIndicator />
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}

export function TaskCommonSettingsSection({
  id, title = "节点通用配置", settings, onChange, disabled = false,
  description, footer, compact = false,
}: {
  id: string;
  title?: string;
  settings: TaskCommonSettings;
  onChange: (settings: TaskCommonSettings) => void;
  disabled?: boolean;
  description?: string;
  footer?: ReactNode;
  compact?: boolean;
}) {
  const navigate = useNavigate();
  const { projectCode, taskGroups, taskGroupsQuery } = useTaskGroups();
  const selectedTaskGroup = taskGroups.find(group => group.id === settings.taskGroupId);
  const taskGroupOptions = [
    { value: "0", label: "不使用任务组" },
    ...(settings.taskGroupId > 0 && !selectedTaskGroup
      ? [{ value: String(settings.taskGroupId), label: "当前任务组 #" + settings.taskGroupId }]
      : []),
    ...taskGroups.map(group => ({
      value: String(group.id),
      label: group.name + " · " + (group.useSize ?? 0) + "/" + group.groupSize +
        (isTaskGroupEnabled(group.status) ? "" : " · 已停用"),
      disabled: !isTaskGroupEnabled(group.status) && group.id !== settings.taskGroupId,
    })),
  ];
  const update = <K extends keyof TaskCommonSettings>(key: K, value: TaskCommonSettings[K]) =>
    onChange({ ...settings, [key]: value });
  const fieldGridClass = compact
    ? "grid grid-cols-1 gap-y-3"
    : "grid grid-cols-2 gap-x-8 gap-y-3 max-xl:grid-cols-1";

  return (
    <CollapseSection
      id={id}
      title={title}
      extra={compact ? undefined : (
        <span className="text-[11px] font-normal text-[var(--yak-components-muted-text)]">
          {selectedTaskGroup
            ? (selectedTaskGroup.useSize ?? 0) + "/" + selectedTaskGroup.groupSize + " 已使用"
            : "执行资源 · 策略 · 超时 · 配额"}
        </span>
      )}
      triggerClassName="!h-8 rounded-sm px-3"
      contentClassName="space-y-4 pt-3"
    >
      {description ? <p className="text-[11px] leading-5 text-[var(--yak-components-muted-text)]">{description}</p> : null}

      <section className="space-y-3">
        <h4 className="border-b border-[var(--yak-components-panel-border)] pb-1.5 text-xs font-semibold text-[var(--yak-components-panel-text)]">执行资源</h4>
        <div className={fieldGridClass}>
          <SettingField label="Worker Group">
            <Input size="small" variant="outlined" value={settings.workerGroup} disabled={disabled}
              onChange={event => update("workerGroup", event.target.value)} placeholder="default" className="w-full min-w-0" />
          </SettingField>
          <SettingField label="Environment Code">
            <Input size="small" variant="outlined" type="number" value={settings.environmentCode} disabled={disabled}
              onChange={event => update("environmentCode", event.target.value)} placeholder="-1" className="w-full min-w-0" />
          </SettingField>
          <SettingField label="TaskGroup">
            <div className="space-y-1">
              <SettingsSelect value={String(settings.taskGroupId)} options={taskGroupOptions}
                disabled={disabled || (settings.taskGroupId > 0 && (taskGroupsQuery.isPending || taskGroupsQuery.isError))}
                onChange={value => {
                  const taskGroupId = Number(value || 0);
                  onChange({ ...settings, taskGroupId, taskGroupPriority: taskGroupId > 0 ? settings.taskGroupPriority || "0" : "0" });
                }}
                placeholder={!projectCode ? "请先选择工作空间" : "请选择任务组"} />
              {taskGroupsQuery.isError ? (
                <button type="button" onClick={() => void taskGroupsQuery.refetch()} className="text-[11px] text-[#315efb] hover:underline">
                  任务组加载失败，点击重试
                </button>
              ) : null}
            </div>
          </SettingField>
          <SettingField label="组内优先级">
            <Input size="small" variant="outlined" type="number" min={0} step={1}
              value={settings.taskGroupPriority} disabled={disabled || settings.taskGroupId <= 0}
              onChange={event => update("taskGroupPriority", event.target.value)} placeholder="0" className="w-full min-w-0" />
          </SettingField>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-2 text-[11px] text-[var(--yak-components-muted-text)]">
          <span>
            {selectedTaskGroup
              ? "当前并发容量：" + (selectedTaskGroup.useSize ?? 0) + "/" + selectedTaskGroup.groupSize
              : "可在任务组管理中创建和维护并发资源组"}
          </span>
          <div className="flex items-center gap-1">
            <Button type="button" size="sm" variant="ghost" onClick={() => navigate("/resources/task-groups")}>管理任务组</Button>
            {settings.taskGroupId > 0 ? (
              <Button type="button" size="sm" variant="ghost" onClick={() => navigate("/resources/task-group-queue?groupId=" + settings.taskGroupId)}>查看队列</Button>
            ) : null}
          </div>
        </div>
      </section>

      <section className="space-y-3">
        <h4 className="border-b border-[var(--yak-components-panel-border)] pb-1.5 text-xs font-semibold text-[var(--yak-components-panel-text)]">执行策略</h4>
        <div className={fieldGridClass}>
          <SettingField label="任务优先级">
            <SettingsSelect value={settings.taskPriority} options={TASK_PRIORITIES} disabled={disabled}
              onChange={value => update("taskPriority", value as TaskPriority)} />
          </SettingField>
          <SettingField label="失败重试次数">
            <Input size="small" variant="outlined" type="number" min={0} step={1} value={settings.failRetryTimes}
              disabled={disabled} onChange={event => update("failRetryTimes", event.target.value)} className="w-full min-w-0" />
          </SettingField>
          <SettingField label="重试间隔（分钟）">
            <Input size="small" variant="outlined" type="number" min={1} step={1} value={settings.failRetryInterval}
              disabled={disabled} onChange={event => update("failRetryInterval", event.target.value)} className="w-full min-w-0" />
          </SettingField>
          <SettingField label="延迟执行（分钟）">
            <Input size="small" variant="outlined" type="number" min={0} step={1} value={settings.delayTime}
              disabled={disabled} onChange={event => update("delayTime", event.target.value)} className="w-full min-w-0" />
          </SettingField>
        </div>
      </section>

      <section className="space-y-3">
        <h4 className="border-b border-[var(--yak-components-panel-border)] pb-1.5 text-xs font-semibold text-[var(--yak-components-panel-text)]">超时设置</h4>
        <div className={fieldGridClass}>
          <SettingField label="超时控制">
            <SettingsSelect value={settings.timeoutFlag} options={[{ value: "CLOSE", label: "关闭" }, { value: "OPEN", label: "开启" }]}
              disabled={disabled} onChange={value => update("timeoutFlag", value as TaskTimeoutFlag)} />
          </SettingField>
          <SettingField label="超时时间（分钟）">
            <Input size="small" variant="outlined" type="number" min={0} step={1} value={settings.timeout}
              disabled={disabled || settings.timeoutFlag === "CLOSE"} onChange={event => update("timeout", event.target.value)} className="w-full min-w-0" />
          </SettingField>
          <SettingField label="超时处理策略">
            <SettingsSelect value={settings.timeoutNotifyStrategy} options={TIMEOUT_STRATEGIES}
              disabled={disabled || settings.timeoutFlag === "CLOSE"}
              onChange={value => update("timeoutNotifyStrategy", value as TaskTimeoutStrategy)} />
          </SettingField>
          <SettingField label="执行类型">
            <SettingsSelect value={settings.taskExecuteType} options={EXECUTE_TYPES} disabled={disabled}
              onChange={value => update("taskExecuteType", value as TaskExecuteType)} />
          </SettingField>
        </div>
      </section>

      <section className="space-y-3">
        <h4 className="border-b border-[var(--yak-components-panel-border)] pb-1.5 text-xs font-semibold text-[var(--yak-components-panel-text)]">资源限制</h4>
        <div className={fieldGridClass}>
          <SettingField label="CPU 配额">
            <Input size="small" variant="outlined" type="number" min={-1} step={1} value={settings.cpuQuota}
              disabled={disabled} onChange={event => update("cpuQuota", event.target.value)} className="w-full min-w-0" />
          </SettingField>
          <SettingField label="最大内存">
            <Input size="small" variant="outlined" type="number" min={-1} step={1} value={settings.memoryMax}
              disabled={disabled} onChange={event => update("memoryMax", event.target.value)} className="w-full min-w-0" />
          </SettingField>
        </div>
        <p className="text-[11px] leading-5 text-[var(--yak-components-muted-text)]">
          CPU 配额和最大内存填写 -1 表示不限制；Environment Code 填 -1 表示使用默认环境。
        </p>
      </section>

      <div className="flex flex-wrap items-center justify-between gap-2 border-t border-[var(--yak-components-panel-border)] pt-3">
        <span className="text-[11px] leading-5 text-[var(--yak-components-muted-text)]">
          TaskGroup 控制同组任务并发；不选择任务组时不受任务组容量限制。
        </span>
        {footer}
      </div>
    </CollapseSection>
  );
}

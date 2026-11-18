import { useEffect, useMemo, useState, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import {
  Button, CollapseSection, Input, Select, SelectContent, SelectItem,
  SelectItemIndicator, SelectItemText, SelectTrigger, SelectValue, SliderInput,
} from "@yak-ops/yak-ui";

import { useWorkspace } from "@/hooks/use-workspace";
import {
  isTaskGroupEnabled, listTaskGroupsByProjectCode, type TaskGroupRecord,
} from "@/service/task-group";
import type {
  WorkflowTaskCommonSettingsPatch, WorkflowTaskRecord,
} from "@/service/workflow/definition";
import type { LogicTaskRunConfigController } from "../../shared/logic-task-run-config";

export type ConditionsTaskPriority = "HIGHEST" | "HIGH" | "MEDIUM" | "LOW" | "LOWEST";
export type ConditionsTaskTimeoutFlag = "OPEN" | "CLOSE";
export type ConditionsRunConfig = {
  // Preserve an existing internal routing value; this is not user-configurable for Master logic tasks.
  workerGroup: string;
  taskPriority: ConditionsTaskPriority;
  failRetryTimes: string;
  failRetryInterval: string;
  timeoutFlag: ConditionsTaskTimeoutFlag;
  timeout: string;
  delayTime: string;
  taskGroupId: number;
  taskGroupPriority: string;
};

};

const TASK_PRIORITIES: Array<{ value: ConditionsTaskPriority; label: string }> = [
  { value: "HIGHEST", label: "最高" }, { value: "HIGH", label: "高" },
  { value: "MEDIUM", label: "中" }, { value: "LOW", label: "低" },
  { value: "LOWEST", label: "最低" },
];
const FIELD_CLASS = "grid grid-cols-[116px_minmax(0,1fr)] items-center gap-3 max-md:grid-cols-1";
const FIELD_LABEL_CLASS = "text-xs text-[#687385]";

function numberText(value: number | null | undefined, fallback: number) {
  return value === null || value === undefined ? String(fallback) : String(value);
}

export function parseConditionsRunConfig(task?: WorkflowTaskRecord): ConditionsRunConfig {
  const taskPriority = TASK_PRIORITIES.some(item => item.value === task?.taskPriority)
    ? task!.taskPriority as ConditionsTaskPriority : "MEDIUM";
  return {
    // Keep the stored workerGroup for compatibility with existing task definitions.
    workerGroup: task?.workerGroup?.trim() || "default",
    taskPriority,
    failRetryTimes: numberText(task?.failRetryTimes, 0),
    failRetryInterval: numberText(task?.failRetryInterval, 1),
    timeoutFlag: task?.timeoutFlag === "OPEN" ? "OPEN" : "CLOSE",
    timeout: numberText(task?.timeout, 0),
    delayTime: numberText(task?.delayTime, 0),
    taskGroupId: task?.taskGroupId && task.taskGroupId > 0 ? task.taskGroupId : 0,
    taskGroupPriority: numberText(task?.taskGroupPriority, 0),
  };
}

function parseInteger(value: string, label: string, minimum: number) {
  const parsed = Number(value);
  if (!Number.isSafeInteger(parsed) || parsed < minimum) {
    throw new Error(label + "必须是大于等于 " + minimum + " 的整数");
  }
  return parsed;
}

export function normalizeConditionsRunConfig(settings: ConditionsRunConfig): WorkflowTaskCommonSettingsPatch {
  const taskGroupId = parseInteger(String(settings.taskGroupId), "TaskGroup", 0);
  const failRetryTimes = parseInteger(settings.failRetryTimes, "失败重试次数", 0);
  return {
    // Keep the existing Worker Group value, but do not expose Worker selection for Master logic tasks.
    workerGroup: settings.workerGroup.trim() || "default",
    // These fields remain in the existing API contract and are fixed for Master logic tasks.
    environmentCode: -1,
    taskPriority: settings.taskPriority,
    failRetryTimes,
    failRetryInterval: failRetryTimes > 0
      ? parseInteger(settings.failRetryInterval, "重试间隔", 1)
      : 1,
    timeoutFlag: settings.timeoutFlag,
    timeoutNotifyStrategy: settings.timeoutFlag === "OPEN" ? "WARN" : null,
    timeout: parseInteger(settings.timeout, "超时时间", settings.timeoutFlag === "OPEN" ? 1 : 0),
    delayTime: parseInteger(settings.delayTime, "延迟执行时间", 0),
    taskGroupId,
    taskGroupPriority: taskGroupId > 0
      ? parseInteger(settings.taskGroupPriority, "组内优先级", 0) : 0,
    cpuQuota: -1,
    memoryMax: -1,
    taskExecuteType: "BATCH",
  };
}

export function getConditionsTaskGroupValidationError(
  settings: ConditionsRunConfig,
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

export function useConditionsTaskGroups() {
  const { currentWorkspace } = useWorkspace();
  const projectCode = currentWorkspace?.id ?? "";
  const taskGroupsQuery = useQuery({
    queryKey: ["data-development", "conditions-task-groups", projectCode],
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
      <SelectContent emptyContent={options.length === 0 ? "暂无可选项" : undefined}>
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


export function ConditionsRunConfigPanel({
  id, settings, onChange, disabled = false,
  description, footer, compact = false,
}: {
  id: string;
  settings: ConditionsRunConfig;
  onChange: (settings: ConditionsRunConfig) => void;
  disabled?: boolean;
  description?: string;
  footer?: ReactNode;
  compact?: boolean;
}) {
  const navigate = useNavigate();
  const { projectCode, taskGroups, taskGroupsQuery } = useConditionsTaskGroups();
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
  const update = <K extends keyof ConditionsRunConfig>(key: K, value: ConditionsRunConfig[K]) =>
    onChange({ ...settings, [key]: value });
  const fieldGridClass = compact
    ? "grid grid-cols-1 gap-y-3"
    : "grid grid-cols-2 gap-x-8 gap-y-3 max-xl:grid-cols-1";

  return (
    <div className="space-y-2">
      {!compact && description ? <p className="text-[11px] leading-5 text-[var(--yak-components-muted-text)]">{description}</p> : null}

      <CollapseSection
        id={id + "-scheduling"}
        title="调度配置"
        triggerClassName="!h-8 rounded-sm px-3"
        contentClassName="space-y-3 pt-3"
      >
        <div className={fieldGridClass}>
          <SettingField label="任务优先级">
            <SettingsSelect value={settings.taskPriority} options={TASK_PRIORITIES} disabled={disabled}
              onChange={value => update("taskPriority", value as ConditionsTaskPriority)} />
          </SettingField>
          <SettingField label="延迟执行（分钟）">
            <Input size="small" variant="outlined" type="number" min={0} step={1} value={settings.delayTime}
              disabled={disabled} onChange={event => update("delayTime", event.target.value)} className="w-full min-w-0" />
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
          {settings.taskGroupId > 0 ? (
            <SettingField label="组内优先级">
              <Input size="small" variant="outlined" type="number" min={0} step={1}
                value={settings.taskGroupPriority} disabled={disabled}
                onChange={event => update("taskGroupPriority", event.target.value)} placeholder="0" className="w-full min-w-0" />
            </SettingField>
          ) : null}
        </div>
        <div className={"flex flex-wrap items-center gap-2 text-[11px] text-[var(--yak-components-muted-text)] " + (compact ? "justify-end" : "justify-between")}>
          {!compact ? (
            <span>
              {selectedTaskGroup
                ? "当前并发容量：" + (selectedTaskGroup.useSize ?? 0) + "/" + selectedTaskGroup.groupSize
                : "TaskGroup 用于限制同组逻辑节点的并发执行数量"}
            </span>
          ) : null}
          <div className="flex items-center gap-1">
            <Button type="button" size="sm" variant="ghost" onClick={() => navigate("/resources/task-groups")}>管理任务组</Button>
            {settings.taskGroupId > 0 ? (
              <Button type="button" size="sm" variant="ghost" onClick={() => navigate("/resources/task-group-queue?groupId=" + settings.taskGroupId)}>查看队列</Button>
            ) : null}
          </div>
        </div>
      </CollapseSection>

      <CollapseSection
        id={id + "-retries"}
        title="失败重试"
        triggerClassName="!h-8 rounded-sm px-3"
        contentClassName="space-y-3 pt-3"
      >
        <div className={fieldGridClass}>
          <SettingField label="最大重试次数">
            <SliderInput
              label="最大重试次数"
              value={Math.max(0, Number(settings.failRetryTimes) || 0)}
              min={0}
              max={10}
              step={1}
              unit="次"
              disabled={disabled}
              onValueChange={value => update("failRetryTimes", String(value))}
            />
          </SettingField>
          {Number(settings.failRetryTimes) > 0 ? (
            <SettingField label="重试间隔">
              <SliderInput
                label="重试间隔"
                value={Math.max(1, Number(settings.failRetryInterval) || 1)}
                min={1}
                max={60}
                step={1}
                unit="分钟"
                disabled={disabled}
                onValueChange={value => update("failRetryInterval", String(value))}
              />
            </SettingField>
          ) : null}
        </div>
      </CollapseSection>

      <CollapseSection
        id={id + "-timeout-alert"}
        title="超时告警"
        triggerClassName="!h-8 rounded-sm px-3"
        contentClassName="space-y-3 pt-3"
      >
        <div className={fieldGridClass}>
          <SettingField label="超时告警">
            <SettingsSelect value={settings.timeoutFlag} options={[{ value: "CLOSE", label: "关闭" }, { value: "OPEN", label: "开启" }]}
              disabled={disabled} onChange={value => update("timeoutFlag", value as ConditionsTaskTimeoutFlag)} />
          </SettingField>
          {settings.timeoutFlag === "OPEN" ? (
            <SettingField label="超时时间（分钟）">
              <Input size="small" variant="outlined" type="number" min={1} step={1} value={settings.timeout}
                disabled={disabled} onChange={event => update("timeout", event.target.value)} className="w-full min-w-0" />
            </SettingField>
          ) : null}
        </div>
        {settings.timeoutFlag === "OPEN" ? (
          <p className="text-[11px] leading-5 text-[var(--yak-components-muted-text)]">
            超时后仅发送告警，不会主动中断逻辑节点；请在工作流告警配置中设置告警接收组。
          </p>
        ) : null}
      </CollapseSection>


      {!compact || footer ? (
        <div className="flex flex-wrap items-center justify-between gap-2 border-t border-[var(--yak-components-panel-border)] pt-3">
          {!compact ? (
            <span className="text-[11px] leading-5 text-[var(--yak-components-muted-text)]">
              TaskGroup 控制同组任务并发；不选择任务组时不受任务组容量限制。
            </span>
          ) : null}
          {footer}
        </div>
      ) : null}
    </div>
  );
}


export function useConditionsRunConfig(
  task: WorkflowTaskRecord,
  disabled: boolean,
): LogicTaskRunConfigController {
  const { projectCode, taskGroups, taskGroupsQuery } = useConditionsTaskGroups();
  const initialSettings = useMemo(() => parseConditionsRunConfig(task), [task]);
  const [settings, setSettings] = useState<ConditionsRunConfig>(initialSettings);
  const [baselineSettings, setBaselineSettings] = useState<ConditionsRunConfig>(initialSettings);

  useEffect(() => {
    setSettings(initialSettings);
    setBaselineSettings(initialSettings);
  }, [initialSettings]);

  const isDirty = JSON.stringify(settings) !== JSON.stringify(baselineSettings);

  return {
    isDirty,
    content: (
      <ConditionsRunConfigPanel
        id="conditions-run-config"
        settings={settings}
        onChange={setSettings}
        disabled={disabled}
        compact
      />
    ),
    getPatch: () => normalizeConditionsRunConfig(settings),
    getPreviewPatch: () => {
      try {
        return normalizeConditionsRunConfig(settings) as unknown as Record<string, unknown>;
      } catch {
        return settings as unknown as Record<string, unknown>;
      }
    },
    validate: () => getConditionsTaskGroupValidationError(settings, {
      projectCode,
      taskGroups,
      isPending: taskGroupsQuery.isPending,
      isError: taskGroupsQuery.isError,
    }),
    markSaved: () => setBaselineSettings(settings),
    refreshFromTask: (latestTask) => {
      const nextSettings = parseConditionsRunConfig(latestTask);
      setSettings(nextSettings);
      setBaselineSettings(nextSettings);
    },
  };
}

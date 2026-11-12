import { useEffect, useMemo, useState, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { Info } from "lucide-react";

import { useWorkspace } from "@/hooks/use-workspace";
import {
  Button,
  CollapseSection,
  Field,
  FieldLabel,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@yak-ops/yak-ui";
import {
  isTaskGroupEnabled,
  listTaskGroupsByProjectCode,
  type TaskGroupRecord,
} from "@/service/task-group";
import type { WorkflowTaskRecord } from "@/service/workflow/definition";

export type TaskSchedulingSettings = {
  taskGroupId: number;
  taskGroupPriority: string;
};

export type NormalizedTaskSchedulingSettings = {
  taskGroupId: number;
  taskGroupPriority: number;
};

function parseSettings(task?: WorkflowTaskRecord): TaskSchedulingSettings {
  return {
    taskGroupId:
      task?.taskGroupId && task.taskGroupId > 0 ? task.taskGroupId : 0,
    taskGroupPriority: String(task?.taskGroupPriority ?? 0),
  };
}

export function useTaskScheduling(task?: WorkflowTaskRecord) {
  const { currentWorkspace } = useWorkspace();
  const projectCode = currentWorkspace?.id ?? "";
  const initialSettings = useMemo(
    () => parseSettings(task),
    [task?.taskGroupId, task?.taskGroupPriority],
  );
  const [settings, setSettings] = useState<TaskSchedulingSettings>(initialSettings);
  const [baseline, setBaseline] = useState<TaskSchedulingSettings>(initialSettings);

  useEffect(() => {
    setSettings(initialSettings);
    setBaseline(initialSettings);
  }, [initialSettings]);

  const taskGroupQuery = useQuery({
    queryKey: ["data-development", "task-groups", projectCode],
    queryFn: ({ signal }) =>
      listTaskGroupsByProjectCode(
        projectCode,
        { pageNo: 1, pageSize: 1000 },
        signal,
      ),
    enabled: Boolean(projectCode),
    staleTime: 30_000,
    refetchOnMount: "always",
  });

  const taskGroups = taskGroupQuery.data?.totalList ?? [];
  const selectedTaskGroup = taskGroups.find(
    (group) => group.id === settings.taskGroupId,
  );
  const isDirty = JSON.stringify(settings) !== JSON.stringify(baseline);

  const getNormalizedSettings = (): NormalizedTaskSchedulingSettings => {
    const taskGroupId = settings.taskGroupId > 0 ? settings.taskGroupId : 0;
    return {
      taskGroupId,
      taskGroupPriority:
        taskGroupId > 0 ? Number(settings.taskGroupPriority || 0) : 0,
    };
  };

  const getValidationError = (): string | undefined => {
    const normalized = getNormalizedSettings();
    if (normalized.taskGroupId <= 0) return undefined;

    if (
      !Number.isSafeInteger(normalized.taskGroupPriority) ||
      normalized.taskGroupPriority < 0
    ) {
      return "组内优先级必须为大于等于 0 的整数";
    }
    if (!projectCode) return "请先选择工作空间";
    if (taskGroupQuery.isPending) return "任务组正在加载，请稍后再保存";
    if (taskGroupQuery.isError) return "任务组列表加载失败，请刷新后重试";
    if (taskGroupQuery.isSuccess && !selectedTaskGroup) {
      return "所选任务组已不存在，请重新选择";
    }
    if (selectedTaskGroup && !isTaskGroupEnabled(selectedTaskGroup.status)) {
      return "所选任务组已停用，请启用任务组或重新选择";
    }
    return undefined;
  };

  const markSaved = (normalized: NormalizedTaskSchedulingSettings) => {
    const next: TaskSchedulingSettings = {
      taskGroupId: normalized.taskGroupId,
      taskGroupPriority: String(normalized.taskGroupPriority),
    };
    setSettings(next);
    setBaseline(next);
  };

  return {
    projectCode,
    settings,
    setSettings,
    taskGroups,
    selectedTaskGroup,
    taskGroupQuery,
    isDirty,
    getNormalizedSettings,
    getValidationError,
    markSaved,
  };
}

function SchedulingField({
  label,
  labelAddon,
  children,
}: {
  label: string;
  labelAddon?: ReactNode;
  children: ReactNode;
}) {
  return (
    <Field className="grid grid-cols-[148px_minmax(0,1fr)] !gap-3 items-center max-md:grid-cols-1">
      <div className="flex min-w-0 items-center gap-1.5">
        <FieldLabel>{label}</FieldLabel>
        {labelAddon}
      </div>
      <div className="min-w-0 max-w-[760px]">{children}</div>
    </Field>
  );
}

export function TaskSchedulingSection({
  settings,
  onChange,
  projectCode,
  taskGroups,
  isFetching,
  isError,
}: {
  settings: TaskSchedulingSettings;
  onChange: (settings: TaskSchedulingSettings) => void;
  projectCode: string;
  taskGroups: TaskGroupRecord[];
  isFetching: boolean;
  isError: boolean;
}) {
  const navigate = useNavigate();
  const selectedTaskGroup = taskGroups.find(
    (group) => group.id === settings.taskGroupId,
  );
  const taskGroupOptions = useMemo(
    () => ({
      "0": "不使用任务组",
      ...Object.fromEntries(
        taskGroups.map((group) => [
          String(group.id),
          group.name +
            " · " +
            (group.useSize ?? 0) +
            "/" +
            group.groupSize +
            (isTaskGroupEnabled(group.status) ? "" : " · 已停用"),
        ]),
      ),
    }),
    [taskGroups],
  );

  return (
    <CollapseSection
      id="task-scheduling"
      title="调度配置"
      extra={
        settings.taskGroupId > 0 && selectedTaskGroup ? (
          <span className="text-xs font-normal text-[var(--yak-components-muted-text)]">
            {(selectedTaskGroup.useSize ?? 0) +
              "/" +
              selectedTaskGroup.groupSize +
              " 已使用"}
          </span>
        ) : undefined
      }
      triggerClassName="!h-8 rounded-sm px-3"
      contentClassName="space-y-3 pt-3"
    >
      <SchedulingField
        label="任务组"
        labelAddon={
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger
                render={
                  <button
                    type="button"
                    aria-label="查看任务组说明"
                    className="inline-flex size-4 cursor-help items-center justify-center rounded-full text-[var(--yak-components-muted-text)] transition-colors hover:text-[var(--yak-color-primary)]"
                  >
                    <Info size={13} aria-hidden="true" />
                  </button>
                }
              />
              <TooltipContent
                side="top"
                align="start"
                className="max-w-[300px] shadow-xl"
              >
                任务组用于限制同组任务的并发数量。容量占满后，后续任务会进入任务组队列等待资源。
              </TooltipContent>
            </Tooltip>
          </TooltipProvider>
        }
      >
        <Select<string>
          size="small"
          items={taskGroupOptions}
          value={String(settings.taskGroupId)}
          onValueChange={(value) => {
            const taskGroupId = Number(value ?? 0);
            onChange({
              taskGroupId,
              taskGroupPriority:
                taskGroupId > 0 ? settings.taskGroupPriority || "0" : "0",
            });
          }}
        >
          <SelectTrigger
            variant="outlined"
            className="w-full max-w-[760px]"
          >
            <SelectValue placeholder="请选择任务组" />
          </SelectTrigger>
          <SelectContent
            emptyContent={
              !projectCode
                ? "请先选择工作空间"
                : isFetching
                  ? "任务组加载中..."
                  : isError
                    ? "任务组加载失败，请刷新重试"
                    : "当前工作空间暂无任务组"
            }
          >
            <SelectItem value="0">
              <SelectItemText>不使用任务组</SelectItemText>
              <SelectItemIndicator />
            </SelectItem>
            {taskGroups.map((group) => (
              <SelectItem
                key={group.id}
                value={String(group.id)}
                disabled={
                  !isTaskGroupEnabled(group.status) &&
                  group.id !== settings.taskGroupId
                }
              >
                <SelectItemText>
                  {group.name} · {group.useSize ?? 0}/{group.groupSize}
                  {!isTaskGroupEnabled(group.status) ? " · 已停用" : ""}
                </SelectItemText>
                <SelectItemIndicator />
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </SchedulingField>

      <SchedulingField
        label="组内优先级"
        labelAddon={
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger
                render={
                  <button
                    type="button"
                    aria-label="查看组内优先级说明"
                    className="inline-flex size-4 cursor-help items-center justify-center rounded-full text-[var(--yak-components-muted-text)] transition-colors hover:text-[var(--yak-color-primary)]"
                  >
                    <Info size={13} aria-hidden="true" />
                  </button>
                }
              />
              <TooltipContent
                side="top"
                align="start"
                className="max-w-[260px] shadow-xl"
              >
                同一任务组中，数值越大的任务越优先获得可用资源槽位。
              </TooltipContent>
            </Tooltip>
          </TooltipProvider>
        }
      >
        <Input
          type="number"
          min={0}
          step={1}
          size="small"
          variant="outlined"
          value={settings.taskGroupPriority}
          disabled={settings.taskGroupId <= 0}
          onChange={(event) =>
            onChange({
              ...settings,
              taskGroupPriority: event.target.value,
            })
          }
          placeholder="0"
          className="w-full max-w-[760px]"
        />
      </SchedulingField>

      <div className="flex flex-wrap items-center justify-between gap-2 border-t border-[var(--yak-components-panel-border)] pt-3">
        <span className="text-[11px] leading-5 text-[var(--yak-components-muted-text)]">
          未选择任务组时，当前节点不受任务组并发容量限制。
        </span>
        <div className="flex items-center gap-1">
          <Button
            type="button"
            size="sm"
            variant="ghost"
            onClick={() => navigate("/resources/task-groups")}
          >
            管理任务组
          </Button>
          {settings.taskGroupId > 0 ? (
            <Button
              type="button"
              size="sm"
              variant="ghost"
              onClick={() =>
                navigate(
                  "/resources/task-group-queue?groupId=" +
                    settings.taskGroupId,
                )
              }
            >
              查看队列
            </Button>
          ) : null}
        </div>
      </div>
    </CollapseSection>
  );
}

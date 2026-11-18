import { useQuery } from "@tanstack/react-query";
import { Button, Input } from "@yak-ops/yak-ui";

import {
  listWorkflowDefinitions,
  listWorkflowTasks,
  type WorkflowDefinitionRecord,
  type WorkflowTaskRecord,
} from "@/service/workflow/definition";

import { LogicTaskEditor, type LogicTaskFormProps } from "../shared/logic-task-editor";
import { useGenericLogicTaskRunConfig } from "../shared/use-generic-logic-run-config";
import { LogicField, LogicSection, LogicSelect } from "../shared/logic-form-controls";
import {
  createDependentItem,
  normalizeDependentTaskParams,
  validateDependentTaskParams,
} from "./adapter";
import type {
  DependResult,
  DependentFailurePolicy,
  DependentGroup,
  DependentItem,
  DependentRelation,
  DependentType,
} from "./types";

const RELATION_OPTIONS = [
  { value: "AND", label: "同时满足（AND）" },
  { value: "OR", label: "满足其一（OR）" },
];

const DEPENDENT_TYPE_OPTIONS = [
  { value: "DEPENDENT_ON_WORKFLOW", label: "工作流完成" },
  { value: "DEPENDENT_ON_TASK", label: "任务完成" },
];

const RESULT_OPTIONS = [
  { value: "SUCCESS", label: "成功" },
  { value: "WAITING", label: "等待中" },
  { value: "FAILED", label: "失败" },
  { value: "NON_EXEC", label: "未执行" },
];

const CYCLE_OPTIONS = [
  { value: "hour", label: "小时" },
  { value: "day", label: "天" },
  { value: "week", label: "周" },
  { value: "month", label: "月" },
];

const DATE_OPTIONS: Record<string, Array<{ value: string; label: string }>> = {
  hour: [
    { value: "currentHour", label: "当前小时" },
    { value: "last1Hour", label: "最近 1 小时" },
    { value: "last2Hours", label: "最近 2 小时" },
    { value: "last3Hours", label: "最近 3 小时" },
    { value: "last24Hours", label: "最近 24 小时" },
  ],
  day: [
    { value: "today", label: "今天" },
    { value: "last1Days", label: "最近 1 天" },
    { value: "last2Days", label: "最近 2 天" },
    { value: "last3Days", label: "最近 3 天" },
    { value: "last7Days", label: "最近 7 天" },
  ],
  week: [
    { value: "thisWeek", label: "本周" },
    { value: "lastWeek", label: "上周" },
    { value: "lastMonday", label: "上周一" },
    { value: "lastTuesday", label: "上周二" },
    { value: "lastWednesday", label: "上周三" },
    { value: "lastThursday", label: "上周四" },
    { value: "lastFriday", label: "上周五" },
    { value: "lastSaturday", label: "上周六" },
    { value: "lastSunday", label: "上周日" },
  ],
  month: [
    { value: "thisMonth", label: "本月" },
    { value: "thisMonthBegin", label: "本月月初" },
    { value: "thisMonthEnd", label: "本月月末" },
    { value: "lastMonth", label: "上月" },
    { value: "lastMonthBegin", label: "上月月初" },
    { value: "lastMonthEnd", label: "上月月末" },
  ],
};

function DependentItemEditor({
  item,
  projectCode,
  workflows,
  disabled,
  idPrefix,
  onChange,
}: {
  item: DependentItem;
  projectCode: number;
  workflows: WorkflowDefinitionRecord[];
  disabled: boolean;
  idPrefix: string;
  onChange: (patch: Partial<DependentItem>) => void;
}) {
  const tasksQuery = useQuery({
    queryKey: ["workflow-logic-dependent-tasks", projectCode, item.definitionCode],
    queryFn: () => listWorkflowTasks(item.definitionCode),
    enabled: item.dependentType === "DEPENDENT_ON_TASK" && item.definitionCode > 0,
    staleTime: 30_000,
  });
  const tasks = tasksQuery.data ?? [];
  const selectedWorkflow = workflows.find((workflow) => workflow.code === item.definitionCode);
  const taskOptions = [
    { value: "-1", label: "该工作流全部任务" },
    ...tasks.map((task: WorkflowTaskRecord) => ({
      value: String(task.code),
      label: task.name + "（" + task.taskType + "）",
    })),
  ];

  const handleWorkflowChange = (value: string) => {
    const workflow = workflows.find((item) => item.code === Number(value));
    onChange({
      projectCode: workflow?.projectCode ?? projectCode,
      definitionCode: Number(value),
      depTaskCode: 0,
    });
  };

  const handleTypeChange = (value: string) => {
    const dependentType = value as DependentType;
    onChange({
      dependentType,
      depTaskCode: 0,
    });
  };

  const handleCycleChange = (value: string) => {
    const cycle = value as DependentItem["cycle"];
    const firstDate = DATE_OPTIONS[cycle]?.[0]?.value ?? "today";
    onChange({ cycle, dateValue: firstDate });
  };

  return (
    <div className="rounded-md border border-[#e4e7ec] bg-[#fcfcfd] p-3">
      <div className="grid grid-cols-2 gap-3">
        <LogicField label="依赖类型" required>
          <LogicSelect
            id={idPrefix + "-type"}
            value={item.dependentType}
            options={DEPENDENT_TYPE_OPTIONS}
            placeholder="选择依赖类型"
            disabled={disabled}
            onChange={handleTypeChange}
          />
        </LogicField>
        <LogicField label="依赖工作流" required>
          <LogicSelect
            id={idPrefix + "-workflow"}
            value={item.definitionCode > 0 ? String(item.definitionCode) : ""}
            options={workflows.map((workflow) => ({
              value: String(workflow.code),
              label: workflow.name + "（" + workflow.code + "）",
            }))}
            placeholder="选择工作流"
            disabled={disabled || workflows.length === 0}
            onChange={handleWorkflowChange}
          />
        </LogicField>
        {item.dependentType === "DEPENDENT_ON_TASK" ? (
          <LogicField label="依赖任务" required>
            <LogicSelect
              id={idPrefix + "-task"}
              value={item.depTaskCode !== 0 ? String(item.depTaskCode) : ""}
              options={taskOptions}
              placeholder={tasksQuery.isPending ? "正在加载任务..." : "选择任务"}
              disabled={disabled || item.definitionCode <= 0 || tasksQuery.isPending}
              onChange={(value) => onChange({ depTaskCode: Number(value) })}
            />
          </LogicField>
        ) : (
          <LogicField label="依赖对象">
            <div className="flex h-7 items-center rounded-md border border-[#e4e7ec] bg-[#f9fafb] px-2.5 text-xs text-[#667085]">
              整个工作流完成
            </div>
          </LogicField>
        )}
        <LogicField label="依赖周期">
          <LogicSelect
            id={idPrefix + "-cycle"}
            value={item.cycle}
            options={CYCLE_OPTIONS}
            placeholder="选择周期"
            disabled={disabled}
            onChange={handleCycleChange}
          />
        </LogicField>
        <LogicField label="周期日期">
          <LogicSelect
            id={idPrefix + "-date"}
            value={item.dateValue}
            options={DATE_OPTIONS[item.cycle] ?? DATE_OPTIONS.day}
            placeholder="选择日期"
            disabled={disabled}
            onChange={(value) => onChange({ dateValue: value })}
          />
        </LogicField>
        <LogicField label="期望结果">
          <LogicSelect
            id={idPrefix + "-result"}
            value={item.dependResult}
            options={RESULT_OPTIONS}
            placeholder="选择期望结果"
            disabled={disabled}
            onChange={(value) => onChange({ dependResult: value as DependResult })}
          />
        </LogicField>
        <div className="col-span-2 flex items-center justify-between border-t border-[#eaecf0] pt-2">
          <span className="text-[11px] text-[#98A2B3]">
            {selectedWorkflow ? "工作流版本 " + selectedWorkflow.version + " · " + selectedWorkflow.releaseState : "请先选择依赖工作流"}
          </span>
          <label className="flex items-center gap-2 text-xs text-[#667085]">
            <input
              type="checkbox"
              checked={item.parameterPassing}
              disabled={disabled}
              onChange={(event) => onChange({ parameterPassing: event.target.checked })}
              className="size-3.5 accent-[var(--yak-color-primary)]"
            />
            传递依赖参数
          </label>
        </div>
      </div>
    </div>
  );
}

function DependentForm({
  projectCode,
  params,
  onChange,
  disabled,
}: LogicTaskFormProps & {
  projectCode: number;
}) {
  const workflowsQuery = useQuery({
    queryKey: ["workflow-logic-dependent-definitions", projectCode],
    queryFn: () => listWorkflowDefinitions({ pageNo: 1, pageSize: 200 }),
    staleTime: 60_000,
  });
  const workflows = workflowsQuery.data?.totalList ?? [];
  const normalized = normalizeDependentTaskParams(params);
  const dependence = normalized.dependence;

  const updateGroups = (dependTaskList: DependentGroup[]) => {
    onChange({
      ...params,
      dependence: { ...dependence, dependTaskList },
    });
  };

  const updateGroup = (index: number, patch: Partial<DependentGroup>) => {
    updateGroups(dependence.dependTaskList.map((group, itemIndex) =>
      itemIndex === index ? { ...group, ...patch } : group,
    ));
  };

  const updateItem = (
    groupIndex: number,
    itemIndex: number,
    patch: Partial<DependentItem>,
  ) => {
    updateGroup(groupIndex, {
      dependItemList: dependence.dependTaskList[groupIndex].dependItemList.map((item, index) =>
        index === itemIndex ? { ...item, ...patch } : item,
      ),
    });
  };

  return (
    <div className="space-y-3">
      <LogicSection
        title="依赖对象"
        description="可以配置工作流完成或指定任务完成作为依赖条件，支持多个条件组。"
        action={
          <Button
            size="sm"
            variant="secondary"
            isDisabled={disabled}
            onClick={() => updateGroups([
              ...dependence.dependTaskList,
              {
                relation: "AND",
                dependItemList: [createDependentItem(projectCode)],
              },
            ])}
          >
            添加依赖组
          </Button>
        }
      >
        <div className="space-y-3">
          <LogicField label="条件组关系">
            <LogicSelect
              id="dependent-relation"
              value={dependence.relation}
              options={RELATION_OPTIONS}
              placeholder="选择条件关系"
              disabled={disabled}
              onChange={(value) => onChange({
                ...params,
                dependence: { ...dependence, relation: value as DependentRelation },
              })}
            />
          </LogicField>
          {workflowsQuery.isError ? (
            <p className="text-xs text-[#b42318]">工作流列表加载失败，请刷新后重试</p>
          ) : null}
          {dependence.dependTaskList.length === 0 ? (
            <div className="rounded-md border border-dashed border-[#d0d5dd] bg-[#f9fafb] px-3 py-6 text-center text-xs text-[#667085]">
              暂无依赖对象，请添加依赖组
            </div>
          ) : null}
          {dependence.dependTaskList.map((group, groupIndex) => (
            <section key={groupIndex} className="rounded-md border border-[#e4e7ec] bg-white p-3">
              <div className="mb-3 flex items-center justify-between gap-2">
                <span className="text-xs font-semibold text-[#344054]">依赖组 {groupIndex + 1}</span>
                <div className="flex items-center gap-2">
                  <div className="w-[150px]">
                    <LogicSelect
                      id={"dependent-group-relation-" + groupIndex}
                      value={group.relation}
                      options={RELATION_OPTIONS}
                      placeholder="组内关系"
                      disabled={disabled}
                      onChange={(value) => updateGroup(groupIndex, { relation: value as DependentRelation })}
                    />
                  </div>
                  <Button
                    size="sm"
                    variant="ghost"
                    isDisabled={disabled}
                    onClick={() => updateGroups(dependence.dependTaskList.filter((_item, index) => index !== groupIndex))}
                  >
                    删除组
                  </Button>
                </div>
              </div>
              <div className="space-y-2">
                {group.dependItemList.map((item, itemIndex) => (
                  <div key={itemIndex} className="space-y-2">
                    <DependentItemEditor
                      item={item}
                      projectCode={projectCode}
                      workflows={workflows}
                      disabled={disabled}
                      idPrefix={"dependent-" + groupIndex + "-" + itemIndex}
                      onChange={(patch) => updateItem(groupIndex, itemIndex, patch)}
                    />
                    <div className="flex justify-end">
                      <Button
                        size="sm"
                        variant="ghost"
                        isDisabled={disabled}
                        onClick={() => updateGroup(groupIndex, {
                          dependItemList: group.dependItemList.filter((_item, index) => index !== itemIndex),
                        })}
                      >
                        删除依赖项
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
              <div className="mt-3">
                <Button
                  size="sm"
                  variant="secondary"
                  isDisabled={disabled}
                  onClick={() => updateGroup(groupIndex, {
                    dependItemList: [
                      ...group.dependItemList,
                      createDependentItem(projectCode),
                    ],
                  })}
                >
                  添加依赖项
                </Button>
              </div>
            </section>
          ))}
        </div>
      </LogicSection>

      <LogicSection title="运行策略" description="检查间隔单位为秒，失败等待时间单位为分钟。">
        <div className="space-y-3">
          <LogicField label="检查间隔" required>
            <Input
              size="small"
              variant="outlined"
              type="number"
              min={1}
              value={String(dependence.checkInterval)}
              disabled={disabled}
              onChange={(event) => onChange({
                ...params,
                dependence: { ...dependence, checkInterval: Number(event.target.value) },
              })}
            />
          </LogicField>
          <LogicField label="依赖失败策略">
            <LogicSelect
              id="dependent-failure-policy"
              value={dependence.failurePolicy}
              options={[
                { value: "DEPENDENT_FAILURE_FAILURE", label: "依赖失败则当前任务失败" },
                { value: "DEPENDENT_FAILURE_WAITING", label: "继续等待依赖恢复" },
              ]}
              placeholder="选择失败策略"
              disabled={disabled}
              onChange={(value) => onChange({
                ...params,
                dependence: { ...dependence, failurePolicy: value as DependentFailurePolicy },
              })}
            />
          </LogicField>
          {dependence.failurePolicy === "DEPENDENT_FAILURE_WAITING" ? (
            <LogicField label="失败等待时间" required>
              <Input
                size="small"
                variant="outlined"
                type="number"
                min={1}
                value={String(dependence.failureWaitingTime)}
                disabled={disabled}
                onChange={(event) => onChange({
                  ...params,
                  dependence: { ...dependence, failureWaitingTime: Number(event.target.value) },
                })}
              />
            </LogicField>
          ) : null}
        </div>
      </LogicSection>
    </div>
  );
}

export function DependentEditor({
  workflowCode,
  task,
  taskName,
  readOnly,
  onApplyTask,
  onDirtyChange,
  onBackToWorkflow,
}: {
  workflowCode: number;
  task: WorkflowTaskRecord;
  taskName: string;
  readOnly: boolean;
  onApplyTask: (task: WorkflowTaskRecord) => boolean;
  onDirtyChange: (dirty: boolean) => void;
  onBackToWorkflow: () => void;
}) {
  const runConfig = useGenericLogicTaskRunConfig(task, readOnly);
  return (
    <LogicTaskEditor
      workflowCode={workflowCode}
      task={task}
      taskName={taskName}
      taskType="DEPENDENT"
      readOnly={readOnly}
      runConfig={runConfig}
      onApplyTask={onApplyTask}
      onDirtyChange={onDirtyChange}
      onBackToWorkflow={onBackToWorkflow}
      validateParams={validateDependentTaskParams}
    >
      {(formProps) => (
        <DependentForm
          {...formProps}
          projectCode={task.projectCode}
        />
      )}
    </LogicTaskEditor>
  );
}

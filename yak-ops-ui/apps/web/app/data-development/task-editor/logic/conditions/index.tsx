import { AlertCircle, CheckCircle2, Plus, Trash2, XCircle } from "lucide-react";
import { Button } from "@yak-ops/yak-ui";

import type { WorkflowTaskRecord } from "@/service/workflow/definition";

import { LogicTaskEditor, type LogicTaskFormProps } from "../shared/logic-task-editor";
import { LogicSection, LogicSelect } from "../shared/logic-form-controls";
import {
  buildConditionLogicExpression,
  createConditionDependentItem,
  normalizeConditionsTaskParams,
  validateConditionsTaskParams,
} from "./adapter";
import type {
  ConditionDependentGroup,
  ConditionRelation,
  ConditionTaskOption,
  ConditionTaskStatus,
} from "./types";

const RELATION_OPTIONS = [
  { value: "AND", label: "全部满足（AND）" },
  { value: "OR", label: "满足其一（OR）" },
];

const STATUS_OPTIONS = [
  { value: "SUCCESS", label: "成功" },
  { value: "FAILURE", label: "失败" },
  { value: "FORCED_SUCCESS", label: "强制成功" },
  { value: "KILL", label: "已终止" },
  { value: "PAUSE", label: "已暂停" },
];

const STATUS_LABELS: Record<ConditionTaskStatus, string> = {
  SUCCESS: "成功",
  FAILURE: "失败",
  FORCED_SUCCESS: "强制成功",
  KILL: "已终止",
  PAUSE: "已暂停",
};

function getTaskOptions(
  upstreamTasks: ConditionTaskOption[],
  selectedTaskCode: number,
  occupiedTaskCodes: number[] = [],
) {
  const occupied = new Set(occupiedTaskCodes.filter((code) => code !== selectedTaskCode));
  const options: Array<{ value: string; label: string; disabled?: boolean }> =
    upstreamTasks.map((task) => ({
      value: String(task.code),
      label: task.name + "（" + task.taskType + "）",
      disabled: occupied.has(task.code),
    }));

  if (
    selectedTaskCode > 0 &&
    !upstreamTasks.some((task) => task.code === selectedTaskCode)
  ) {
    options.push({
      value: String(selectedTaskCode),
      label: "任务 #" + selectedTaskCode + "（已不在上游连线中）",
      disabled: true,
    });
  }

  return options;
}

function RelationConnector({
  relation,
  disabled,
  onToggle,
}: {
  relation: ConditionRelation;
  disabled: boolean;
  onToggle: () => void;
}) {
  const nextRelation = relation === "AND" ? "OR" : "AND";

  return (
    <div
      className="flex items-center justify-center gap-2 py-1"
      role="group"
      aria-label="条件组之间的逻辑关系"
    >
      <span className="h-px w-8 bg-[#e4e7ec]" />
      <span className="text-[10px] text-[#98A2B3]">组间</span>
      <Button
        size="sm"
        variant="secondary"
        className="h-6 min-w-12 px-2 text-[10px] font-semibold"
        isDisabled={disabled}
        title={"点击切换为 " + nextRelation}
        aria-label={"当前组间关系为 " + relation + "，点击切换为 " + nextRelation}
        onClick={onToggle}
      >
        {relation}
      </Button>
      <span className="h-px w-8 bg-[#e4e7ec]" />
    </div>
  );
}

function BranchTargetSummary({
  title,
  targets,
  kind,
}: {
  title: string;
  targets: ConditionTaskOption[];
  kind: "success" | "failed";
}) {
  const success = kind === "success";
  const Icon = success ? CheckCircle2 : XCircle;
  const tone = success
    ? {
        border: "border-[#bbf7d0]",
        background: "bg-[#f0fdf4]",
        title: "text-[#15803d]",
        text: "text-[#166534]",
        chip: "bg-white text-[#166534]",
      }
    : {
        border: "border-[#fecaca]",
        background: "bg-[#fff7f7]",
        title: "text-[#b42318]",
        text: "text-[#b42318]",
        chip: "bg-white text-[#b42318]",
      };

  return (
    <div className={"min-w-0 rounded-md border px-3 py-2 " + tone.border + " " + tone.background}>
      <div className="flex items-center gap-2">
        <Icon size={14} className={tone.title} />
        <span className={"text-xs font-semibold " + tone.title}>{title}</span>
        <span className={"ml-auto text-[11px] tabular-nums " + tone.text}>
          {targets.length} 个节点
        </span>
      </div>
      <div className="mt-1.5 flex min-h-5 flex-wrap items-center gap-1">
        {targets.length > 0 ? (
          targets.map((target) => (
            <span
              key={target.code}
              className={"max-w-full truncate rounded px-2 py-1 text-[10px] " + tone.chip}
              title={target.name}
            >
              {target.name}
            </span>
          ))
        ) : (
          <span className="text-[11px] text-[#98A2B3]">未连接</span>
        )}
      </div>
    </div>
  );
}

function ConditionsForm({
  upstreamTasks,
  successTargets,
  failedTargets,
  unassignedTargetCount,
  params,
  onChange,
  disabled,
  onBackToWorkflow,
}: LogicTaskFormProps & {
  upstreamTasks: ConditionTaskOption[];
  successTargets: ConditionTaskOption[];
  failedTargets: ConditionTaskOption[];
  unassignedTargetCount: number;
  onBackToWorkflow: () => void;
}) {
  const normalized = normalizeConditionsTaskParams(params);
  const dependence = normalized.dependence;
  const groups = dependence.dependTaskList;
  const expression = buildConditionLogicExpression(normalized, upstreamTasks);

  const updateGroups = (dependTaskList: ConditionDependentGroup[]) => {
    onChange({
      ...normalized,
      dependence: { ...dependence, dependTaskList },
    });
  };

  const updateGroup = (index: number, patch: Partial<ConditionDependentGroup>) => {
    updateGroups(groups.map((group, itemIndex) =>
      itemIndex === index ? { ...group, ...patch } : group,
    ));
  };

  const updateItem = (
    groupIndex: number,
    itemIndex: number,
    patch: Partial<ConditionDependentGroup["dependItemList"][number]>,
  ) => {
    const group = groups[groupIndex];
    if (!group) return;

    updateGroup(groupIndex, {
      dependItemList: group.dependItemList.map((item, index) =>
        index === itemIndex ? { ...item, ...patch } : item,
      ),
    });
  };

  const addGroup = () => {
    updateGroups([
      ...groups,
      {
        relation: "AND",
        dependItemList: [createConditionDependentItem()],
      },
    ]);
  };

  const toggleGroupRelation = () => onChange({
    ...normalized,
    dependence: {
      ...dependence,
      relation: dependence.relation === "AND" ? "OR" : "AND",
    },
  });

  const expressionPlaceholder = groups.length === 0
    ? "添加条件组后显示判断表达式"
    : upstreamTasks.length === 0
      ? "连接上游任务后显示判断表达式"
      : "请完成所有判断条件";

  return (
    <div className="space-y-2.5">
      <LogicSection
        title="判断规则"
        action={
          <Button size="sm" variant="secondary" isDisabled={disabled} onClick={addGroup}>
            <Plus size={13} />
            添加条件组
          </Button>
        }
      >
        {upstreamTasks.length === 0 ? (
          <div className="mb-2 flex items-center justify-between gap-2 rounded-md border border-dashed border-[#d0d5dd] px-3 py-2">
            <span className="text-xs text-[#667085]">当前节点尚未连接上游任务</span>
            <Button size="sm" variant="ghost" isDisabled={disabled} onClick={onBackToWorkflow}>
              返回画布
            </Button>
          </div>
        ) : null}

        {groups.length === 0 ? (
          <div className="rounded-md border border-dashed border-[#d0d5dd] px-3 py-4 text-center text-xs text-[#98A2B3]">
            暂无条件组
          </div>
        ) : (
          <div>
            {groups.map((group, groupIndex) => (
              <div key={"conditions-group-" + groupIndex}>
                <section className="rounded-md border border-[#e4e7ec] bg-[#fcfcfd] p-2.5">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <span className="flex size-6 items-center justify-center rounded bg-[#eef2ff] text-[10px] font-semibold text-[#3a3d51]">
                        {groupIndex + 1}
                      </span>
                      <span className="text-xs font-semibold text-[#344054]">
                        条件组 {groupIndex + 1}
                      </span>
                      {group.dependItemList.length > 1 ? (
                        <span className="text-[10px] text-[#98A2B3]">组内关系</span>
                      ) : null}
                    </div>
                    <div className="flex items-center gap-2">
                      {group.dependItemList.length > 1 ? (
                        <div className="w-[152px]">
                          <LogicSelect
                            id={"conditions-group-relation-" + groupIndex}
                            value={group.relation}
                            options={RELATION_OPTIONS}
                            placeholder="组内关系"
                            disabled={disabled}
                            onChange={(value) => updateGroup(groupIndex, {
                              relation: value as ConditionRelation,
                            })}
                          />
                        </div>
                      ) : null}
                      <Button
                        size="sm"
                        variant="ghost"
                        isDisabled={disabled}
                        aria-label={"删除条件组 " + (groupIndex + 1)}
                        onClick={() => updateGroups(
                          groups.filter((_item, index) => index !== groupIndex),
                        )}
                      >
                        <Trash2 size={13} />
                        删除
                      </Button>
                    </div>
                  </div>

                  {group.dependItemList.length === 0 ? (
                    <div className="mt-2 rounded border border-dashed border-[#e4e7ec] bg-white px-3 py-3 text-center text-[11px] text-[#98A2B3]">
                      暂无判断条件
                    </div>
                  ) : (
                    <div className="mt-2 divide-y divide-[#eef0f2]">
                      {group.dependItemList.map((item, itemIndex) => {
                        const occupiedTaskCodes = group.dependItemList
                          .filter((_other, index) => index !== itemIndex)
                          .map((other) => other.depTaskCode);

                        return (
                          <div
                            key={"conditions-item-" + groupIndex + "-" + itemIndex}
                            className="grid grid-cols-[minmax(0,1fr)_160px_34px] items-center gap-2 py-2 first:pt-0 last:pb-0 max-md:grid-cols-1"
                          >
                            <LogicSelect
                              id={"conditions-task-" + groupIndex + "-" + itemIndex}
                              value={item.depTaskCode > 0 ? String(item.depTaskCode) : ""}
                              options={getTaskOptions(
                                upstreamTasks,
                                item.depTaskCode,
                                occupiedTaskCodes,
                              )}
                              placeholder={upstreamTasks.length ? "选择上游任务" : "暂无上游任务"}
                              disabled={disabled || upstreamTasks.length === 0}
                              onChange={(value) => updateItem(groupIndex, itemIndex, {
                                depTaskCode: Number(value),
                              })}
                            />
                            <LogicSelect
                              id={"conditions-status-" + groupIndex + "-" + itemIndex}
                              value={item.status}
                              options={STATUS_OPTIONS}
                              placeholder="执行状态"
                              disabled={disabled}
                              onChange={(value) => updateItem(groupIndex, itemIndex, {
                                status: value as ConditionTaskStatus,
                              })}
                            />
                            <Button
                              size="sm"
                              variant="ghost"
                              isDisabled={disabled}
                              aria-label={"删除条件组 " + (groupIndex + 1) + " 的第 " + (itemIndex + 1) + " 项"}
                              onClick={() => updateGroup(groupIndex, {
                                dependItemList: group.dependItemList.filter(
                                  (_other, index) => index !== itemIndex,
                                ),
                              })}
                            >
                              <Trash2 size={13} />
                            </Button>
                          </div>
                        );
                      })}
                    </div>
                  )}

                  <div className="mt-2">
                    <Button
                      size="sm"
                      variant="secondary"
                      isDisabled={disabled}
                      onClick={() => updateGroup(groupIndex, {
                        dependItemList: [
                          ...group.dependItemList,
                          createConditionDependentItem(),
                        ],
                      })}
                    >
                      <Plus size={13} />
                      添加判断条件
                    </Button>
                  </div>
                </section>

                {groupIndex < groups.length - 1 ? (
                  <RelationConnector
                    relation={dependence.relation}
                    disabled={disabled}
                    onToggle={toggleGroupRelation}
                  />
                ) : null}
              </div>
            ))}
          </div>
        )}
      </LogicSection>

      <LogicSection
        title="逻辑表达式"
        action={<span className="text-[10px] text-[#98A2B3]">实时预览</span>}
      >
        <div className="rounded-md border border-[#eaecf0] bg-[#f9fafb] px-3 py-2.5">
          {expression ? (
            <code className="whitespace-normal break-words text-xs leading-6 text-[#344054]">
              {expression}
            </code>
          ) : (
            <span className="text-xs text-[#98A2B3]">{expressionPlaceholder}</span>
          )}
        </div>
      </LogicSection>

      <LogicSection title="执行去向">
        <div className="grid grid-cols-2 gap-2 max-md:grid-cols-1">
          <BranchTargetSummary
            title="成功分支"
            targets={successTargets}
            kind="success"
          />
          <BranchTargetSummary
            title="失败分支"
            targets={failedTargets}
            kind="failed"
          />
        </div>
        {unassignedTargetCount > 0 ? (
          <div className="mt-2 flex items-center gap-2 rounded-md border border-[#fed7aa] bg-[#fff7ed] px-2.5 py-2 text-[11px] text-[#9a3412]">
            <AlertCircle size={14} className="shrink-0" />
            {unassignedTargetCount} 条连线未归属分支
          </div>
        ) : null}
      </LogicSection>
    </div>
  );
}

export function ConditionsEditor({
  workflowCode,
  task,
  taskName,
  readOnly,
  onApplyTask,
  onDirtyChange,
  onBackToWorkflow,
  upstreamTasks,
  successTargets,
  failedTargets,
  unassignedTargetCount,
}: {
  workflowCode: number;
  task: WorkflowTaskRecord;
  taskName: string;
  readOnly: boolean;
  onApplyTask: (task: WorkflowTaskRecord) => boolean;
  onDirtyChange: (dirty: boolean) => void;
  onBackToWorkflow: () => void;
  upstreamTasks: ConditionTaskOption[];
  successTargets: ConditionTaskOption[];
  failedTargets: ConditionTaskOption[];
  unassignedTargetCount: number;
}) {
  const applyNormalizedTask = (nextTask: WorkflowTaskRecord) => onApplyTask({
    ...nextTask,
    taskParams: normalizeConditionsTaskParams(nextTask.taskParams),
  });

  return (
    <LogicTaskEditor
      workflowCode={workflowCode}
      task={task}
      taskName={taskName}
      taskType="CONDITIONS"
      readOnly={readOnly}
      onApplyTask={applyNormalizedTask}
      onDirtyChange={onDirtyChange}
      onBackToWorkflow={onBackToWorkflow}
      validateParams={(value) =>
        validateConditionsTaskParams(value, upstreamTasks.map((item) => item.code))
      }
    >
      {(formProps) => (
        <ConditionsForm
          {...formProps}
          upstreamTasks={upstreamTasks}
          successTargets={successTargets}
          failedTargets={failedTargets}
          unassignedTargetCount={unassignedTargetCount}
          onBackToWorkflow={onBackToWorkflow}
        />
      )}
    </LogicTaskEditor>
  );
}

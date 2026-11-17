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
    <div className={"w-full min-w-0 rounded-md border px-3 py-2 " + tone.border + " " + tone.background}>
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
}: LogicTaskFormProps & {
  upstreamTasks: ConditionTaskOption[];
  successTargets: ConditionTaskOption[];
  failedTargets: ConditionTaskOption[];
  unassignedTargetCount: number;
}) {
  const normalized = normalizeConditionsTaskParams(params);
  const dependence = normalized.dependence;
  // Render one initial input node without mutating taskParams until the user edits it.
  const groups: ConditionDependentGroup[] = dependence.dependTaskList.length > 0
    ? dependence.dependTaskList
    : [{ relation: "AND", dependItemList: [createConditionDependentItem()] }];
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

  const rowCount = Math.max(groups.length, 2);
  const groupRows = groups.length === 1
    ? [{ group: groups[0], groupIndex: 0, row: `1 / span ${rowCount}` }]
    : groups.map((group, groupIndex) => ({
        group,
        groupIndex,
        row: String(groupIndex + 1),
      }));

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div>
          <div className="text-xs font-semibold text-[#344054]">CONDITIONS 判断流程</div>
        </div>
        <Button size="sm" variant="secondary" isDisabled={disabled} onClick={addGroup}>
          <Plus size={13} />
          新增条件组
        </Button>
      </div>

      {unassignedTargetCount > 0 ? (
        <div className="flex items-center gap-2 rounded-md border border-[#fed7aa] bg-[#fff7ed] px-2.5 py-2 text-[11px] text-[#9a3412]">
          <AlertCircle size={14} className="shrink-0" />
          {unassignedTargetCount} 条连线未归属成功或失败分支
        </div>
      ) : null}

      <div className="overflow-x-auto rounded-xl border border-[#eaecf0] bg-[#fbfcfe] p-3">
        <div className="grid min-w-[1040px] grid-cols-[minmax(310px,1.2fr)_104px_236px_104px_minmax(260px,1fr)] items-stretch">
          <div className="pb-2 text-[11px] font-semibold text-[#667085]">输入 · 条件组</div>
          <div />
          <div className="pb-2 text-center text-[11px] font-semibold text-[#667085]">判断 · CONDITIONS</div>
          <div />
          <div className="pb-2 text-[11px] font-semibold text-[#667085]">输出 · 执行分支</div>
        </div>

        <div
          className="grid min-w-[1040px] grid-cols-[minmax(310px,1.2fr)_104px_236px_104px_minmax(260px,1fr)] items-stretch"
          style={{ gridTemplateRows: `repeat(${rowCount}, minmax(164px, auto))` }}
        >
          {groups.length === 0 ? (
            <div
              className="flex items-center py-2"
              style={{ gridColumn: 1, gridRow: `1 / span ${rowCount}` }}
            >
              <div className="flex min-h-36 w-full flex-col items-center justify-center rounded-lg border border-dashed border-[#d0d5dd] bg-white px-3 text-center">
                <span className="text-xs font-medium text-[#667085]">还没有输入条件</span>
                <Button className="mt-2" size="sm" variant="secondary" isDisabled={disabled} onClick={addGroup}>
                  <Plus size={12} />
                  添加第一个条件组
                </Button>
              </div>
            </div>
          ) : groupRows.map(({ group, groupIndex, row }) => (
            <div
              key={"conditions-input-" + groupIndex}
              className="flex min-w-0 items-center py-2"
              style={{ gridColumn: 1, gridRow: row }}
            >
              <section className="w-full rounded-lg border border-[#dfe3ea] bg-white p-2.5 shadow-[0_1px_2px_rgba(16,24,40,0.04)]">
                <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span className="flex size-6 items-center justify-center rounded-md bg-[#eef2ff] text-[10px] font-semibold text-[#3a3d51]">
                      {groupIndex + 1}
                    </span>
                    <span className="text-xs font-semibold text-[#344054]">条件组 {groupIndex + 1}</span>
                  </div>
                  <div className="flex items-center gap-1.5">
                    {group.dependItemList.length > 1 ? (
                      <div className="w-[142px]">
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
                      isDisabled={disabled || groups.length <= 1}
                      title={groups.length <= 1 ? "至少保留一个条件组" : "删除条件组"}
                      aria-label={"删除条件组 " + (groupIndex + 1)}
                      onClick={() => updateGroups(groups.filter((_item, index) => index !== groupIndex))}
                    >
                      <Trash2 size={13} />
                    </Button>
                  </div>
                </div>

                <div className="space-y-2">
                  {group.dependItemList.map((item, itemIndex) => {
                    const occupiedTaskCodes = group.dependItemList
                      .filter((_other, index) => index !== itemIndex)
                      .map((other) => other.depTaskCode);
                    return (
                      <div key={"conditions-item-" + groupIndex + "-" + itemIndex} className="rounded-md bg-[#f8f9fb] p-2">
                        <div className="grid grid-cols-[minmax(0,1fr)_minmax(100px,0.72fr)_28px] items-center gap-1.5 max-sm:grid-cols-1">
                          <LogicSelect
                            id={"conditions-task-" + groupIndex + "-" + itemIndex}
                            value={item.depTaskCode > 0 ? String(item.depTaskCode) : ""}
                            options={getTaskOptions(upstreamTasks, item.depTaskCode, occupiedTaskCodes)}
                            placeholder={upstreamTasks.length ? "选择上游任务" : "暂无上游任务"}
                            disabled={disabled || upstreamTasks.length === 0}
                            onChange={(value) => updateItem(groupIndex, itemIndex, { depTaskCode: Number(value) })}
                          />
                          <LogicSelect
                            id={"conditions-status-" + groupIndex + "-" + itemIndex}
                            value={item.status}
                            options={STATUS_OPTIONS}
                            placeholder="执行状态"
                            disabled={disabled}
                            onChange={(value) => updateItem(groupIndex, itemIndex, { status: value as ConditionTaskStatus })}
                          />
                          <Button
                            size="sm"
                            variant="ghost"
                            isDisabled={disabled}
                            aria-label={"删除判断条件 " + (itemIndex + 1)}
                            onClick={() => updateGroup(groupIndex, {
                              dependItemList: group.dependItemList.filter((_other, index) => index !== itemIndex),
                            })}
                          >
                            <Trash2 size={12} />
                          </Button>
                        </div>
                      </div>
                    );
                  })}
                  <Button
                    size="sm"
                    variant="ghost"
                    isDisabled={disabled}
                    onClick={() => updateGroup(groupIndex, {
                      dependItemList: [...group.dependItemList, createConditionDependentItem()],
                    })}
                  >
                    <Plus size={12} />
                    添加判断条件
                  </Button>
                </div>
              </section>
            </div>
          ))}

          {groups.length === 1 ? (
            <div
              className="pointer-events-none relative"
              style={{ gridColumn: 2, gridRow: `1 / span ${rowCount}` }}
            >
              <span className="absolute left-0 right-0 top-1/2 border-t border-dashed border-[#aeb7c4]" />
            </div>
          ) : groups.length > 1 ? (
            <>
              <div
                className="pointer-events-none relative"
                style={{ gridColumn: 2, gridRow: `1 / span ${rowCount}` }}
              >
                <span className="absolute left-1/2 top-0 bottom-0 border-l border-dashed border-[#aeb7c4]" />
                <span className="absolute left-1/2 right-0 top-1/2 border-t border-dashed border-[#aeb7c4]" />
              </div>
              {groups.map((_group, groupIndex) => (
                <div
                  key={"conditions-input-link-" + groupIndex}
                  className="pointer-events-none relative flex items-center justify-center"
                  style={{ gridColumn: 2, gridRow: groupIndex + 1 }}
                >
                  <span className="absolute left-0 right-1/2 top-1/2 border-t border-dashed border-[#aeb7c4]" />
                  <span className="relative z-10 rounded-full border border-[#d0d5dd] bg-white px-1.5 py-0.5 text-[9px] font-semibold text-[#475467]">
                    {dependence.relation}
                  </span>
                </div>
              ))}
            </>
          ) : null}

          <div
            className="flex min-w-0 items-center py-3"
            style={{ gridColumn: 3, gridRow: `1 / span ${rowCount}` }}
          >
            <div className="w-full rounded-xl border border-[#c7d2fe] bg-[#f5f6ff] p-3 shadow-[0_3px_10px_rgba(58,61,81,0.07)]">
              <div className="mb-3 flex items-center justify-center gap-2">
                <span className="flex size-9 items-center justify-center rounded-lg bg-[#e7eaff] text-[#3a3d51]">
                  <AlertCircle size={18} />
                </span>
                <div>
                  <div className="text-xs font-semibold text-[#344054]">CONDITIONS</div>
                </div>
              </div>
              <div className="rounded-md border border-[#e4e7ec] bg-white p-2.5">
                <div className="mb-1.5 text-[10px] font-medium text-[#98A2B3]">逻辑表达式</div>
                {expression ? (
                  <code className="block break-words text-[10px] leading-5 text-[#344054]">{expression}</code>
                ) : (
                  <span className="text-[10px] leading-5 text-[#98A2B3]">—</span>
                )}
              </div>
              <div className="mt-2 flex items-center justify-center gap-2 text-[10px] text-[#667085]">
                <span>{groups.length} 个条件组</span>
                <span className="text-[#d0d5dd]">·</span>
                <span>{groups.reduce((sum, group) => sum + group.dependItemList.length, 0)} 条判断</span>
              </div>
            </div>
          </div>

          <div
            className="pointer-events-none relative"
            style={{ gridColumn: 4, gridRow: `1 / span ${rowCount}` }}
          >
            <span className="absolute left-0 top-1/2 h-0 w-1/2 border-t border-dashed border-[#aeb7c4]" />
            <span className="absolute left-1/2 top-0 bottom-0 border-l border-dashed border-[#aeb7c4]" />
          </div>
          {Array.from({ length: rowCount }, (_item, rowIndex) => (
            <div
              key={"conditions-output-link-" + rowIndex}
              className="pointer-events-none relative"
              style={{ gridColumn: 4, gridRow: rowIndex + 1 }}
            >
              {rowIndex === 0 ? (
                <>
                  <span className="absolute left-1/2 right-0 top-1/2 border-t border-dashed border-[#aeb7c4]" />
                  <span className="absolute left-1/2 top-1/2 bottom-0 border-l border-dashed border-[#aeb7c4]" />
                </>
              ) : rowIndex === rowCount - 1 ? (
                <>
                  <span className="absolute left-1/2 right-0 top-1/2 border-t border-dashed border-[#aeb7c4]" />
                  <span className="absolute left-1/2 top-0 bottom-1/2 border-l border-dashed border-[#aeb7c4]" />
                </>
              ) : (
                <span className="absolute left-1/2 top-0 bottom-0 border-l border-dashed border-[#aeb7c4]" />
              )}
            </div>
          ))}

          <div
            className="flex min-w-0 items-center py-2"
            style={{ gridColumn: 5, gridRow: 1 }}
          >
            <BranchTargetSummary title="成功分支" targets={successTargets} kind="success" />
          </div>
          <div
            className="flex min-w-0 items-center py-2"
            style={{ gridColumn: 5, gridRow: rowCount }}
          >
            <BranchTargetSummary title="失败分支" targets={failedTargets} kind="failed" />
          </div>
        </div>
      </div>
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
        />
      )}
    </LogicTaskEditor>
  );
}

import { AlertCircle, CheckCircle2, Plus, Trash2, XCircle } from "lucide-react";
import { Button } from "@yak-ops/yak-ui";

import type { WorkflowTaskRecord } from "@/service/workflow/definition";

import { LogicTaskEditor, type LogicTaskFormProps } from "../shared/logic-task-editor";
import { LogicField, LogicSection, LogicSelect } from "../shared/logic-form-controls";
import {
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
  { value: "AND", label: "同时满足（AND）" },
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
) {
  const options = upstreamTasks.map((task) => ({
    value: String(task.code),
    label: task.name + "（" + task.taskType + "）",
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

function BranchTargetCard({
  title,
  description,
  targets,
  kind,
}: {
  title: string;
  description: string;
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
    <div className={"min-w-0 rounded-md border p-3 " + tone.border + " " + tone.background}>
      <div className="flex items-center gap-2">
        <Icon size={15} className={tone.title} />
        <span className={"text-xs font-semibold " + tone.title}>{title}</span>
        <span className={"ml-auto text-[11px] tabular-nums " + tone.text}>
          {targets.length} 个下游节点
        </span>
      </div>
      <p className={"mt-1 text-[11px] leading-4 " + tone.text}>{description}</p>
      {targets.length > 0 ? (
        <div className="mt-2 flex flex-wrap gap-1.5">
          {targets.map((target) => (
            <span
              key={target.code}
              className={"max-w-full truncate rounded px-2 py-1 text-[10px] " + tone.chip}
              title={target.name}
            >
              {target.name}
            </span>
          ))}
        </div>
      ) : (
        <div className={"mt-2 text-[11px] " + tone.text}>尚未连接下游节点，请在画布中拖出该分支连线</div>
      )}
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

  const updateGroups = (dependTaskList: ConditionDependentGroup[]) => {
    onChange({
      ...normalized,
      dependence: { ...dependence, dependTaskList },
    });
  };

  const updateGroup = (index: number, patch: Partial<ConditionDependentGroup>) => {
    updateGroups(dependence.dependTaskList.map((group, itemIndex) =>
      itemIndex === index ? { ...group, ...patch } : group,
    ));
  };

  const updateItem = (
    groupIndex: number,
    itemIndex: number,
    patch: Partial<ConditionDependentGroup["dependItemList"][number]>,
  ) => {
    const group = dependence.dependTaskList[groupIndex];
    if (!group) return;
    updateGroup(groupIndex, {
      dependItemList: group.dependItemList.map((item, index) =>
        index === itemIndex ? { ...item, ...patch } : item,
      ),
    });
  };

  const addGroup = () => {
    updateGroups([
      ...dependence.dependTaskList,
      {
        relation: "AND",
        dependItemList: [createConditionDependentItem()],
      },
    ]);
  };

  return (
    <div className="space-y-3">
      <LogicSection
        title="依赖条件"
        description="只选择当前节点在画布中已连接的上游任务。条件组之间、组内条件分别支持 AND / OR 组合。"
        action={
          <Button size="sm" variant="secondary" isDisabled={disabled} onClick={addGroup}>
            <Plus size={13} />
            添加条件组
          </Button>
        }
      >
        <div className="space-y-3">
          <LogicField label="条件组关系">
            <div className="max-w-[260px]">
              <LogicSelect
                id="conditions-relation"
                value={dependence.relation}
                options={RELATION_OPTIONS}
                placeholder="请选择条件组关系"
                disabled={disabled}
                onChange={(value) => onChange({
                  ...normalized,
                  dependence: { ...dependence, relation: value as ConditionRelation },
                })}
              />
            </div>
          </LogicField>

          {upstreamTasks.length === 0 ? (
            <div className="flex items-start gap-2 rounded-md border border-dashed border-[#d0d5dd] bg-[#f9fafb] px-3 py-3 text-xs text-[#667085]">
              <AlertCircle size={15} className="mt-0.5 shrink-0 text-[#98A2B3]" />
              <div>
                <div className="font-medium text-[#475467]">当前节点还没有上游任务</div>
                <p className="mt-1 leading-5">
                  请先返回工作流画布，将上游节点连接到当前 CONDITIONS 节点，再回来配置依赖条件。
                </p>
              </div>
            </div>
          ) : (
            <div className="text-[11px] text-[#98A2B3]">
              可选上游任务：{upstreamTasks.length} 个。依赖任务来源于当前画布连线，不再展示工作流中的无关节点。
            </div>
          )}

          {dependence.dependTaskList.length === 0 ? (
            <div className="rounded-md border border-dashed border-[#d0d5dd] bg-[#f9fafb] px-3 py-6 text-center text-xs text-[#667085]">
              暂无依赖条件，请添加条件组
            </div>
          ) : null}

          <div className="space-y-3">
            {dependence.dependTaskList.map((group, groupIndex) => (
              <section
                key={groupIndex}
                className="rounded-md border border-[#e4e7ec] bg-[#fcfcfd] p-3"
              >
                <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <span className="flex size-6 items-center justify-center rounded bg-[#eef2ff] text-[10px] font-semibold text-[#3a3d51]">
                      {groupIndex + 1}
                    </span>
                    <span className="text-xs font-semibold text-[#344054]">条件组 {groupIndex + 1}</span>
                    <span className="text-[10px] text-[#98A2B3]">
                      {group.dependItemList.length} 项
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    <div className="w-[150px]">
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
                    <Button
                      size="sm"
                      variant="ghost"
                      isDisabled={disabled}
                      aria-label={"删除条件组 " + (groupIndex + 1)}
                      onClick={() => updateGroups(
                        dependence.dependTaskList.filter((_item, index) => index !== groupIndex),
                      )}
                    >
                      <Trash2 size={13} />
                      删除组
                    </Button>
                  </div>
                </div>

                {group.dependItemList.length === 0 ? (
                  <div className="rounded border border-dashed border-[#e4e7ec] bg-white px-3 py-4 text-center text-[11px] text-[#98A2B3]">
                    该条件组还没有依赖任务，请添加一项
                  </div>
                ) : (
                  <div className="space-y-2">
                    {group.dependItemList.map((item, itemIndex) => (
                      <div
                        key={itemIndex}
                        className="grid grid-cols-[minmax(0,1fr)_160px_34px] items-center gap-2 max-md:grid-cols-1"
                      >
                        <LogicSelect
                          id={"conditions-task-" + groupIndex + "-" + itemIndex}
                          value={item.depTaskCode > 0 ? String(item.depTaskCode) : ""}
                          options={getTaskOptions(upstreamTasks, item.depTaskCode)}
                          placeholder={upstreamTasks.length ? "选择上游任务" : "请先连接上游节点"}
                          disabled={disabled || upstreamTasks.length === 0}
                          onChange={(value) => updateItem(groupIndex, itemIndex, {
                            depTaskCode: Number(value),
                          })}
                        />
                        <LogicSelect
                          id={"conditions-status-" + groupIndex + "-" + itemIndex}
                          value={item.status}
                          options={STATUS_OPTIONS}
                          placeholder="期望执行状态"
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
                              (_item, index) => index !== itemIndex,
                            ),
                          })}
                        >
                          <Trash2 size={13} />
                        </Button>
                      </div>
                    ))}
                  </div>
                )}

                <div className="mt-3">
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
                    添加依赖任务
                  </Button>
                </div>
              </section>
            ))}
          </div>
        </div>
      </LogicSection>

      <LogicSection
        title="执行分支"
        description="分支目标由画布连线决定。成功和失败分支均支持连接多个下游节点。"
      >
        <div className="grid grid-cols-2 gap-3 max-md:grid-cols-1">
          <BranchTargetCard
            title="成功分支"
            description="所有依赖条件计算结果为真时执行"
            targets={successTargets}
            kind="success"
          />
          <BranchTargetCard
            title="失败分支"
            description="依赖条件计算结果为假时执行"
            targets={failedTargets}
            kind="failed"
          />
        </div>
        {unassignedTargetCount > 0 ? (
          <div className="mt-3 flex items-start gap-2 rounded-md border border-[#fed7aa] bg-[#fff7ed] px-3 py-2 text-[11px] leading-5 text-[#9a3412]">
            <AlertCircle size={14} className="mt-0.5 shrink-0" />
            当前有 {unassignedTargetCount} 条分支连线未识别，请返回画布，从成功或失败输出端口重新连接。
          </div>
        ) : null}
        <p className="mt-3 text-[11px] leading-5 text-[#98A2B3]">
          保存工作流前，成功和失败分支都必须至少连接一个下游节点。条件判断结果由运行引擎计算，前端不写入 conditionSuccess。
        </p>
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
        />
      )}
    </LogicTaskEditor>
  );
}

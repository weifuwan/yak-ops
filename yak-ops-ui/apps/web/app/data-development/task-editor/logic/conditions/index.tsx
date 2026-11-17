import { Button } from "@yak-ops/yak-ui";

import type { WorkflowTaskRecord } from "@/service/workflow/definition";

import { LogicTaskEditor, type LogicTaskFormProps } from "../shared/logic-task-editor";
import { LogicField, LogicSection, LogicSelect } from "../shared/logic-form-controls";
import { createConditionDependentItem, normalizeConditionsTaskParams } from "./adapter";
import type { ConditionDependentGroup, ConditionRelation, ConditionTaskStatus } from "./types";

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

function ConditionsForm({
  workflowCode,
  projectCode,
  workflowTasks,
  params,
  onChange,
  disabled,
}: LogicTaskFormProps & {
  workflowCode: number;
  projectCode: number;
  workflowTasks: WorkflowTaskRecord[];
}) {
  const normalized = normalizeConditionsTaskParams(params);
  const dependence = normalized.dependence;

  const updateGroups = (dependTaskList: ConditionDependentGroup[]) => {
    onChange({
      ...params,
      dependence: { ...dependence, dependTaskList },
      conditionResult: normalized.conditionResult,
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
    patch: Record<string, unknown>,
  ) => {
    updateGroup(groupIndex, {
      dependItemList: dependence.dependTaskList[groupIndex].dependItemList.map((item, index) =>
        index === itemIndex ? { ...item, ...patch } : item,
      ),
    });
  };

  const addGroup = () => {
    updateGroups([
      ...dependence.dependTaskList,
      {
        relation: "AND",
        dependItemList: [createConditionDependentItem(projectCode, workflowCode)],
      },
    ]);
  };

  return (
    <div className="space-y-3">
      <LogicSection
        title="依赖条件"
        description="判断当前工作流中指定上游任务的执行状态，支持条件组与组内条件分别设置 AND / OR。"
        action={
          <Button size="sm" variant="secondary" isDisabled={disabled} onClick={addGroup}>
            添加条件组
          </Button>
        }
      >
        <div className="space-y-3">
          <LogicField label="条件组关系">
            <LogicSelect
              id="conditions-relation"
              value={dependence.relation}
              options={RELATION_OPTIONS}
              placeholder="请选择条件组关系"
              disabled={disabled}
              onChange={(value) => onChange({
                ...params,
                dependence: { ...dependence, relation: value as ConditionRelation },
              })}
            />
          </LogicField>

          {dependence.dependTaskList.length === 0 ? (
            <div className="rounded-md border border-dashed border-[#d0d5dd] bg-[#f9fafb] px-3 py-6 text-center text-xs text-[#667085]">
              暂无依赖条件，请添加条件组
            </div>
          ) : null}

          {dependence.dependTaskList.map((group, groupIndex) => (
            <section key={groupIndex} className="rounded-md border border-[#e4e7ec] bg-[#fcfcfd] p-3">
              <div className="mb-3 flex items-center justify-between gap-2">
                <span className="text-xs font-semibold text-[#344054]">条件组 {groupIndex + 1}</span>
                <div className="flex items-center gap-2">
                  <div className="w-[150px]">
                    <LogicSelect
                      id={"conditions-group-relation-" + groupIndex}
                      value={group.relation}
                      options={RELATION_OPTIONS}
                      placeholder="组内关系"
                      disabled={disabled}
                      onChange={(value) => updateGroup(groupIndex, { relation: value as ConditionRelation })}
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
                  <div key={itemIndex} className="grid grid-cols-[minmax(0,1fr)_150px_56px] items-center gap-2">
                    <LogicSelect
                      id={"conditions-task-" + groupIndex + "-" + itemIndex}
                      value={item.depTaskCode > 0 ? String(item.depTaskCode) : ""}
                      options={workflowTasks.map((task) => ({
                        value: String(task.code),
                        label: task.name + "（" + task.taskType + "）",
                      }))}
                      placeholder={workflowTasks.length ? "选择上游任务" : "当前工作流暂无可选任务"}
                      disabled={disabled || workflowTasks.length === 0}
                      onChange={(value) => {
                        const selected = workflowTasks.find((task) => task.code === Number(value));
                        updateItem(groupIndex, itemIndex, {
                          dependentType: "DEPENDENT_ON_TASK",
                          projectCode: selected?.projectCode ?? projectCode,
                          definitionCode: workflowCode,
                          depTaskCode: Number(value),
                        });
                      }}
                    />
                    <LogicSelect
                      id={"conditions-status-" + groupIndex + "-" + itemIndex}
                      value={item.status}
                      options={STATUS_OPTIONS}
                      placeholder="期望状态"
                      disabled={disabled}
                      onChange={(value) => updateItem(groupIndex, itemIndex, { status: value as ConditionTaskStatus })}
                    />
                    <Button
                      size="sm"
                      variant="ghost"
                      isDisabled={disabled}
                      onClick={() => updateGroup(groupIndex, {
                        dependItemList: group.dependItemList.filter((_item, index) => index !== itemIndex),
                      })}
                    >
                      删除
                    </Button>
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
                      createConditionDependentItem(projectCode, workflowCode),
                    ],
                  })}
                >
                  添加依赖任务
                </Button>
              </div>
            </section>
          ))}
        </div>
      </LogicSection>

      <LogicSection
        title="执行分支"
        description="分支目标通过画布右侧的成功、失败输出端口连接；保存工作流时会自动写入 successNode 和 failedNode。"
      >
        <div className="grid grid-cols-2 gap-3">
          <div className="rounded-md border border-[#bbf7d0] bg-[#f0fdf4] p-3">
            <div className="text-xs font-semibold text-[#15803d]">成功分支</div>
            <p className="mt-1 text-[11px] leading-4 text-[#166534]">条件判断结果为 SUCCESS 时执行</p>
          </div>
          <div className="rounded-md border border-[#fecaca] bg-[#fff7f7] p-3">
            <div className="text-xs font-semibold text-[#b42318]">失败分支</div>
            <p className="mt-1 text-[11px] leading-4 text-[#b42318]">条件判断结果不满足时执行</p>
          </div>
        </div>
        <p className="mt-2 text-[11px] leading-4 text-[#98A2B3]">
          两个分支都至少需要连接一个下游节点。条件结果由运行引擎计算，不需要手动设置。
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
  workflowTasks,
}: {
  workflowCode: number;
  task: WorkflowTaskRecord;
  taskName: string;
  readOnly: boolean;
  onApplyTask: (task: WorkflowTaskRecord) => boolean;
  onDirtyChange: (dirty: boolean) => void;
  onBackToWorkflow: () => void;
  workflowTasks: WorkflowTaskRecord[];
}) {
  return (
    <LogicTaskEditor
      workflowCode={workflowCode}
      task={task}
      taskName={taskName}
      taskType="CONDITIONS"
      readOnly={readOnly}
      onApplyTask={onApplyTask}
      onDirtyChange={onDirtyChange}
      onBackToWorkflow={onBackToWorkflow}
      validateParams={(value) => {
        const normalized = normalizeConditionsTaskParams(value);
        const groups = normalized.dependence.dependTaskList;
        if (groups.length === 0) return "至少需要配置一个依赖条件组";
        if (groups.some((group) => group.dependItemList.length === 0)) {
          return "每个条件组至少需要配置一个依赖任务";
        }
        if (groups.some((group) => group.dependItemList.some((item) => item.depTaskCode <= 0))) {
          return "请选择每个条件项对应的上游任务";
        }
        return null;
      }}
    >
      {(formProps) => (
        <ConditionsForm
          {...formProps}
          workflowCode={workflowCode}
          projectCode={task.projectCode}
          workflowTasks={workflowTasks}
        />
      )}
    </LogicTaskEditor>
  );
}

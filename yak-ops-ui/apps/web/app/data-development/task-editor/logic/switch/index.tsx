import { useEffect, useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { ArrowDown, ArrowUp, Plus, Trash2 } from "lucide-react";
import { Button, Input } from "@yak-ops/yak-ui";

import {
  getWorkflowDefinition,
  type WorkflowTaskRecord,
} from "@/service/workflow/definition";

import { LogicTaskEditor, type LogicTaskFormProps } from "../shared/logic-task-editor";
import { LogicSection, LogicSelect } from "../shared/logic-form-controls";
import {
  getSwitchVariableOptions,
  normalizeSwitchTaskParams,
  validateSwitchTaskParams,
  type SwitchVariableOption,
} from "./adapter";
import type { SwitchCase } from "./types";

const OPERATOR_OPTIONS = [
  { value: "==", label: "等于 ==" },
  { value: "===", label: "严格等于 ===" },
  { value: "!=", label: "不等于 !=" },
  { value: "!==", label: "严格不等于 !==" },
  { value: ">", label: "大于 >" },
  { value: ">=", label: "大于等于 >=" },
  { value: "<", label: "小于 <" },
  { value: "<=", label: "小于等于 <=" },
];

function parseSimpleExpression(condition: string) {
  const match = condition.match(/^\s*(\$\{[^}]+\})\s*(===|!==|==|!=|>=|<=|>|<)\s*(.*?)\s*$/);
  if (!match) return null;
  return { variable: match[1], operator: match[2], rightValue: match[3] };
}

function SwitchExpressionBuilder({
  condition,
  variables,
  disabled,
  onChange,
}: {
  condition: string;
  variables: SwitchVariableOption[];
  disabled: boolean;
  onChange: (value: string) => void;
}) {
  const parsed = useMemo(() => parseSimpleExpression(condition), [condition]);
  const [variable, setVariable] = useState(parsed?.variable ?? variables[0]?.value ?? "");
  const [operator, setOperator] = useState(parsed?.operator ?? ">=");
  const [rightValue, setRightValue] = useState(parsed?.rightValue ?? "");
  const [advanced, setAdvanced] = useState(Boolean(condition.trim() && !parsed));

  useEffect(() => {
    const next = parseSimpleExpression(condition);
    if (next) {
      setVariable(next.variable);
      setOperator(next.operator);
      setRightValue(next.rightValue);
      setAdvanced(false);
    } else if (!condition.trim()) {
      setVariable(variables[0]?.value ?? "");
      setOperator(">=");
      setRightValue("");
      setAdvanced(false);
    } else {
      setAdvanced(true);
    }
  }, [condition, variables]);

  const emit = (nextVariable: string, nextOperator: string, nextRightValue: string) => {
    setVariable(nextVariable);
    setOperator(nextOperator);
    setRightValue(nextRightValue);
    onChange(nextVariable + " " + nextOperator + " " + nextRightValue);
  };

  return (
    <div className="space-y-2">
      {advanced ? (
        <Input
          size="small"
          variant="outlined"
          value={condition}
          placeholder="例如：${amount} >= 100"
          disabled={disabled}
          onChange={(event) => onChange(event.target.value)}
          aria-label="高级表达式"
        />
      ) : (
        <div className="grid grid-cols-[minmax(0,1fr)_124px_minmax(0,1fr)] gap-2">
          {variables.some((option) => option.value === variable) ? (
            <LogicSelect
              id="switch-expression-variable"
              value={variable}
              options={variables}
              placeholder="选择变量"
              disabled={disabled}
              onChange={(value) => emit(value, operator, rightValue)}
            />
          ) : (
            <Input
              size="small"
              variant="outlined"
              value={variable}
              placeholder="${variable}"
              disabled={disabled}
              onChange={(event) => emit(event.target.value, operator, rightValue)}
              aria-label="表达式变量"
            />
          )}
          <LogicSelect
            id="switch-expression-operator"
            value={operator}
            options={OPERATOR_OPTIONS}
            placeholder="运算符"
            disabled={disabled}
            onChange={(value) => emit(variable, value, rightValue)}
          />
          <Input
            size="small"
            variant="outlined"
            value={rightValue}
            placeholder="常量 / 变量"
            disabled={disabled}
            onChange={(event) => emit(variable, operator, event.target.value)}
            aria-label="表达式右值"
          />
        </div>
      )}
      <div className="flex items-center justify-between gap-2">
        <span className="text-[11px] text-[#98A2B3]">
          {variables.length > 0 ? "变量来源：工作流全局参数、当前节点输入参数" : "未找到可选变量，也可以直接输入变量表达式"}
        </span>
        <Button
          size="sm"
          variant="ghost"
          isDisabled={disabled}
          onClick={() => setAdvanced((value) => !value)}
        >
          {advanced ? "简易构建" : "高级表达式"}
        </Button>
      </div>
    </div>
  );
}

function SwitchForm({
  params,
  onChange,
  disabled,
  variables,
}: LogicTaskFormProps & { variables: SwitchVariableOption[] }) {
  const normalized = useMemo(() => normalizeSwitchTaskParams(params), [params]);
  const result = normalized.switchResult;
  const cases = result.dependTaskList;

  const updateCases = (nextCases: SwitchCase[]) => {
    onChange({
      ...params,
      switchResult: {
        ...result,
        dependTaskList: nextCases,
      },
    });
  };

  const updateCase = (index: number, patch: Partial<SwitchCase>) => {
    updateCases(cases.map((item, itemIndex) =>
      itemIndex === index ? { ...item, ...patch } : item,
    ));
  };

  const moveCase = (index: number, direction: -1 | 1) => {
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= cases.length) return;
    const nextCases = [...cases];
    [nextCases[index], nextCases[targetIndex]] = [nextCases[targetIndex], nextCases[index]];
    updateCases(nextCases);
  };

  return (
    <div className="space-y-3">
      <LogicSection
        title="条件分支"
        description="按 Case 从上到下依次判断，命中第一个表达式后执行对应分支。表达式使用 DolphinScheduler 兼容的变量语法。"
        action={
          <Button
            size="sm"
            variant="secondary"
            isDisabled={disabled}
            onClick={() => updateCases([...cases, { condition: "", nextNode: null }])}
          >
            <Plus size={13} />
            添加 Case
          </Button>
        }
      >
        <div className="space-y-2">
          {cases.length === 0 ? (
            <div className="rounded-md border border-dashed border-[#d0d5dd] bg-[#f9fafb] px-3 py-6 text-center text-xs text-[#667085]">
              当前没有 Case，将直接执行 Default 分支；也可以添加 Case
            </div>
          ) : null}
          {cases.map((item, index) => (
            <section key={index} className="rounded-md border border-[#e4e7ec] bg-[#fcfcfd] p-3">
              <div className="mb-2 flex items-center justify-between gap-2">
                <div className="flex items-center gap-2">
                  <span className="flex size-5 items-center justify-center rounded bg-[#eef2ff] text-[10px] font-semibold text-[#4338ca]">
                    {index + 1}
                  </span>
                  <span className="text-xs font-semibold text-[#344054]">Case {index + 1}</span>
                </div>
                <div className="flex items-center gap-1">
                  <Button
                    size="sm"
                    variant="ghost"
                    isDisabled={disabled || index === 0}
                    aria-label={"Case " + (index + 1) + " 上移"}
                    onClick={() => moveCase(index, -1)}
                  >
                    <ArrowUp size={13} />
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    isDisabled={disabled || index === cases.length - 1}
                    aria-label={"Case " + (index + 1) + " 下移"}
                    onClick={() => moveCase(index, 1)}
                  >
                    <ArrowDown size={13} />
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    isDisabled={disabled}
                    aria-label={"删除 Case " + (index + 1)}
                    onClick={() => updateCases(cases.filter((_candidate, itemIndex) => itemIndex !== index))}
                  >
                    <Trash2 size={13} />
                  </Button>
                </div>
              </div>
              <SwitchExpressionBuilder
                condition={item.condition}
                variables={variables}
                disabled={disabled}
                onChange={(condition) => updateCase(index, { condition })}
              />
              <div className="mt-2 flex items-center justify-between gap-2 text-[11px] text-[#98A2B3]">
                <span>对应画布输出端口：Case {index + 1}</span>
                <span>支持比较运算与 JavaScript 布尔表达式</span>
              </div>
            </section>
          ))}
        </div>
        <p className="mt-3 text-[11px] leading-4 text-[#98A2B3]">
          常用运算符：==、!=、&gt;、&gt;=、&lt;、&lt;=、&&、||。字符串请使用引号。表达式将在任务运行时由后端解析执行。
        </p>
      </LogicSection>

      <LogicSection
        title="默认分支"
        description="当没有 Case 命中（或未配置 Case）时，执行 Default 分支。"
      >
        <div className="flex items-center justify-between rounded-md border border-[#e4e7ec] bg-[#f9fafb] px-3 py-3">
          <div>
            <div className="text-xs font-semibold text-[#344054]">Default</div>
            <div className="mt-1 text-[11px] text-[#667085]">对应画布上的 Default 输出端口</div>
          </div>
          <span className="rounded-full bg-[#eef2f6] px-2 py-1 text-[10px] font-medium text-[#475467]">
            兜底路径
          </span>
        </div>
        <p className="mt-2 text-[11px] leading-4 text-[#98A2B3]">
          每个 Case 和 Default 都必须且只能连接一个下游节点。修改 Case 顺序时，系统会尽量保持连线跟随原表达式。
        </p>
      </LogicSection>
    </div>
  );
}

export function SwitchEditor({
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
  const workflowQuery = useQuery({
    queryKey: ["workflow-switch-variables", workflowCode],
    queryFn: () => getWorkflowDefinition(workflowCode),
    staleTime: 60_000,
  });
  const variables = useMemo(
    () => getSwitchVariableOptions(
      workflowQuery.data?.workflowDefinition.globalParams,
      task.taskParams,
    ),
    [task.taskParams, workflowQuery.data?.workflowDefinition.globalParams],
  );

  return (
    <LogicTaskEditor
      workflowCode={workflowCode}
      task={task}
      taskName={taskName}
      taskType="SWITCH"
      readOnly={readOnly}
      onApplyTask={onApplyTask}
      onDirtyChange={onDirtyChange}
      onBackToWorkflow={onBackToWorkflow}
      validateParams={validateSwitchTaskParams}
    >
      {(formProps) => <SwitchForm {...formProps} variables={variables} />}
    </LogicTaskEditor>
  );
}

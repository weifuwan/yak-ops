import { useEffect, useMemo, useState } from "react";
import {
  Button,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";

import type { SwitchVariableOption } from "../adapter";

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
  const match = condition.match(/^\\s*(\\$\\{[^}]+\\})\\s*(===|!==|==|!=|>=|<=|>|<)\\s*(.*?)\\s*$/);
  if (!match) return null;
  return { variable: match[1], operator: match[2], rightValue: match[3] };
}

function getVariableName(value: string) {
  return value.startsWith("${") && value.endsWith("}")
    ? value.slice(2, -1)
    : value;
}

export function SwitchExpressionBuilder({
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

  const selectedVariable = variables.find((option) => option.value === variable);
  const variableItems = useMemo(
    () => Object.fromEntries(variables.map((option) => [option.value, getVariableName(option.value)])),
    [variables],
  );
  const operatorItems = useMemo(
    () => Object.fromEntries(OPERATOR_OPTIONS.map((option) => [option.value, option.label])),
    [],
  );
  const rightValuePlaceholder = /INT|LONG|DECIMAL|FLOAT|DOUBLE|NUMBER/.test(selectedVariable?.dataType ?? "")
    ? "例如 1000"
    : /DATE|TIME/.test(selectedVariable?.dataType ?? "")
      ? '例如 "2026-11-17"'
      : '例如 "VIP"（字符串请加引号）';

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
    <div className="min-w-0">
      {advanced ? (
        <div className="rounded-lg bg-[#F2F4F7] p-2">
          <Input
            size="small"
            variant="outlined"
            className="!border-0 !bg-transparent !shadow-none focus-visible:!ring-0"
            value={condition}
            placeholder="例如：${amount} >= 100"
            disabled={disabled}
            onChange={(event) => onChange(event.target.value)}
            aria-label="高级表达式"
          />
        </div>
      ) : (
        <div className="overflow-hidden rounded-lg bg-[#F2F4F7]">
          <div className="flex min-h-9 items-center px-2">
            <div className="min-w-0 flex-1">
              {variables.some((option) => option.value === variable) ? (
                <Select<string>
                  size="small"
                  disabled={disabled}
                  items={variableItems}
                  value={variable || null}
                  onValueChange={(value) => emit(value ?? "", operator, rightValue)}
                >
                  <SelectTrigger
                    id="switch-expression-variable"
                    variant="outlined"
                    className="!h-7 !w-full !border-0 !bg-transparent !px-1 !shadow-none focus-visible:!ring-0"
                  >
                    <SelectValue placeholder="选择变量" />
                  </SelectTrigger>
                  <SelectContent>
                    {variables.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        <div className="flex min-w-0 flex-1 items-center justify-between gap-4">
                          <SelectItemText>{getVariableName(option.value)}</SelectItemText>
                          <span className="shrink-0 text-[10px] text-[#98A2B3]">
                            {[option.source, option.dataType].filter(Boolean).join(" · ")}
                          </span>
                        </div>
                        <SelectItemIndicator />
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              ) : (
                <Input
                  size="small"
                  variant="outlined"
                  className="!h-7 !border-0 !bg-transparent !px-1 !shadow-none focus-visible:!ring-0"
                  value={variable}
                  placeholder="${variable}"
                  disabled={disabled}
                  onChange={(event) => emit(event.target.value, operator, rightValue)}
                  aria-label="表达式变量"
                />
              )}
            </div>
            <span className="mx-2 h-4 w-px shrink-0 bg-[#D0D5DD]" />
            <Select<string>
              size="small"
              disabled={disabled}
              items={operatorItems}
              value={operator || null}
              onValueChange={(value) => emit(variable, value ?? "", rightValue)}
            >
              <SelectTrigger
                id="switch-expression-operator"
                variant="outlined"
                className="!h-7 !w-[118px] !shrink-0 !border-0 !bg-transparent !px-1 !shadow-none focus-visible:!ring-0"
              >
                <SelectValue placeholder="运算符" />
              </SelectTrigger>
              <SelectContent>
                {OPERATOR_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    <SelectItemText>{option.label}</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="border-t border-[#E4E7EC] px-2 py-1">
            <Input
              size="small"
              variant="outlined"
              className="!h-7 !border-0 !bg-transparent !px-1 !shadow-none focus-visible:!ring-0"
              value={rightValue}
              placeholder={rightValuePlaceholder}
              disabled={disabled}
              onChange={(event) => emit(variable, operator, event.target.value)}
              aria-label="表达式比较值"
            />
          </div>
        </div>
      )}

      <div className="mt-1 flex min-h-6 items-center justify-between gap-2">
        <span className="min-w-0 truncate text-[11px] leading-4 text-[#98A2B3]">
          {selectedVariable
            ? [selectedVariable.source, selectedVariable.dataType].filter(Boolean).join(" · ")
            : variables.length > 0
              ? "可选择系统、项目、全局及上游 OUT 参数"
              : "暂无可选变量，可直接输入运行时变量表达式"}
        </span>
        <Button
          size="sm"
          variant="ghost"
          className="!h-6 shrink-0 !px-1.5 text-[11px] text-[#667085] hover:!text-[#344054]"
          isDisabled={disabled}
          onClick={() => setAdvanced((value) => !value)}
        >
          {advanced ? "简易构建" : "高级表达式"}
        </Button>
      </div>
    </div>
  );
}

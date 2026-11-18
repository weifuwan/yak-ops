import { useEffect, useState } from "react";
import { ArrowDown, ArrowUp, Plus, Trash2 } from "lucide-react";
import { Button } from "@yak-ops/yak-ui";

import type { SwitchVariableOption } from "../adapter";
import { SwitchExpressionBuilder } from "./switch-expression-builder";

export function SwitchCaseRow({
  index,
  total,
  condition,
  variables,
  disabled,
  warning,
  onConditionChange,
  onMove,
  onRemove,
}: {
  index: number;
  total: number;
  condition: string;
  variables: SwitchVariableOption[];
  disabled: boolean;
  warning?: string;
  onConditionChange: (condition: string) => void;
  onMove: (direction: -1 | 1) => void;
  onRemove: () => void;
}) {
  const [editing, setEditing] = useState(Boolean(condition.trim()));

  useEffect(() => {
    if (condition.trim()) setEditing(true);
  }, [condition]);

  return (
    <div className="group flex gap-3 px-3 py-3 transition-colors duration-150 hover:bg-[#F9FAFB]">
      <div className="w-12 shrink-0 pt-1">
        <div className="text-[13px] font-semibold leading-4 text-[#344054]">
          {index === 0 ? "IF" : "ELIF"}
        </div>
        <div className="mt-0.5 text-[10px] font-medium uppercase tracking-wide text-[#98A2B3]">
          CASE {index + 1}
        </div>
      </div>

      <div className="min-w-0 flex-1">
        <div className="flex min-w-0 items-start gap-2">
          <div className="min-w-0 flex-1">
            {editing ? (
              <SwitchExpressionBuilder
                condition={condition}
                variables={variables}
                disabled={disabled}
                onChange={onConditionChange}
              />
            ) : (
              <Button
                size="sm"
                variant="outline"
                className="!h-7 !gap-1.5 !border-[#E4E7EC] !bg-white !px-2.5 !text-xs !font-medium !text-[#475467] shadow-sm hover:!bg-[#F9FAFB] hover:!text-[#344054]"
                isDisabled={disabled}
                onClick={() => setEditing(true)}
              >
                <Plus size={14} />
                添加条件
              </Button>
            )}
          </div>

          <div className="flex shrink-0 items-center gap-0.5 pt-0.5">
            <Button
              size="sm"
              variant="ghost"
              className="!size-7 !p-0 text-[#98A2B3] opacity-0 transition-opacity group-hover:opacity-100 group-focus-within:opacity-100"
              isDisabled={disabled || index === 0}
              aria-label={"Case " + (index + 1) + " 上移"}
              title="上移"
              onClick={() => onMove(-1)}
            >
              <ArrowUp size={13} />
            </Button>
            <Button
              size="sm"
              variant="ghost"
              className="!size-7 !p-0 text-[#98A2B3] opacity-0 transition-opacity group-hover:opacity-100 group-focus-within:opacity-100"
              isDisabled={disabled || index === total - 1}
              aria-label={"Case " + (index + 1) + " 下移"}
              title="下移"
              onClick={() => onMove(1)}
            >
              <ArrowDown size={13} />
            </Button>
            <Button
              size="sm"
              variant="ghost"
              className="!h-7 !gap-1 !px-2 text-[11px] font-medium text-[#667085] hover:!bg-[#FEF3F2] hover:!text-[#D92D20]"
              isDisabled={disabled}
              aria-label={"移除 Case " + (index + 1)}
              onClick={onRemove}
            >
              <Trash2 size={13} />
              移除
            </Button>
          </div>
        </div>

        {warning ? (
          <div className="mt-1.5 rounded-md bg-[#FFFAEB] px-2.5 py-1.5 text-[11px] leading-4 text-[#B54708]">
            {warning}
          </div>
        ) : null}
      </div>
    </div>
  );
}

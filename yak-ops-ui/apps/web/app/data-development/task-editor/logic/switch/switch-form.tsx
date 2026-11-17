import { Plus } from "lucide-react";
import { Button } from "@yak-ops/yak-ui";

import type { LogicTaskFormProps } from "../shared/logic-task-editor";
import {
  getSwitchCaseWarnings,
  normalizeSwitchTaskParams,
  type SwitchVariableOption,
} from "./adapter";
import type { SwitchCase } from "./types";
import { SwitchCaseRow } from "./components/switch-case-row";

export function SwitchForm({
  params,
  onChange,
  disabled,
  variables,
}: LogicTaskFormProps & { variables: SwitchVariableOption[] }) {
  const normalized = normalizeSwitchTaskParams(params);
  const result = normalized.switchResult;
  const cases = result.dependTaskList;
  const warningsByCase = new Map(
    getSwitchCaseWarnings(normalized).map((warning) => [
      warning.caseIndex,
      warning.message,
    ]),
  );

  const updateCases = (nextCases: SwitchCase[]) => {
    onChange({
      ...params,
      switchResult: {
        ...result,
        dependTaskList: nextCases,
      },
    });
  };

  const updateCase = (index: number, condition: string) => {
    updateCases(cases.map((item, itemIndex) =>
      itemIndex === index ? { ...item, condition } : item,
    ));
  };

  const moveCase = (index: number, direction: -1 | 1) => {
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= cases.length) return;
    const nextCases = [...cases];
    [nextCases[index], nextCases[targetIndex]] = [
      nextCases[targetIndex],
      nextCases[index],
    ];
    updateCases(nextCases);
  };

  return (
    <div className="p-1">
      <div className="overflow-hidden rounded-xl border border-[#EAECF0] bg-[#FBFCFE]">
        <div className="divide-y divide-[#EAECF0]">
          {cases.map((item, index) => (
            <SwitchCaseRow
              key={item.nextNode ? "case-node-" + item.nextNode : "case-index-" + index}
              index={index}
              total={cases.length}
              condition={item.condition}
              variables={variables}
              disabled={disabled}
              warning={warningsByCase.get(index)}
              onConditionChange={(condition) => updateCase(index, condition)}
              onMove={(direction) => moveCase(index, direction)}
              onRemove={() => updateCases(
                cases.filter((_candidate, itemIndex) => itemIndex !== index),
              )}
            />
          ))}

          {cases.length === 0 ? (
            <div className="px-3 py-3 text-xs leading-5 text-[#98A2B3]">
              当前还没有条件分支。添加 IF 后，可以继续添加多个 ELIF 分支。
            </div>
          ) : null}
        </div>
      </div>

      <div className="pt-2">
        <Button
          size="sm"
          variant="secondary"
          className="!h-9 !w-full !justify-center !border-0 !bg-[#F2F4F7] !text-[#475467] !outline-none !outline-offset-0 focus-visible:!outline-none [&[data-focused]]:!outline-none hover:!bg-[#EAECF0]"
          isDisabled={disabled}
          onClick={(event) => {
            // The full-width ELIF button moves down when a Case is inserted.
            // Blur it before the list reflows so its old focus outline cannot flash.
            event.currentTarget.blur();
            updateCases([
              ...cases,
              { condition: "", nextNode: null },
            ]);
          }}
        >
          <Plus size={14} />
          ELIF
        </Button>
      </div>

      <div className="my-3 h-px bg-[#E4E7EC]" />

      <section className="py-1">
        <div className="text-[13px] font-semibold leading-4 text-[#344054]">
          ELSE
        </div>
        <div className="mt-2 flex items-center gap-2">
          <span className="inline-flex h-6 items-center rounded-md bg-[#F2F4F7] px-2 text-[11px] font-medium text-[#475467]">
            Default
          </span>
          <span className="text-[11px] text-[#98A2B3]">
            对应画布上的 Default 输出端口
          </span>
        </div>
      </section>
    </div>
  );
}

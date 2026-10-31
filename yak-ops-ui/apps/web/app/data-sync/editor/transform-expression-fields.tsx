import { Field, FieldLabel, Input, Textarea } from "@yak-ops/yak-ui";
import type { ReactNode } from "react";

import type { DataSyncTransform } from "@/service/data-sync";

interface Props {
  rule: DataSyncTransform;
  index?: number;
  showSelector?: boolean;
  showProjection?: boolean;
  disabled?: boolean;
  keyAction?: ReactNode;
  onChange: (patch: Partial<DataSyncTransform>) => void;
}

/** Edits the existing rule text without evaluating or rewriting its expressions. */
export function TransformExpressionFields({
  rule,
  index = 0,
  showSelector = false,
  showProjection = true,
  disabled,
  keyAction,
  onChange,
}: Props) {
  const suffix = index === 0 ? "" : ` ${index + 1}`;
  return (
    <div className="space-y-3">
      {showSelector ? (
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
          <FieldLabel required>来源表规则</FieldLabel>
          <Input
            size="small"
            variant="outlined"
            aria-label={`来源表规则${suffix}`}
            value={rule["source-table"]}
            disabled={disabled}
            onChange={(event) => onChange({ "source-table": event.target.value })}
          />
        </Field>
      ) : null}
      {showProjection ? (
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
          <FieldLabel>投影表达式</FieldLabel>
          <Textarea
            size="small"
            variant="outlined"
            className="font-mono"
            aria-label={`投影表达式${suffix}`}
            placeholder="*"
            value={rule.projection ?? ""}
            disabled={disabled}
            spellCheck={false}
            onValueChange={(value) => onChange({ projection: String(value) || undefined })}
          />
        </Field>
      ) : null}
      <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
        <FieldLabel>行过滤</FieldLabel>
        <Textarea
          size="small"
          variant="outlined"
          aria-label={`行过滤${suffix}`}
          className="min-h-14 font-mono"
          placeholder="例如 amount > 0"
          value={rule.filter ?? ""}
          disabled={disabled}
          spellCheck={false}
          onValueChange={(value) => onChange({ filter: String(value) || undefined })}
        />
      </Field>
      <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
        <FieldLabel>输出主键</FieldLabel>
        <div className="flex flex-wrap gap-2">
          <Input
            size="small"
            variant="outlined"
            className="min-w-40 flex-1"
            aria-label={`输出主键${suffix}`}
            placeholder="默认保留完整来源主键"
            value={rule["primary-keys"] ?? ""}
            disabled={disabled}
            onChange={(event) => onChange({ "primary-keys": event.target.value || undefined })}
          />
          {keyAction}
        </div>
      </Field>
      {showSelector && rule.description !== undefined ? (
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
          <FieldLabel>规则说明</FieldLabel>
          <Input
            size="small"
            variant="outlined"
            value={rule.description}
            disabled={disabled}
            onChange={(event) => onChange({ description: event.target.value })}
          />
        </Field>
      ) : null}
      {showSelector &&
      Object.keys(rule).some((key) =>
        [
          "partition-keys",
          "table-options",
          "table-options.delimiter",
          "converter-after-transform",
        ].includes(key),
      ) ? (
        <pre className="overflow-auto rounded-md bg-[var(--yak-components-panel-bg)] p-3 text-xs">
          {JSON.stringify(
            Object.fromEntries(
              Object.entries(rule).filter(([key]) =>
                [
                  "partition-keys",
                  "table-options",
                  "table-options.delimiter",
                  "converter-after-transform",
                ].includes(key),
              ),
            ),
            null,
            2,
          )}
        </pre>
      ) : null}
    </div>
  );
}

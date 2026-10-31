import { Alert, Field, FieldLabel, Switch } from "@yak-ops/yak-ui";
import type { DataSyncSolution } from "@/service/data-sync";
import { DataSyncSearchableSelect } from "../components/searchable-select";
import { changeSchemaPreparation, type EditorConfiguration } from "./configuration";

export function DdlConfiguration({
  value,
  solution,
  onChange,
}: {
  value: EditorConfiguration;
  solution?: DataSyncSolution;
  onChange: (value: EditorConfiguration) => void;
}) {
  const initial = value.draft.steps.includes("FULL");
  const continuous =
    value.draft.productType === "DATABASE_REPLICATION" && value.draft.steps.includes("INCREMENTAL");
  const canPrepare = Boolean(initial && solution?.steps.includes("SCHEMA"));
  const sink = value.form.sink;
  const patch = (next: Partial<typeof sink>) =>
    onChange({ ...value, form: { ...value.form, sink: { ...sink, ...next } } });
  return (
    <div className="space-y-5">
      <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
        <FieldLabel>目标表结构</FieldLabel>
        <DataSyncSearchableSelect
          ariaLabel="目标表结构"
          placeholder="请选择目标表结构"
          value={value.draft.steps.includes("SCHEMA") ? "CREATE_OR_ALTER" : "VERIFY"}
          disabled={!canPrepare}
          options={[
            { value: "VERIFY", label: "校验已有表" },
            ...(canPrepare ? [{ value: "CREATE_OR_ALTER", label: "创建或调整目标表" }] : []),
          ]}
          onValueChange={(next) =>
            onChange(changeSchemaPreparation(value, next === "CREATE_OR_ALTER", solution))
          }
        />
      </Field>
      {continuous ? (
        <>
          <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
            <FieldLabel>同步结构变更</FieldLabel>
            <Switch
              aria-label="同步结构变更"
              size="small"
              checked={sink.schemaChangesEnabled}
              disabled={value.draft.targetType === "ORACLE"}
              onCheckedChange={(enabled) =>
                patch({
                  schemaChangesEnabled: enabled,
                  allowDropColumns: enabled && sink.allowDropColumns,
                })
              }
            />
          </Field>
          <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
            <FieldLabel>允许删除字段</FieldLabel>
            <Switch
              aria-label="允许删除目标字段"
              size="small"
              checked={sink.allowDropColumns}
              disabled={!sink.schemaChangesEnabled || value.draft.targetType === "ORACLE"}
              onCheckedChange={(allowDropColumns) => patch({ allowDropColumns })}
            />
          </Field>
          {sink.allowDropColumns ? (
            <Alert>来源字段删除后，目标端对应字段及数据也会删除。</Alert>
          ) : null}
        </>
      ) : null}
    </div>
  );
}

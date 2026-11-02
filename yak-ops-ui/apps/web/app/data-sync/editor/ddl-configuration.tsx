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
  const oracle = value.draft.targetType === "ORACLE";
  const patch = (next: Partial<typeof sink>) =>
    onChange({ ...value, form: { ...value.form, sink: { ...sink, ...next } } });
  return (
    <div className="space-y-5">
      <section aria-label="目标表初始化" className="space-y-3">
        <div className="space-y-1">
          <h3 className="text-sm font-medium">目标表初始化</h3>
          <p className="text-xs text-[var(--yak-components-muted-text)]">
            仅影响首次全量阶段，不决定运行期间的表结构变更策略。
          </p>
        </div>
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel>处理方式</FieldLabel>
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
      </section>
      {continuous ? (
        <section
          aria-label="运行期间结构变更"
          className="space-y-3 border-t border-[var(--yak-components-panel-border)] pt-5"
        >
          <div className="space-y-1">
            <h3 className="text-sm font-medium">运行期间结构变更</h3>
            <p className="text-xs text-[var(--yak-components-muted-text)]">
              只处理当前连接器支持并通过安全校验的结构变更。
            </p>
          </div>
          <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
            <FieldLabel>同步结构变更</FieldLabel>
            <Switch
              aria-label="同步结构变更"
              size="small"
              checked={sink.schemaChangesEnabled}
              disabled={oracle}
              onCheckedChange={(enabled) =>
                patch({
                  schemaChangesEnabled: enabled,
                  allowDropColumns: enabled && sink.allowDropColumns,
                })
              }
            />
          </Field>
          {sink.schemaChangesEnabled ? (
            <>
              <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
                <FieldLabel>新增列与类型变更</FieldLabel>
                <span className="text-xs text-[var(--yak-components-muted-text)]">
                  仅同步安全新增列与安全类型扩宽
                </span>
              </Field>
              <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
                <FieldLabel>允许删除字段</FieldLabel>
                <Switch
                  aria-label="允许删除目标字段"
                  size="small"
                  checked={sink.allowDropColumns}
                  disabled={oracle}
                  onCheckedChange={(allowDropColumns) => patch({ allowDropColumns })}
                />
              </Field>
              {sink.allowDropColumns ? (
                <Alert>删除目标字段可能造成数据丢失，仅对支持的非主键字段生效。</Alert>
              ) : null}
            </>
          ) : (
            <p className="text-xs text-[var(--yak-components-muted-text)]">
              关闭后不自动同步结构变更；遇到不兼容的变更仍可能导致任务失败。
            </p>
          )}
          <p className="text-xs text-[var(--yak-components-muted-text)]">
            删除表、重命名表或列、清空表目前不提供独立处理策略。
          </p>
        </section>
      ) : null}
    </div>
  );
}

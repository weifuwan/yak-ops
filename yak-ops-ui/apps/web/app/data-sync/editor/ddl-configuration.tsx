import { Alert, Button, Field, FieldLabel, Switch } from "@yak-ops/yak-ui";
import type { DataSyncDdlAction, DataSyncSolution } from "@/service/data-sync";
import {
  DDL_ACTION_LABELS,
  DDL_FIELDS,
  recommendLegacyDdlStrategies,
} from "@/service/data-sync/ddl-policy";
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
  const allowedPolicies = solution?.capabilities.ddlPolicies;
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
      {continuous && sink.ddlStrategies ? (
        <div className="space-y-3">
          <p className="text-xs text-[var(--yak-components-muted-text)]">
            DDL 处理策略针对运行中的结构变更。正常处理只执行当前连接器支持的操作；
            忽略仍会更新来源字段结构，不兼容的数据将使任务失败。
          </p>
          {DDL_FIELDS.map(({ key, label, help }) => {
            const available = allowedPolicies?.[key] || [];
            const current = sink.ddlStrategies![key];
            const supported = available.includes(current);
            return (
              <div key={key} className="space-y-1">
                <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
                  <FieldLabel>{label}</FieldLabel>
                  <DataSyncSearchableSelect
                    ariaLabel={label}
                    placeholder="请选择处理策略"
                    value={current}
                    disabled={!available.length}
                    options={[
                      ...available.map((action) => ({
                        value: action,
                        label: DDL_ACTION_LABELS[action],
                      })),
                      ...(!supported
                        ? [
                            {
                              value: current,
                              label: `${DDL_ACTION_LABELS[current]}（当前链路不可用）`,
                            },
                          ]
                        : []),
                    ]}
                    onValueChange={(next) =>
                      patch({
                        ddlStrategies: {
                          ...sink.ddlStrategies!,
                          [key]: next as DataSyncDdlAction,
                        },
                      })
                    }
                  />
                </Field>
                <p className="pl-[116px] text-xs text-[var(--yak-components-muted-text)]">{help}</p>
                {!supported ? (
                  <p className="pl-[116px] text-xs text-[var(--yak-components-danger-text)]">
                    当前连接器不支持此策略，请选择可用项。
                  </p>
                ) : null}
              </div>
            );
          })}
          {sink.ddlStrategies.dropColumn === "NORMAL" ? (
            <Alert>删除列会永久删除目标端对应字段及其数据，请确认已备份。</Alert>
          ) : null}
        </div>
      ) : continuous ? (
        <div className="space-y-4">
          <Alert>
            这是旧版“整体结构演进”配置。它与逐事件的“忽略/出错”不完全等价； 升级将影响新发布任务的
            DDL 捕获及异常行为，旧运行实例不受影响。
          </Alert>
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
          {allowedPolicies ? (
            <Button
              variant="secondary"
              size="small"
              onClick={() =>
                patch({
                  schemaChangesEnabled: true,
                  ddlStrategies: recommendLegacyDdlStrategies(
                    sink.schemaChangesEnabled,
                    sink.allowDropColumns,
                    Boolean(value.form.selection),
                  ),
                })
              }
            >
              我已了解语义差异，升级为逐项 DDL 策略
            </Button>
          ) : null}
          {sink.allowDropColumns ? (
            <Alert>来源字段删除后，目标端对应字段及数据也会删除。</Alert>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}

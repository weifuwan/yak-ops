import { useQuery } from "@tanstack/react-query";
import { Alert, Button, CollapseSection, Field, FieldError, Modal } from "@yak-ops/yak-ui";
import { useState } from "react";

import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import { listDataSourceColumns, type DataSourceCatalogTablePath } from "@/service/datasource";
import type { DataSyncTransform } from "@/service/data-sync";
import {
  canEditFieldMapping,
  fieldProjection,
  mappedSourcePrimaryKeys,
  readDirectProjection,
  sameNameFieldMappings,
  transformEditingError,
  transformTableSelector,
  updateTransformRule,
  type DirectFieldMapping,
} from "@/service/data-sync/transforms";
import { FieldMappingTable } from "./field-mapping-table";
import { TransformExpressionFields } from "./transform-expression-fields";

interface FieldEndpoint extends DataSourceCatalogTablePath {
  dataSourceId: string;
}

interface Props {
  source: FieldEndpoint;
  target: FieldEndpoint;
  targetTableExists: boolean;
  value: DataSyncTransform[];
  onChange: (rules: DataSyncTransform[]) => void;
  disabled?: boolean;
}

function useFieldColumns(endpoint: FieldEndpoint, enabled: boolean) {
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const { dataSourceId, database, schema, table } = endpoint;
  return useQuery({
    queryKey: [
      "data-sync-field-columns",
      currentUser?.id,
      currentWorkspace?.id,
      dataSourceId,
      database ?? "",
      schema ?? "",
      table,
    ],
    queryFn: ({ signal }) =>
      listDataSourceColumns(
        dataSourceId,
        { database: database || undefined, schema: schema || undefined, table },
        signal,
      ),
    enabled: Boolean(enabled && currentUser?.id && currentWorkspace?.id && dataSourceId && table),
    retry: false,
  });
}

/** Owns physical field reads and edits the existing transform rule array. */
export function FieldMappingSection({
  source,
  target,
  targetTableExists,
  value,
  onChange,
  disabled = false,
}: Props) {
  const sourceQuery = useFieldColumns(source, true);
  const targetQuery = useFieldColumns(target, targetTableExists);
  const sourceColumns = sourceQuery.data ?? [];
  const targetColumns = targetTableExists ? targetQuery.data : undefined;
  const selector = transformTableSelector(source);
  const rule = value[0] ?? { "source-table": selector };
  const single = canEditFieldMapping(value, selector);
  const direct = single ? readDirectProjection(rule.projection) : undefined;
  const fields = direct
    ? direct.all
      ? sameNameFieldMappings(sourceColumns)
      : direct.fields
    : undefined;
  const [expressionView, setExpressionView] = useState(false);
  const [replacement, setReplacement] = useState<string>();
  const signature = JSON.stringify([source, value]);
  const showFields = single && Boolean(direct) && !expressionView;
  const ready = Boolean(source.dataSourceId && source.table);
  const editingDisabled = disabled || !ready;
  const fieldsDisabled =
    editingDisabled || !sourceQuery.isSuccess || (targetTableExists && !targetQuery.isSuccess);
  const defaults = sameNameFieldMappings(sourceColumns, targetColumns);
  const keys = fields ? mappedSourcePrimaryKeys(fields, sourceColumns) : undefined;
  const error = transformEditingError(value);
  const missingTargets =
    fields && targetColumns
      ? targetColumns.filter((column) => !fields.some((field) => field.target === column.name))
      : [];
  const patch = (index: number, change: Partial<DataSyncTransform>) =>
    onChange(updateTransformRule(value, index, selector, change));
  const setFields = (next: DirectFieldMapping[]) => patch(0, { projection: fieldProjection(next) });
  const applyDefaults = () => {
    setFields(defaults);
    setExpressionView(false);
    setReplacement(undefined);
  };
  return (
    <CollapseSection id="field-mapping" title="字段加工">
      {!ready ? <div className="text-sm">请选择来源表</div> : null}
      {ready && sourceQuery.isError ? (
        <Alert>
          来源字段加载失败
          <Button size="small" onClick={() => void sourceQuery.refetch()}>
            重试
          </Button>
        </Alert>
      ) : null}
      {ready && targetTableExists && targetQuery.isError ? (
        <Alert>
          去向字段加载失败
          <Button size="small" onClick={() => void targetQuery.refetch()}>
            重试
          </Button>
        </Alert>
      ) : null}
      {single ? (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            <Button
              size="small"
              variant={showFields ? "primary" : "secondary"}
              aria-pressed={showFields}
              disabled={editingDisabled || !direct}
              onClick={() => setExpressionView(false)}
            >
              字段映射
            </Button>
            <Button
              size="small"
              variant={showFields ? "secondary" : "primary"}
              aria-pressed={!showFields}
              disabled={editingDisabled}
              onClick={() => setExpressionView(true)}
            >
              表达式
            </Button>
            <Button
              size="small"
              variant="ghost"
              disabled={fieldsDisabled || defaults.length === 0}
              onClick={() => {
                if (!direct) setReplacement(signature);
                else applyDefaults();
              }}
            >
              {direct ? "同名映射" : "改为同名映射"}
            </Button>
            {direct && !direct.all && showFields ? (
              <Button
                size="small"
                variant="ghost"
                disabled={editingDisabled}
                onClick={() => patch(0, { projection: "*" })}
              >
                全部字段
              </Button>
            ) : null}
            {showFields && direct?.all ? (
              <span className="text-xs text-[var(--yak-components-field-description)]">
                全部字段
              </span>
            ) : null}
          </div>
          {showFields ? (
            sourceQuery.isPending && ready ? (
              <div className="py-6 text-sm" role="status">
                字段加载中
              </div>
            ) : (
              <FieldMappingTable
                fields={fields ?? []}
                sourceColumns={sourceColumns}
                targetColumns={targetColumns}
                disabled={fieldsDisabled}
                onChange={setFields}
              />
            )
          ) : null}
          {missingTargets.length > 0 && showFields ? (
            <Alert>
              去向字段尚未映射：{missingTargets.map((column) => column.name).join("、")}
            </Alert>
          ) : null}
          <TransformExpressionFields
            rule={rule}
            showProjection={!showFields}
            disabled={editingDisabled}
            onChange={(change) => patch(0, change)}
            keyAction={
              keys ? (
                <Button
                  size="small"
                  disabled={fieldsDisabled}
                  onClick={() => patch(0, { "primary-keys": keys.join(", ") })}
                >
                  使用映射后的来源主键
                </Button>
              ) : undefined
            }
          />
        </div>
      ) : (
        <div className="space-y-5">
          {value.map((item, index) => (
            <div
              key={index}
              className="space-y-3 rounded-lg border border-[var(--yak-components-panel-border)] p-4"
            >
              <div className="text-sm font-medium">转换规则 {index + 1}</div>
              <TransformExpressionFields
                rule={item}
                index={index}
                showSelector
                disabled={disabled}
                onChange={(change) => patch(index, change)}
              />
            </div>
          ))}
        </div>
      )}
      {error ? (
        <Field invalid>
          <FieldError match>{error}</FieldError>
        </Field>
      ) : null}
      <Modal
        open={replacement === signature}
        onClose={() => setReplacement(undefined)}
        title="替换投影表达式"
        closeLabel="关闭"
        footer={
          <>
            <Button size="small" onClick={() => setReplacement(undefined)}>
              取消
            </Button>
            <Button
              size="small"
              variant="primary"
              disabled={fieldsDisabled || !defaults.length}
              onClick={applyDefaults}
            >
              替换为同名映射
            </Button>
          </>
        }
      >
        将替换当前字段选择、别名和投影表达式。确认后请检查行过滤与输出主键。
      </Modal>
    </CollapseSection>
  );
}

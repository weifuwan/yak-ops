import { useQuery } from "@tanstack/react-query";
import { Alert, Button, Modal, Table, type TableColumns } from "@yak-ops/yak-ui";
import { useState, type ReactNode } from "react";
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
import { ContentSection } from "./content-layout";
import { FieldMappingTable } from "./field-mapping-table";
import { TransformRulesDialog } from "./transform-rules-dialog";

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
  extra?: ReactNode;
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

export function FieldMappingSection({
  source,
  target,
  targetTableExists,
  value,
  onChange,
  disabled = false,
  extra,
}: Props) {
  const sourceQuery = useFieldColumns(source, true);
  const targetQuery = useFieldColumns(target, targetTableExists);
  const sourceColumns = sourceQuery.data ?? [];
  const targetColumns = targetTableExists ? targetQuery.data : undefined;
  const selector = transformTableSelector(source);
  const rule = value[0] ?? { "source-table": selector };
  const direct = canEditFieldMapping(value, selector)
    ? readDirectProjection(rule.projection)
    : undefined;
  const fields = direct
    ? direct.all
      ? sameNameFieldMappings(sourceColumns)
      : direct.fields
    : undefined;
  const [expressions, setExpressions] = useState(false);
  const [replacement, setReplacement] = useState<string>();
  const signature = JSON.stringify([source, value]);
  const ready = Boolean(source.dataSourceId && source.table && target.dataSourceId && target.table);
  const fieldsDisabled =
    disabled || !ready || !sourceQuery.isSuccess || (targetTableExists && !targetQuery.isSuccess);
  const defaults = sameNameFieldMappings(sourceColumns, targetColumns);
  const setFields = (next: DirectFieldMapping[]) =>
    onChange(updateTransformRule(value, 0, selector, { projection: fieldProjection(next) }));
  const applyDefaults = () => {
    setFields(defaults);
    setReplacement(undefined);
  };
  const missingTargets =
    fields && targetColumns
      ? targetColumns.filter((column) => !fields.some((field) => field.target === column.name))
      : [];
  const error = transformEditingError(value);
  const primaryKeys = fields ? mappedSourcePrimaryKeys(fields, sourceColumns) : undefined;
  const summaryColumns: TableColumns<DataSyncTransform & { index: number }> = [
    { key: "source", title: "来源表规则", render: (_, row) => row["source-table"] },
    {
      key: "projection",
      title: "字段表达式",
      render: (_, row) => (
        <span className="block max-w-lg truncate font-mono text-xs">{row.projection || "*"}</span>
      ),
    },
    { key: "filter", title: "行过滤", render: (_, row) => row.filter || "—" },
  ];
  return (
    <ContentSection id="field-mapping" title="去向字段映射">
      {extra ? <div className="flex justify-end">{extra}</div> : null}
      {!ready ? (
        <Alert>暂无字段数据，请选择数据来源与数据去向表</Alert>
      ) : (
        <>
          {sourceQuery.isError || (targetTableExists && targetQuery.isError) ? (
            <Alert>
              {sourceQuery.isError ? "来源字段加载失败" : "去向字段加载失败"}
              <Button
                size="small"
                onClick={() => {
                  void sourceQuery.refetch();
                  if (targetTableExists) void targetQuery.refetch();
                }}
              >
                重试
              </Button>
            </Alert>
          ) : null}
          <div className="flex flex-wrap items-center gap-2">
            <Button
              size="small"
              disabled={fieldsDisabled || !defaults.length || !canEditFieldMapping(value, selector)}
              onClick={() => {
                if (!direct) setReplacement(signature);
                else applyDefaults();
              }}
            >
              同名映射
            </Button>
            {direct && !direct.all ? (
              <Button
                size="small"
                disabled={disabled}
                onClick={() =>
                  onChange(updateTransformRule(value, 0, selector, { projection: "*" }))
                }
              >
                全部字段
              </Button>
            ) : null}
            <Button size="small" disabled={disabled} onClick={() => setExpressions(true)}>
              表达式
            </Button>
            <span className="ml-auto text-xs text-[var(--yak-components-muted-text)]">
              {fields ? `已映射 ${fields.length} 个字段` : `已配置 ${value.length} 条表达式规则`}
            </span>
          </div>
          {direct ? (
            <FieldMappingTable
              fields={fields ?? []}
              sourceColumns={sourceColumns}
              targetColumns={targetColumns}
              disabled={fieldsDisabled}
              onChange={setFields}
            />
          ) : (
            <Table
              columns={summaryColumns}
              dataSource={value.map((item, index) => ({ ...item, index }))}
              rowKey="index"
              size="small"
              pagination={false}
            />
          )}
          {primaryKeys ? (
            <Button
              size="small"
              disabled={fieldsDisabled}
              onClick={() =>
                onChange(
                  updateTransformRule(value, 0, selector, {
                    "primary-keys": primaryKeys.join(", "),
                  }),
                )
              }
            >
              使用映射后的来源主键
            </Button>
          ) : null}
          {missingTargets.length ? (
            <Alert>
              去向字段尚未映射：{missingTargets.map((column) => column.name).join("、")}
            </Alert>
          ) : null}
          {error ? <Alert>{error}</Alert> : null}
        </>
      )}
      {expressions ? (
        <TransformRulesDialog
          value={value.length ? value : [{ "source-table": selector }]}
          onApply={(next) => {
            onChange(next);
            setExpressions(false);
          }}
          onClose={() => setExpressions(false)}
        />
      ) : null}
      <Modal
        open={replacement === signature}
        onClose={() => setReplacement(undefined)}
        title="替换投影表达式"
        footer={
          <>
            <Button size="small" onClick={() => setReplacement(undefined)}>
              取消
            </Button>
            <Button
              size="small"
              variant="primary"
              disabled={fieldsDisabled || !defaults.length || !canEditFieldMapping(value, selector)}
              onClick={applyDefaults}
            >
              替换为同名映射
            </Button>
          </>
        }
      >
        将替换当前字段选择、别名和投影表达式。行过滤与输出主键保留。
      </Modal>
    </ContentSection>
  );
}

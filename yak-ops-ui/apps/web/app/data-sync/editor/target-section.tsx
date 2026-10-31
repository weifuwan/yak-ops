import { Alert, CollapseSection, Field, FieldLabel, Switch } from "@yak-ops/yak-ui";
import { type Dispatch, type SetStateAction } from "react";
import { TableMappingEditor } from "./table-mapping-editor";
import { DataSyncSearchableSelect } from "../components/searchable-select";
import { TableSection } from "./table-section";
import { ReplicationOptions, type ReplicationMetadata } from "./replication-options";
import type { DataSourceRecord } from "@/service/datasource";
import type { DataSyncTableMapping, DataSyncWriteMode } from "@/service/data-sync";
import type { CatalogOptions, EditorForm, EditorSink } from "./types";

interface Props {
  replication: boolean;
  prepareSchema: boolean;
  canPrepareSchema: boolean;
  onPrepareSchemaChange: (enabled: boolean) => void;
  form: EditorForm;
  targetCatalog: CatalogOptions;
  selectedTargetDataSource?: DataSourceRecord;
  mappings: DataSyncTableMapping[];
  updateMappings: (value: DataSyncTableMapping[]) => void;
  writeModes: DataSyncWriteMode[];
  replicationMetadata: ReplicationMetadata;
  continuous: boolean;
  initialLoad: boolean;
  targetType: string;
  saving: boolean;
  patchSink: <K extends keyof EditorSink>(key: K, value: EditorSink[K]) => void;
  setTaskDirty: Dispatch<SetStateAction<boolean>>;
  setParametersDirty: Dispatch<SetStateAction<boolean>>;
  setForm: Dispatch<SetStateAction<EditorForm>>;
  requestTargetChange: (apply: () => void) => void;
}
export function TargetSection({
  replication,
  prepareSchema,
  canPrepareSchema,
  onPrepareSchemaChange,
  form,
  targetCatalog,
  selectedTargetDataSource,
  mappings,
  updateMappings,
  writeModes,
  replicationMetadata,
  continuous,
  initialLoad,
  targetType,
  saving,
  patchSink,
  setTaskDirty,
  setParametersDirty,
  setForm,
  requestTargetChange,
}: Props) {
  return (
    <CollapseSection id="target" title="数据去向">
      <Field className="mb-3 grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
        <FieldLabel>目标表结构</FieldLabel>
        <DataSyncSearchableSelect
          ariaLabel="目标表结构"
          placeholder="请选择目标表结构策略"
          value={prepareSchema ? "CREATE_OR_ALTER" : "VERIFY"}
          options={[
            { value: "VERIFY", label: "校验已有表" },
            ...(canPrepareSchema ? [{ value: "CREATE_OR_ALTER", label: "创建或调整目标表" }] : []),
          ]}
          disabled={saving || !canPrepareSchema}
          onValueChange={(value) => onPrepareSchemaChange(value === "CREATE_OR_ALTER")}
        />
      </Field>
      {prepareSchema ? (
        <Alert className="mb-3">执行前会创建缺失的目标表，并按同步输出调整已有表结构。</Alert>
      ) : null}
      {prepareSchema &&
      replication &&
      !selectedTargetDataSource?.schema &&
      targetCatalog.schemas.length > 0 ? (
        <div className="mb-3">
          <DataSyncSearchableSelect
            ariaLabel="去向 Schema"
            value={form.sink.schema || null}
            options={targetCatalog.schemas.map((value) => ({ value, label: value }))}
            placeholder="去向 Schema"
            onValueChange={(value) => {
              patchSink("schema", value);
              updateMappings(
                mappings.map((mapping) => ({
                  ...mapping,
                  target: { ...mapping.target, schema: value },
                })),
              );
            }}
          />
        </div>
      ) : null}
      {replication ? (
        <>
          <TableMappingEditor
            prepareSchema={prepareSchema}
            writeModes={writeModes}
            mappings={mappings}
            targetTables={targetCatalog.tables}
            loading={targetCatalog.loading}
            error={targetCatalog.error}
            refresh={targetCatalog.refresh}
            onChange={updateMappings}
          />
          <ReplicationOptions
            mappings={mappings}
            metadata={replicationMetadata}
            initialLoad={initialLoad}
            onChange={updateMappings}
          />
        </>
      ) : (
        <TableSection
          prepareSchema={prepareSchema}
          onTableNameChange={(value) => patchSink("table", value)}
          dataSourceId={form.sink.dataSourceId}
          boundSchema={selectedTargetDataSource?.schema}
          database={form.sink.database}
          schema={form.sink.schema}
          table={form.sink.table}
          catalog={targetCatalog}
          onSchemaChange={(value) =>
            requestTargetChange(() => {
              setTaskDirty(true);
              setParametersDirty(true);
              setForm((current) => ({
                ...current,
                sink: { ...current.sink, schema: value, table: "" },
              }));
            })
          }
          onTableChange={(table) =>
            requestTargetChange(() => {
              setTaskDirty(true);
              setParametersDirty(true);
              setForm((current) => ({
                ...current,
                sink: {
                  ...current.sink,
                  database: current.sink.database || table.database || "",
                  schema: current.sink.schema || table.schema || "",
                  table: table.name,
                },
              }));
            })
          }
        >
          <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
            <FieldLabel required>写入方式</FieldLabel>
            <DataSyncSearchableSelect
              ariaLabel="写入方式"
              placeholder="请选择写入方式"
              value={form.sink.writeMode}
              disabled={writeModes.length < 2}
              options={writeModes.map((mode) => ({
                value: mode,
                label: mode === "UPSERT" ? "按主键更新" : "追加写入",
              }))}
              onValueChange={(value) => patchSink("writeMode", value as DataSyncWriteMode)}
            />
          </Field>
        </TableSection>
      )}
      {replication && continuous ? (
        <Field className="mt-4 flex items-center justify-between rounded-lg border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] px-4 py-3">
          <FieldLabel>同步结构变更</FieldLabel>
          <Switch
            aria-label="同步结构变更"
            size="small"
            checked={form.sink.schemaChangesEnabled}
            disabled={saving || targetType === "ORACLE"}
            onCheckedChange={(checked) => {
              patchSink("schemaChangesEnabled", checked);
              if (!checked) patchSink("allowDropColumns", false);
            }}
          />
        </Field>
      ) : null}
      {replication && continuous && form.sink.schemaChangesEnabled && targetType !== "ORACLE" ? (
        <Field className="mt-3 flex flex-col gap-2 rounded-lg border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] px-4 py-3">
          <div className="flex items-center justify-between">
            <FieldLabel>允许删除目标字段</FieldLabel>
            <Switch
              aria-label="允许删除目标字段"
              size="small"
              checked={form.sink.allowDropColumns}
              disabled={saving}
              onCheckedChange={(checked) => patchSink("allowDropColumns", checked)}
            />
          </div>
          {form.sink.allowDropColumns ? (
            <span className="text-xs text-[var(--yak-components-danger-text)]">
              来源字段删除后，目标端对应字段及数据也会删除。
            </span>
          ) : null}
        </Field>
      ) : null}
    </CollapseSection>
  );
}

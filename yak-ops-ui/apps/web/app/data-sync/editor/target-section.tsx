import { CollapseSection, Field, FieldLabel, Input, Switch } from "@yak-ops/yak-ui";
import { type Dispatch, type SetStateAction } from "react";
import { TableMappingEditor } from "./table-mapping-editor";
import { SchemaPreview } from "./schema-preview";
import { DataSyncSearchableSelect } from "../components/searchable-select";
import { TableSection } from "./table-section";
import type { DataSourceRecord } from "@/service/datasource";
import type {
  DataSyncTableMapping,
  DataSyncWriteMode,
  DataSyncTaskSavePayload,
} from "@/service/data-sync";
import type { CatalogOptions, EditorForm } from "./types";

interface Props {
  batch: boolean;
  prepareSchema: boolean;
  form: EditorForm;
  targetCatalog: CatalogOptions;
  selectedTargetDataSource?: DataSourceRecord;
  mappings: DataSyncTableMapping[];
  updateMappings: (value: DataSyncTableMapping[]) => void;
  writeModes: DataSyncWriteMode[];
  hasCombination: boolean;
  canSave: boolean;
  payload: () => DataSyncTaskSavePayload;
  usesChangelog: boolean;
  useIncremental: boolean;
  initialLoad: boolean;
  targetType: string;
  published: boolean;
  saving: boolean;
  patch: <K extends keyof EditorForm>(key: K, value: EditorForm[K]) => void;
  setTaskDirty: Dispatch<SetStateAction<boolean>>;
  setParametersDirty: Dispatch<SetStateAction<boolean>>;
  setForm: Dispatch<SetStateAction<EditorForm>>;
}
export function TargetSection({
  batch,
  prepareSchema,
  form,
  targetCatalog,
  selectedTargetDataSource,
  mappings,
  updateMappings,
  writeModes,
  hasCombination,
  canSave,
  payload,
  usesChangelog,
  useIncremental,
  initialLoad,
  targetType,
  published,
  saving,
  patch,
  setTaskDirty,
  setParametersDirty,
  setForm,
}: Props) {
  const combination = hasCombination ? { writeModes } : undefined;
  return (
    <CollapseSection id="target" title="目标表映射">
      {prepareSchema ? (
        <div className="mb-3 flex flex-wrap items-center gap-3">
          <SchemaPreview value={payload()} disabled={!canSave} />
          {batch && !selectedTargetDataSource?.schema && targetCatalog.schemas.length ? (
            <DataSyncSearchableSelect
              ariaLabel="去向 Schema"
              value={form.targetSchema || null}
              options={targetCatalog.schemas.map((value) => ({ value, label: value }))}
              placeholder="去向 Schema"
              onValueChange={(value) => {
                patch("targetSchema", value);
                updateMappings(
                  mappings.map((mapping) => ({
                    ...mapping,
                    target: { ...mapping.target, schema: value },
                  })),
                );
              }}
            />
          ) : null}
        </div>
      ) : null}
      {batch ? (
        <TableMappingEditor
          prepareSchema={prepareSchema}
          incremental={useIncremental}
          initialLoad={initialLoad}
          writeModes={combination?.writeModes || []}
          mappings={mappings}
          targetTables={targetCatalog.tables}
          loading={targetCatalog.loading}
          error={targetCatalog.error}
          refresh={targetCatalog.refresh}
          onChange={updateMappings}
        />
      ) : (
        <TableSection
          prepareSchema={prepareSchema}
          onTableNameChange={(value) => patch("targetTable", value)}
          dataSourceId={form.targetDataSourceId}
          boundSchema={selectedTargetDataSource?.schema}
          database={form.targetDatabase}
          schema={form.targetSchema}
          table={form.targetTable}
          catalog={targetCatalog}
          onSchemaChange={(value) => {
            setTaskDirty(true);
            setParametersDirty(true);
            setForm((current) => ({ ...current, targetSchema: value, targetTable: "" }));
          }}
          onTableChange={(table) => {
            setTaskDirty(true);
            setParametersDirty(true);
            setForm((current) => ({
              ...current,
              targetDatabase: current.targetDatabase || table.database || "",
              targetSchema: current.targetSchema || table.schema || "",
              targetTable: table.name,
            }));
          }}
        >
          <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
            <FieldLabel required>写入方式</FieldLabel>
            <DataSyncSearchableSelect
              placeholder="请选择写入方式"
              value={form.writeMode}
              disabled={!combination || combination.writeModes.length === 1 || usesChangelog}
              options={(combination?.writeModes || []).map((mode) => ({
                value: mode,
                label:
                  mode === "UPSERT" ? "按主键更新" : mode === "RETRACT" ? "逐行更新" : "追加写入",
              }))}
              onValueChange={(value) => patch("writeMode", value as DataSyncWriteMode)}
            />
          </Field>
          {usesChangelog && form.keylessEnabled ? (
            <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
              <FieldLabel>目标主键</FieldLabel>
              <Input
                size="small"
                variant="outlined"
                placeholder="字段名，多个字段用逗号分隔"
                value={form.targetPrimaryKeys.join(", ")}
                onChange={(event) => {
                  const keys = event.target.value.trim()
                    ? event.target.value.split(",").map((key) => key.trim())
                    : [];
                  patch("targetPrimaryKeys", keys);
                  patch("writeMode", keys.some(Boolean) ? "UPSERT" : "RETRACT");
                }}
              />
            </Field>
          ) : null}
        </TableSection>
      )}
      {usesChangelog ? (
        <Field className="mt-4 flex items-center justify-between rounded-lg border border-[var(--yak-components-panel-border)] bg-white px-4 py-3">
          <FieldLabel>同步结构变更</FieldLabel>
          <Switch
            aria-label="同步结构变更"
            size="small"
            checked={form.schemaChangesEnabled}
            disabled={published || saving || targetType === "ORACLE" || form.keylessEnabled}
            onCheckedChange={(checked) => {
              patch("schemaChangesEnabled", checked);
              if (!checked) patch("allowDropColumns", false);
            }}
          />
        </Field>
      ) : null}
      {usesChangelog && form.schemaChangesEnabled && targetType !== "ORACLE" ? (
        <Field className="mt-3 flex flex-col gap-2 rounded-lg border border-[var(--yak-components-panel-border)] bg-white px-4 py-3">
          <div className="flex items-center justify-between">
            <FieldLabel>允许删除目标字段</FieldLabel>
            <Switch
              aria-label="允许删除目标字段"
              size="small"
              checked={form.allowDropColumns}
              disabled={published || saving}
              onCheckedChange={(checked) => patch("allowDropColumns", checked)}
            />
          </div>
          {form.allowDropColumns ? (
            <span className="text-xs text-red-600">
              来源字段删除后，目标端对应字段及数据也会删除。
            </span>
          ) : null}
        </Field>
      ) : null}
    </CollapseSection>
  );
}

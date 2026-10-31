import { CollapseSection, Field, FieldLabel, Input, Switch } from "@yak-ops/yak-ui";
import { type Dispatch, type SetStateAction } from "react";
import { TableMappingEditor } from "./table-mapping-editor";
import { DataSyncSearchableSelect } from "../components/searchable-select";
import { TableSection } from "./table-section";
import type { DataSourceRecord } from "@/service/datasource";
import type { DataSyncTableMapping, DataSyncWriteMode } from "@/service/data-sync";
import type { CatalogOptions, EditorForm, EditorSink } from "./types";

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
  usesChangelog: boolean;
  continuous: boolean;
  useIncremental: boolean;
  initialLoad: boolean;
  targetType: string;
  published: boolean;
  saving: boolean;
  patchSink: <K extends keyof EditorSink>(key: K, value: EditorSink[K]) => void;
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
  usesChangelog,
  continuous,
  useIncremental,
  initialLoad,
  targetType,
  published,
  saving,
  patchSink,
  setTaskDirty,
  setParametersDirty,
  setForm,
}: Props) {
  const combination = hasCombination ? { writeModes } : undefined;
  return (
    <CollapseSection id="target" title="数据去向 Sink">
      {prepareSchema &&
      batch &&
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
          onTableNameChange={(value) => patchSink("table", value)}
          dataSourceId={form.sink.dataSourceId}
          boundSchema={selectedTargetDataSource?.schema}
          database={form.sink.database}
          schema={form.sink.schema}
          table={form.sink.table}
          catalog={targetCatalog}
          onSchemaChange={(value) => {
            setTaskDirty(true);
            setParametersDirty(true);
            setForm((current) => ({
              ...current,
              sink: { ...current.sink, schema: value, table: "" },
            }));
          }}
          onTableChange={(table) => {
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
          }}
        >
          <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
            <FieldLabel required>写入方式</FieldLabel>
            <DataSyncSearchableSelect
              placeholder="请选择写入方式"
              value={form.sink.writeMode}
              disabled={!combination || combination.writeModes.length === 1 || continuous}
              options={(combination?.writeModes || []).map((mode) => ({
                value: mode,
                label:
                  mode === "UPSERT" ? "按主键更新" : mode === "RETRACT" ? "逐行更新" : "追加写入",
              }))}
              onValueChange={(value) => patchSink("writeMode", value as DataSyncWriteMode)}
            />
          </Field>
          {usesChangelog &&
          form.source.sourceHasPrimaryKey === false &&
          (continuous || form.sink.writeMode === "UPSERT") ? (
            <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
              <FieldLabel>目标主键</FieldLabel>
              <Input
                size="small"
                variant="outlined"
                placeholder="字段名，多个字段用逗号分隔"
                value={form.sink.targetPrimaryKeys.join(", ")}
                onChange={(event) => {
                  const keys = event.target.value.trim()
                    ? event.target.value.split(",").map((key) => key.trim())
                    : [];
                  patchSink("targetPrimaryKeys", keys);
                  patchSink("writeMode", !continuous || keys.some(Boolean) ? "UPSERT" : "RETRACT");
                }}
              />
            </Field>
          ) : null}
        </TableSection>
      )}
      {usesChangelog && continuous ? (
        <Field className="mt-4 flex items-center justify-between rounded-lg border border-[var(--yak-components-panel-border)] bg-white px-4 py-3">
          <FieldLabel>同步结构变更</FieldLabel>
          <Switch
            aria-label="同步结构变更"
            size="small"
            checked={form.sink.schemaChangesEnabled}
            disabled={
              published ||
              saving ||
              targetType === "ORACLE" ||
              form.source.sourceHasPrimaryKey === false
            }
            onCheckedChange={(checked) => {
              patchSink("schemaChangesEnabled", checked);
              if (!checked) patchSink("allowDropColumns", false);
            }}
          />
        </Field>
      ) : null}
      {usesChangelog && continuous && form.sink.schemaChangesEnabled && targetType !== "ORACLE" ? (
        <Field className="mt-3 flex flex-col gap-2 rounded-lg border border-[var(--yak-components-panel-border)] bg-white px-4 py-3">
          <div className="flex items-center justify-between">
            <FieldLabel>允许删除目标字段</FieldLabel>
            <Switch
              aria-label="允许删除目标字段"
              size="small"
              checked={form.sink.allowDropColumns}
              disabled={published || saving}
              onCheckedChange={(checked) => patchSink("allowDropColumns", checked)}
            />
          </div>
          {form.sink.allowDropColumns ? (
            <span className="text-xs text-red-600">
              来源字段删除后，目标端对应字段及数据也会删除。
            </span>
          ) : null}
        </Field>
      ) : null}
    </CollapseSection>
  );
}

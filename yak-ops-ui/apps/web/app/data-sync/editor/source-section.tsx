import { Alert, CollapseSection, Field, FieldLabel, Switch } from "@yak-ops/yak-ui";
import { type Dispatch, type SetStateAction, useEffect, useState } from "react";
import { listDataSourceColumns, type DataSourceCatalogColumn } from "@/service/datasource";
import { DataSyncSearchableSelect } from "../components/searchable-select";
import { ExplicitTableSelection } from "./table-mapping-editor";
import { IncrementalFields } from "./incremental-fields";
import { TableSection } from "./table-section";
import type { DataSourceRecord } from "@/service/datasource";
import type { DataSyncTableMapping, DataSyncWriteMode } from "@/service/data-sync";
import type { CatalogOptions, EditorForm } from "./types";

interface Props {
  batch: boolean;
  prepareSchema: boolean;
  form: EditorForm;
  sourceCatalog: CatalogOptions;
  targetCatalog: CatalogOptions;
  selectedSourceDataSource?: DataSourceRecord;
  mappings: DataSyncTableMapping[];
  updateMappings: (value: DataSyncTableMapping[]) => void;
  writeMode?: DataSyncWriteMode;
  allowKeyless: boolean;
  usesChangelog: boolean;
  useIncremental: boolean;
  initialLoad: boolean;
  patch: <K extends keyof EditorForm>(key: K, value: EditorForm[K]) => void;
  setTaskDirty: Dispatch<SetStateAction<boolean>>;
  setParametersDirty: Dispatch<SetStateAction<boolean>>;
  setForm: Dispatch<SetStateAction<EditorForm>>;
}
export function SourceSection({
  batch,
  prepareSchema,
  form,
  sourceCatalog,
  targetCatalog,
  selectedSourceDataSource,
  mappings,
  updateMappings,
  writeMode,
  allowKeyless,
  usesChangelog,
  useIncremental,
  initialLoad,
  patch,
  setTaskDirty,
  setParametersDirty,
  setForm,
}: Props) {
  const combination = { writeModes: writeMode ? [writeMode] : [] };
  const [sourceColumns, setSourceColumns] = useState<DataSourceCatalogColumn[]>([]);
  const [columnsError, setColumnsError] = useState(false);
  useEffect(() => {
    if (!allowKeyless || !form.sourceDataSourceId || !form.sourceTable) {
      setSourceColumns([]);
      return;
    }
    let active = true;
    setSourceColumns([]);
    setColumnsError(false);
    void listDataSourceColumns(form.sourceDataSourceId, {
      database: form.sourceDatabase || undefined,
      schema: form.sourceSchema || undefined,
      table: form.sourceTable,
    })
      .then((columns) => {
        if (active) setSourceColumns(columns);
      })
      .catch(() => {
        if (active) setColumnsError(true);
      });
    return () => {
      active = false;
    };
  }, [
    allowKeyless,
    form.sourceDataSourceId,
    form.sourceDatabase,
    form.sourceSchema,
    form.sourceTable,
  ]);
  const chunkColumns = sourceColumns.filter(
    (column) =>
      column.nullable === false &&
      /^(tinyint|smallint|int(?:eger)?|bigint|decimal|numeric)(\b|\()/i.test(column.typeName || ""),
  );
  return (
    <CollapseSection id="source" title="数据来源">
      {batch ? (
        <ExplicitTableSelection
          prepareSchema={prepareSchema}
          targetNamespace={{
            database: form.targetDatabase || undefined,
            schema: form.targetSchema || undefined,
          }}
          writeMode={combination?.writeModes[0]}
          sourceTables={sourceCatalog.tables}
          targetTables={targetCatalog.tables}
          mappings={mappings}
          loading={sourceCatalog.loading}
          error={sourceCatalog.error}
          refresh={sourceCatalog.refresh}
          onChange={updateMappings}
        />
      ) : (
        <TableSection
          dataSourceId={form.sourceDataSourceId}
          boundSchema={selectedSourceDataSource?.schema}
          database={form.sourceDatabase}
          schema={form.sourceSchema}
          table={form.sourceTable}
          catalog={sourceCatalog}
          onSchemaChange={(value) => {
            setTaskDirty(true);
            setParametersDirty(true);
            setForm((current) => ({
              ...current,
              sourceSchema: value,
              sourceTable: "",
              keylessEnabled: false,
              chunkKeyColumn: "",
              targetPrimaryKeys: [],
              writeMode: usesChangelog ? "UPSERT" : current.writeMode,
            }));
          }}
          onTableChange={(table) => {
            setTaskDirty(true);
            setParametersDirty(true);
            setForm((current) => ({
              ...current,
              sourceDatabase: current.sourceDatabase || table.database || "",
              sourceSchema: current.sourceSchema || table.schema || "",
              sourceTable: table.name,
              keylessEnabled: false,
              chunkKeyColumn: "",
              targetPrimaryKeys: [],
              writeMode: usesChangelog ? "UPSERT" : current.writeMode,
            }));
          }}
        >
          {usesChangelog ? (
            <Alert>实时同步依赖 ROW Binlog 和 CDC 权限；连接测试通过不代表 CDC 可用。</Alert>
          ) : null}
          {allowKeyless ? (
            <>
              <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
                <FieldLabel>无主键表</FieldLabel>
                <Switch
                  aria-label="无主键表"
                  size="small"
                  checked={form.keylessEnabled}
                  onCheckedChange={(enabled) => {
                    setTaskDirty(true);
                    setParametersDirty(true);
                    setForm((current) => ({
                      ...current,
                      keylessEnabled: enabled,
                      chunkKeyColumn: enabled ? current.chunkKeyColumn : "",
                      targetPrimaryKeys: [],
                      writeMode: enabled ? "RETRACT" : "UPSERT",
                      schemaChangesEnabled: enabled ? false : current.schemaChangesEnabled,
                      allowDropColumns: enabled ? false : current.allowDropColumns,
                    }));
                  }}
                />
              </Field>
              {form.keylessEnabled ? (
                <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
                  <FieldLabel required>快照分片字段</FieldLabel>
                  <div className="w-1/2 min-w-0 max-lg:w-full">
                    <DataSyncSearchableSelect
                      placeholder="选择非空数值字段"
                      value={form.chunkKeyColumn || null}
                      options={chunkColumns.map((column) => ({
                        value: column.name,
                        label: column.name,
                      }))}
                      onValueChange={(value) => patch("chunkKeyColumn", value)}
                    />
                  </div>
                </Field>
              ) : null}
              {columnsError && form.keylessEnabled ? <Alert>来源字段加载失败</Alert> : null}
            </>
          ) : null}
          {useIncremental ? (
            <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-start !gap-3">
              <FieldLabel required>增量设置</FieldLabel>
              <div className="w-1/2 min-w-0 max-lg:w-full">
                <IncrementalFields
                  value={form.incremental}
                  initialLoad={initialLoad}
                  onChange={(value) => patch("incremental", value)}
                />
              </div>
            </Field>
          ) : null}
        </TableSection>
      )}
    </CollapseSection>
  );
}

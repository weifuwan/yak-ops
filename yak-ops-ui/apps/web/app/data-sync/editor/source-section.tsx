import { Alert, CollapseSection, Field, FieldLabel } from "@yak-ops/yak-ui";
import { useEffect, useState } from "react";
import {
  listDataSourceColumns,
  type DataSourceCatalogColumn,
  type DataSourceCatalogTable,
} from "@/service/datasource";
import { DataSyncSearchableSelect } from "../components/searchable-select";
import { ExplicitTableSelection } from "./table-mapping-editor";
import { IncrementalFields } from "./incremental-fields";
import { TableSection } from "./table-section";
import type { DataSourceRecord } from "@/service/datasource";
import type { DataSyncTableMapping, DataSyncWriteMode } from "@/service/data-sync";
import type { CatalogOptions, EditorForm, EditorSource } from "./types";

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
  allowChunkKey: boolean;
  usesChangelog: boolean;
  continuous: boolean;
  useIncremental: boolean;
  initialLoad: boolean;
  startupMode?: string;
  patchSource: <K extends keyof EditorSource>(key: K, value: EditorSource[K]) => void;
  onSourceSchemaChange: (schema: string) => void;
  onSourceTableChange: (table: DataSourceCatalogTable) => void;
  onSourcePrimaryKeys: (hasPrimaryKey: boolean) => void;
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
  allowChunkKey,
  usesChangelog,
  continuous,
  useIncremental,
  initialLoad,
  startupMode,
  patchSource,
  onSourceSchemaChange,
  onSourceTableChange,
  onSourcePrimaryKeys,
}: Props) {
  const combination = { writeModes: writeMode ? [writeMode] : [] };
  const [sourceColumns, setSourceColumns] = useState<DataSourceCatalogColumn[]>([]);
  const [columnsError, setColumnsError] = useState(false);
  useEffect(() => {
    if (!usesChangelog || !form.source.dataSourceId || !form.source.table) {
      setSourceColumns([]);
      return;
    }
    let active = true;
    setSourceColumns([]);
    setColumnsError(false);
    void listDataSourceColumns(form.source.dataSourceId, {
      database: form.source.database || undefined,
      schema: form.source.schema || undefined,
      table: form.source.table,
    })
      .then((columns) => {
        if (active) {
          setSourceColumns(columns);
          onSourcePrimaryKeys(columns.some((column) => column.primaryKey === true));
        }
      })
      .catch(() => {
        if (active) setColumnsError(true);
      });
    return () => {
      active = false;
    };
  }, [
    usesChangelog,
    form.source.dataSourceId,
    form.source.database,
    form.source.schema,
    form.source.table,
  ]);
  const chunkColumns = sourceColumns.filter(
    (column) =>
      column.nullable === false &&
      /^(tinyint|smallint|int(?:eger)?|bigint|decimal|numeric)(\b|\()/i.test(column.typeName || ""),
  );
  return (
    <CollapseSection id="source" title="数据来源 Source">
      {batch ? (
        <ExplicitTableSelection
          prepareSchema={prepareSchema}
          targetNamespace={{
            database: form.sink.database || undefined,
            schema: form.sink.schema || undefined,
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
          dataSourceId={form.source.dataSourceId}
          boundSchema={selectedSourceDataSource?.schema}
          database={form.source.database}
          schema={form.source.schema}
          table={form.source.table}
          catalog={sourceCatalog}
          onSchemaChange={onSourceSchemaChange}
          onTableChange={onSourceTableChange}
        >
          {usesChangelog && continuous ? (
            <Alert>实时同步依赖 ROW Binlog 和 CDC 权限；连接测试通过不代表 CDC 可用。</Alert>
          ) : null}
          {usesChangelog && !continuous ? (
            <Alert>仅全量同步会读取当前表快照，完成后自动结束，不会持续读取 Binlog。</Alert>
          ) : null}
          {usesChangelog && !initialLoad ? (
            <Alert>
              {startupMode && startupMode !== "LATEST_OFFSET"
                ? `当前使用 ${startupMode} 启动位置，修改其他参数不会重置该位置。`
                : "仅从任务启动时的最新 Binlog 位点同步变更，不会补录已有数据。"}
            </Alert>
          ) : null}
          {columnsError && usesChangelog && !allowChunkKey ? <Alert>来源字段加载失败</Alert> : null}
          {allowChunkKey ? (
            <>
              <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-center !gap-3">
                <FieldLabel required={form.source.sourceHasPrimaryKey === false}>
                  快照分片字段
                </FieldLabel>
                <div className="w-1/2 min-w-0 max-lg:w-full">
                  <DataSyncSearchableSelect
                    placeholder={
                      form.source.sourceHasPrimaryKey ? "默认使用来源主键" : "选择非空数值字段"
                    }
                    value={form.source.chunkKeyColumn || null}
                    options={chunkColumns.map((column) => ({
                      value: column.name,
                      label: column.name,
                    }))}
                    onValueChange={(value) => patchSource("chunkKeyColumn", value)}
                  />
                </div>
              </Field>
              {columnsError ? <Alert>来源字段加载失败</Alert> : null}
            </>
          ) : null}
          {useIncremental ? (
            <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-start !gap-3">
              <FieldLabel required>增量设置</FieldLabel>
              <div className="w-1/2 min-w-0 max-lg:w-full">
                <IncrementalFields
                  value={form.source.incremental}
                  initialLoad={initialLoad}
                  onChange={(value) => patchSource("incremental", value)}
                />
              </div>
            </Field>
          ) : null}
        </TableSection>
      )}
    </CollapseSection>
  );
}

import { CollapseSection, Field, FieldLabel } from "@yak-ops/yak-ui";
import { ExplicitTableSelection } from "./table-mapping-editor";
import { IncrementalFields } from "./incremental-fields";
import { TableSection } from "./table-section";
import type { DataSourceRecord, DataSourceCatalogTable } from "@/service/datasource";
import type { DataSyncTableMapping, DataSyncWriteMode } from "@/service/data-sync";
import type { CatalogOptions, EditorForm, EditorSource } from "./types";

interface Props {
  replication: boolean;
  prepareSchema: boolean;
  form: EditorForm;
  sourceCatalog: CatalogOptions;
  targetCatalog: CatalogOptions;
  selectedSourceDataSource?: DataSourceRecord;
  mappings: DataSyncTableMapping[];
  updateMappings: (value: DataSyncTableMapping[]) => void;
  writeMode?: DataSyncWriteMode;
  useIncremental: boolean;
  initialLoad: boolean;
  patchSource: <K extends keyof EditorSource>(key: K, value: EditorSource[K]) => void;
  onSourceSchemaChange: (schema: string) => void;
  onSourceTableChange: (table: DataSourceCatalogTable) => void;
}
export function SourceSection({
  replication,
  prepareSchema,
  form,
  sourceCatalog,
  targetCatalog,
  selectedSourceDataSource,
  mappings,
  updateMappings,
  writeMode,
  useIncremental,
  initialLoad,
  patchSource,
  onSourceSchemaChange,
  onSourceTableChange,
}: Props) {
  return (
    <CollapseSection id="source" title="数据来源">
      {replication ? (
        <ExplicitTableSelection
          prepareSchema={prepareSchema}
          targetNamespace={{
            database: form.sink.database || undefined,
            schema: form.sink.schema || undefined,
          }}
          writeMode={writeMode}
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
          {useIncremental ? (
            <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3">
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

import { CollapseSection } from "@yak-ops/yak-ui";
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
  form: EditorForm;
  targetCatalog: CatalogOptions;
  selectedTargetDataSource?: DataSourceRecord;
  mappings: DataSyncTableMapping[];
  updateMappings: (value: DataSyncTableMapping[]) => void;
  writeModes: DataSyncWriteMode[];
  replicationMetadata: ReplicationMetadata;
  initialLoad: boolean;
  patchSink: <K extends keyof EditorSink>(key: K, value: EditorSink[K]) => void;
  setForm: Dispatch<SetStateAction<EditorForm>>;
  requestTargetChange: (apply: () => void) => void;
}
export function TargetSection({
  replication,
  prepareSchema,
  form,
  targetCatalog,
  selectedTargetDataSource,
  mappings,
  updateMappings,
  writeModes,
  replicationMetadata,
  initialLoad,
  patchSink,
  setForm,
  requestTargetChange,
}: Props) {
  return (
    <CollapseSection id="target" title="数据去向">
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
              setForm((current) => ({
                ...current,
                sink: { ...current.sink, schema: value, table: "" },
              }));
            })
          }
          onTableChange={(table) =>
            requestTargetChange(() => {
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
        />
      )}
    </CollapseSection>
  );
}

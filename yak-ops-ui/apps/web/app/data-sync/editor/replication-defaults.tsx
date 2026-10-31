import { CollapseSection, Input } from "@yak-ops/yak-ui";
import type {
  DataSyncCapabilities,
  DataSyncMappingTemplate,
  DataSyncTableSelection,
  DataSyncWriteMode,
} from "@/service/data-sync";
import { DataSyncSearchableSelect } from "../components/searchable-select";
import { ContentField } from "./content-layout";
import { DmlFields, SinkEngineFields, SnapshotFields } from "./table-option-fields";

export function ReplicationDefaults({
  selection,
  template,
  capabilities,
  initialLoad,
  onChange,
}: {
  selection: DataSyncTableSelection;
  template: DataSyncMappingTemplate;
  capabilities: DataSyncCapabilities;
  initialLoad: boolean;
  onChange: (selection: DataSyncTableSelection, template: DataSyncMappingTemplate) => void;
}) {
  return (
    <CollapseSection
      title="新表默认配置"
      defaultOpen={false}
      triggerClassName="!h-8 rounded-sm"
      contentClassName="space-y-3 pt-3"
    >
      <SnapshotFields
        value={selection.snapshot}
        capabilities={capabilities}
        initialLoad={initialLoad}
        onChange={(snapshot) => onChange({ ...selection, snapshot }, template)}
      />
      {capabilities.dmlEvents ? (
        <DmlFields
          value={selection.dml}
          onChange={(dml) => onChange({ ...selection, dml }, template)}
        />
      ) : null}
      <ContentField label="写入方式">
        <DataSyncSearchableSelect
          ariaLabel="新表写入方式"
          value={template.write.mode}
          placeholder="写入方式"
          options={capabilities.writeModes
            .filter((mode) => mode === "UPSERT")
            .map((value) => ({ value, label: value === "UPSERT" ? "按主键更新" : "追加写入" }))}
          onValueChange={(mode) =>
            onChange(selection, {
              ...template,
              write: { ...template.write, mode: mode as DataSyncWriteMode },
            })
          }
        />
      </ContentField>
      <ContentField label="目标表同步主键">
        <Input
          size="small"
          variant="outlined"
          aria-label="新表目标主键"
          disabled
          placeholder="默认使用来源物理主键"
          value={(template.write.primaryKeys ?? []).join(", ")}
          onChange={(event) =>
            onChange(selection, {
              ...template,
              write: {
                ...template.write,
                primaryKeys: event.target.value.trim()
                  ? event.target.value.split(",").map((key) => key.trim())
                  : undefined,
              },
            })
          }
        />
      </ContentField>
      <SinkEngineFields
        batchSize={template.write.batchSize}
        keys={capabilities.sinkEngineKeys}
        onChange={(batchSize) =>
          onChange(selection, { ...template, write: { ...template.write, batchSize } })
        }
      />
    </CollapseSection>
  );
}

import { useState } from "react";
import { Alert, Button, Input, Modal, Tabs, TabsList, TabsPanel, TabsTab } from "@yak-ops/yak-ui";
import type { DataSourceCatalogTable } from "@/service/datasource";
import type {
  DataSyncCapabilities,
  DataSyncTableMapping,
  DataSyncWriteMode,
} from "@/service/data-sync";
import {
  validateBatchSize,
  validateSourceOptions,
  validateTableDml,
  validateTableSnapshot,
} from "@/service/data-sync/table-configuration";
import { parameterFingerprint } from "@/service/data-sync/versions";
import { useUnsavedChanges } from "@/hooks/use-unsaved-changes";
import { DataSyncSearchableSelect } from "../components/searchable-select";
import { ContentField } from "./content-layout";
import {
  DmlFields,
  SinkEngineFields,
  SnapshotFields,
  SourceEngineFields,
} from "./table-option-fields";
import { catalogPath, tableKey, tablePathLabel } from "./table-catalog";

export type MappingTab = "mapping" | "sync" | "engine";

export function MappingSettingsDialog({
  value,
  initialTab = "mapping",
  capabilities,
  prepareSchema,
  initialLoad,
  targetTables,
  writeModes,
  schemaChanges,
  onApply,
  onClose,
}: {
  value: DataSyncTableMapping;
  initialTab?: MappingTab;
  capabilities: DataSyncCapabilities;
  prepareSchema: boolean;
  initialLoad: boolean;
  targetTables: DataSourceCatalogTable[];
  writeModes: DataSyncWriteMode[];
  schemaChanges: boolean;
  onApply: (mapping: DataSyncTableMapping) => void;
  onClose: () => void;
}) {
  const [mapping, setMapping] = useState(() => structuredClone(value));
  const [tab, setTab] = useState(initialTab);
  const [error, setError] = useState("");
  useUnsavedChanges(parameterFingerprint(mapping) !== parameterFingerprint(value));
  const apply = () => {
    try {
      if (!mapping.target.table.trim()) throw new Error("请选择或填写目标表");
      if (
        !prepareSchema &&
        !targetTables.some((table) => tableKey(catalogPath(table)) === tableKey(mapping.target))
      )
        throw new Error("请选择已有目标表");
      if (!writeModes.includes(mapping.writeMode)) throw new Error("请选择支持的写入方式");
      if (
        mapping.target.primaryKeys &&
        (mapping.target.primaryKeys.some((key) => !key.trim()) ||
          new Set(mapping.target.primaryKeys).size !== mapping.target.primaryKeys.length)
      )
        throw new Error("目标主键不能为空或重复");
      if (schemaChanges && mapping.target.primaryKeys?.length)
        throw new Error("自定义目标主键需先关闭同步结构变更");
      validateTableSnapshot(mapping.source.snapshot);
      validateTableDml(mapping.source.dml);
      validateSourceOptions(mapping.source.options);
      validateBatchSize(mapping.batchSize);
      if (mapping.writeMode === "RETRACT" && mapping.batchSize !== undefined)
        throw new Error("逐行更新不支持设置写入批次大小");
      onApply(mapping);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "表级配置无效");
    }
  };
  return (
    <Modal
      open
      title="目标表配置"
      width={840}
      onClose={onClose}
      footer={
        <>
          <Button size="small" variant="primary" onClick={apply}>
            确认
          </Button>
          <Button size="small" onClick={onClose}>
            取消
          </Button>
        </>
      }
    >
      <div className="mb-3 text-xs text-[var(--yak-components-muted-text)]">
        {tablePathLabel(value.source)}
      </div>
      <Tabs value={tab} onValueChange={(next) => setTab(next as MappingTab)}>
        <TabsList>
          <TabsTab value="mapping">目标映射</TabsTab>
          <TabsTab value="sync">同步配置</TabsTab>
          {capabilities.sourceEngineKeys.length || capabilities.sinkEngineKeys.length ? (
            <TabsTab value="engine">引擎参数</TabsTab>
          ) : null}
        </TabsList>
        <TabsPanel value="mapping" className="space-y-3 pt-4">
          {prepareSchema ? (
            <>
              <ContentField label="目标库名">
                <Input
                  size="small"
                  variant="outlined"
                  aria-label="目标库名"
                  value={mapping.target.database || ""}
                  onChange={(event) =>
                    setMapping({
                      ...mapping,
                      target: { ...mapping.target, database: event.target.value || undefined },
                    })
                  }
                />
              </ContentField>
              {mapping.target.schema !== undefined ? (
                <ContentField label="目标 Schema">
                  <Input
                    size="small"
                    variant="outlined"
                    aria-label="目标 Schema"
                    value={mapping.target.schema}
                    onChange={(event) =>
                      setMapping({
                        ...mapping,
                        target: { ...mapping.target, schema: event.target.value || undefined },
                      })
                    }
                  />
                </ContentField>
              ) : null}
              <ContentField label="目标表名" required>
                <Input
                  size="small"
                  variant="outlined"
                  aria-label="目标表名"
                  value={mapping.target.table}
                  maxLength={128}
                  onChange={(event) =>
                    setMapping({
                      ...mapping,
                      target: { ...mapping.target, table: event.target.value },
                    })
                  }
                />
              </ContentField>
            </>
          ) : (
            <ContentField label="目标表" required>
              <DataSyncSearchableSelect
                ariaLabel="目标表"
                value={mapping.target.table ? tableKey(mapping.target) : null}
                placeholder="请选择已有目标表"
                options={targetTables.map((table) => ({
                  value: tableKey(catalogPath(table)),
                  label: tablePathLabel(catalogPath(table)),
                }))}
                onValueChange={(key) => {
                  const found = targetTables.find((table) => tableKey(catalogPath(table)) === key);
                  if (found)
                    setMapping({
                      ...mapping,
                      target: { ...catalogPath(found), primaryKeys: mapping.target.primaryKeys },
                    });
                }}
              />
            </ContentField>
          )}
          <ContentField label="写入方式">
            <DataSyncSearchableSelect
              ariaLabel="写入方式"
              value={mapping.writeMode}
              placeholder="请选择写入方式"
              options={writeModes.map((mode) => ({
                value: mode,
                label:
                  mode === "UPSERT" ? "按主键更新" : mode === "RETRACT" ? "逐行更新" : "追加写入",
              }))}
              disabled={writeModes.length < 2}
              onValueChange={(mode) =>
                setMapping({
                  ...mapping,
                  writeMode: mode as DataSyncWriteMode,
                  batchSize: mode === "RETRACT" ? undefined : mapping.batchSize,
                  target: {
                    ...mapping.target,
                    ...(mode === "RETRACT" ? { primaryKeys: undefined } : {}),
                  },
                })
              }
            />
          </ContentField>
        </TabsPanel>
        <TabsPanel value="sync" className="space-y-3 pt-4">
          <ContentField label="目标表同步主键">
            <Input
              size="small"
              variant="outlined"
              aria-label="目标主键"
              placeholder={
                mapping.writeMode === "RETRACT" ? "逐行更新不设置目标主键" : "默认使用来源物理主键"
              }
              disabled={mapping.writeMode === "RETRACT" || schemaChanges}
              value={(mapping.target.primaryKeys ?? []).join(", ")}
              onChange={(event) =>
                setMapping({
                  ...mapping,
                  target: {
                    ...mapping.target,
                    primaryKeys: event.target.value.trim()
                      ? event.target.value.split(",").map((key) => key.trim())
                      : undefined,
                  },
                })
              }
            />
          </ContentField>
          <SnapshotFields
            value={mapping.source.snapshot}
            initialLoad={initialLoad}
            capabilities={capabilities}
            onChange={(snapshot) =>
              setMapping({ ...mapping, source: { ...mapping.source, snapshot } })
            }
          />
          {capabilities.dmlEvents ? (
            <DmlFields
              value={mapping.source.dml}
              onChange={(dml) => setMapping({ ...mapping, source: { ...mapping.source, dml } })}
            />
          ) : null}
        </TabsPanel>
        <TabsPanel value="engine" className="space-y-3 pt-4">
          <SourceEngineFields
            value={mapping.source.options}
            keys={capabilities.sourceEngineKeys}
            onChange={(options) =>
              setMapping({ ...mapping, source: { ...mapping.source, options } })
            }
          />
          <SinkEngineFields
            batchSize={mapping.batchSize}
            keys={capabilities.sinkEngineKeys}
            disabled={mapping.writeMode === "RETRACT"}
            onChange={(batchSize) => setMapping({ ...mapping, batchSize })}
          />
        </TabsPanel>
      </Tabs>
      {error ? <Alert className="mt-3">{error}</Alert> : null}
    </Modal>
  );
}

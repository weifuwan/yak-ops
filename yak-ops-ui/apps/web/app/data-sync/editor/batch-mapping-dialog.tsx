import { useState } from "react";
import { Alert, Button, Checkbox, Input, Modal, Textarea } from "@yak-ops/yak-ui";
import type {
  DataSyncCapabilities,
  DataSyncDml,
  DataSyncTableMapping,
  DataSyncWriteMode,
} from "@/service/data-sync";
import {
  applyTableTemplate,
  validateBatchSize,
  validateTableDml,
  validateTableSnapshot,
} from "@/service/data-sync/table-configuration";
import { useUnsavedChanges } from "@/hooks/use-unsaved-changes";
import { DataSyncSearchableSelect } from "../components/searchable-select";
import { ContentField } from "./content-layout";
import { DmlFields } from "./table-option-fields";
import { tableKey } from "./table-catalog";

type BatchField =
  | "database"
  | "table"
  | "write"
  | "keys"
  | "chunk"
  | "skip"
  | "where"
  | "dml"
  | "batchSize";

export function BatchMappingDialog({
  mappings,
  selectedKeys,
  capabilities,
  writeModes,
  initialLoad,
  schemaChanges,
  onApply,
  onClose,
}: {
  mappings: DataSyncTableMapping[];
  selectedKeys: string[];
  capabilities: DataSyncCapabilities;
  writeModes: DataSyncWriteMode[];
  initialLoad: boolean;
  schemaChanges: boolean;
  onApply: (mappings: DataSyncTableMapping[]) => void;
  onClose: () => void;
}) {
  const [fields, setFields] = useState<BatchField[]>([]);
  const [database, setDatabase] = useState("");
  const [table, setTable] = useState("${table}");
  const [mode, setMode] = useState<DataSyncWriteMode>(writeModes[0]);
  const [keys, setKeys] = useState("");
  const [chunk, setChunk] = useState("");
  const [skip, setSkip] = useState(false);
  const [where, setWhere] = useState("");
  const [dml, setDml] = useState<DataSyncDml>({ events: ["INSERT", "UPDATE", "DELETE"] });
  const [batchSize, setBatchSize] = useState("");
  const [error, setError] = useState("");
  useUnsavedChanges(fields.length > 0);
  const options: Array<{ value: BatchField; label: string }> = [
    { value: "database", label: "目标库名" },
    { value: "table", label: "目标表模板" },
    { value: "write", label: "写入方式" },
    ...(!schemaChanges ? [{ value: "keys" as const, label: "目标同步主键" }] : []),
    ...(initialLoad ? [{ value: "chunk" as const, label: "源端切分列" }] : []),
    ...(initialLoad && capabilities.snapshotSkip
      ? [{ value: "skip" as const, label: "跳过全量" }]
      : []),
    ...(initialLoad && capabilities.snapshotFilter
      ? [{ value: "where" as const, label: "全量条件" }]
      : []),
    ...(capabilities.dmlEvents ? [{ value: "dml" as const, label: "DML 规则" }] : []),
    ...(capabilities.sinkEngineKeys.includes("batchSize")
      ? [{ value: "batchSize" as const, label: "写入批次大小" }]
      : []),
  ];
  const apply = () => {
    try {
      if (!fields.length) throw new Error("请选择要修改的配置项");
      const selected = new Set(selectedKeys);
      const next = mappings.map((original) => {
        if (!selected.has(tableKey(original.source))) return original;
        const mapping = structuredClone(original);
        if (fields.includes("database"))
          mapping.target.database = applyTableTemplate(database, original.source);
        if (fields.includes("table")) {
          const name = applyTableTemplate(table, original.source);
          if (!name?.trim()) throw new Error("目标表模板不能为空");
          mapping.target.table = name;
        }
        if (fields.includes("write")) {
          mapping.writeMode = mode;
          if (mode === "RETRACT") {
            mapping.target.primaryKeys = undefined;
            mapping.batchSize = undefined;
          }
        }
        if (fields.includes("keys"))
          mapping.target.primaryKeys = keys.trim()
            ? keys.split(",").map((key) => key.trim())
            : undefined;
        if (fields.includes("chunk"))
          mapping.source.snapshot = {
            ...mapping.source.snapshot,
            chunkKeyColumn: chunk || undefined,
          };
        if (fields.includes("skip"))
          mapping.source.snapshot = { ...mapping.source.snapshot, skip: skip || undefined };
        if (fields.includes("where"))
          mapping.source.snapshot = {
            ...mapping.source.snapshot,
            where: where.trim() || undefined,
          };
        if (fields.includes("dml")) mapping.source.dml = structuredClone(dml);
        if (fields.includes("batchSize"))
          mapping.batchSize = batchSize ? Number(batchSize) : undefined;
        validateBatchSize(mapping.batchSize);
        if (
          mapping.writeMode === "RETRACT" &&
          (mapping.batchSize !== undefined || mapping.target.primaryKeys?.length)
        )
          throw new Error("逐行更新不支持自定义目标主键和写入批次大小");
        validateTableSnapshot(mapping.source.snapshot);
        validateTableDml(mapping.source.dml);
        if (
          mapping.target.primaryKeys &&
          (mapping.target.primaryKeys.some((key) => !key) ||
            new Set(mapping.target.primaryKeys).size !== mapping.target.primaryKeys.length)
        )
          throw new Error("目标主键不能为空或重复");
        return mapping;
      });
      if (
        new Set(
          next.filter((mapping) => mapping.target.table).map((mapping) => tableKey(mapping.target)),
        ).size !== next.filter((mapping) => mapping.target.table).length
      )
        throw new Error("批量修改产生了重复目标表");
      onApply(next);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "批量修改失败");
    }
  };
  return (
    <Modal
      open
      title={`批量修改 ${selectedKeys.length} 张表`}
      width={820}
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
      <div className="space-y-4">
        <div className="flex flex-wrap gap-4">
          {options.map((option) => (
            <label key={option.value} className="flex items-center gap-2 text-xs">
              <Checkbox
                checked={fields.includes(option.value)}
                onCheckedChange={(checked) =>
                  setFields(
                    checked
                      ? [...fields, option.value]
                      : fields.filter((field) => field !== option.value),
                  )
                }
              />
              {option.label}
            </label>
          ))}
        </div>
        {fields.includes("database") ? (
          <ContentField label="目标库名">
            <Input
              size="small"
              variant="outlined"
              aria-label="批量目标库名"
              value={database}
              onChange={(event) => setDatabase(event.target.value)}
            />
          </ContentField>
        ) : null}
        {fields.includes("table") ? (
          <ContentField label="目标表模板">
            <Input
              size="small"
              variant="outlined"
              aria-label="批量目标表模板"
              value={table}
              onChange={(event) => setTable(event.target.value)}
            />
          </ContentField>
        ) : null}
        {fields.includes("write") ? (
          <ContentField label="写入方式">
            <DataSyncSearchableSelect
              ariaLabel="批量写入方式"
              value={mode}
              placeholder="选择写入方式"
              options={writeModes
                .filter((value) => value !== "RETRACT" || mappings.length === 1)
                .map((value) => ({
                  value,
                  label:
                    value === "UPSERT"
                      ? "按主键更新"
                      : value === "RETRACT"
                        ? "逐行更新"
                        : "追加写入",
                }))}
              onValueChange={(value) => setMode(value as DataSyncWriteMode)}
            />
          </ContentField>
        ) : null}
        {fields.includes("keys") ? (
          <ContentField label="目标同步主键">
            <Input
              size="small"
              variant="outlined"
              aria-label="批量目标主键"
              value={keys}
              placeholder="留空恢复来源主键"
              onChange={(event) => setKeys(event.target.value)}
            />
          </ContentField>
        ) : null}
        {fields.includes("chunk") ? (
          <ContentField label="源端切分列">
            <Input
              size="small"
              variant="outlined"
              aria-label="批量源端切分列"
              value={chunk}
              onChange={(event) => setChunk(event.target.value)}
            />
          </ContentField>
        ) : null}
        {fields.includes("skip") ? (
          <ContentField label="跳过全量">
            <Checkbox aria-label="批量跳过全量" checked={skip} onCheckedChange={setSkip} />
          </ContentField>
        ) : null}
        {fields.includes("where") ? (
          <ContentField label="全量条件" wide>
            <Textarea
              size="small"
              variant="outlined"
              aria-label="批量全量条件"
              value={where}
              onValueChange={(value) => setWhere(String(value))}
            />
          </ContentField>
        ) : null}
        {fields.includes("dml") ? <DmlFields value={dml} onChange={setDml} /> : null}
        {fields.includes("batchSize") ? (
          <ContentField label="写入批次大小">
            <Input
              type="number"
              min={1}
              max={10000}
              size="small"
              variant="outlined"
              aria-label="批量写入批次大小"
              value={batchSize}
              onChange={(event) => setBatchSize(event.target.value)}
            />
          </ContentField>
        ) : null}
        {error ? <Alert>{error}</Alert> : null}
      </div>
    </Modal>
  );
}

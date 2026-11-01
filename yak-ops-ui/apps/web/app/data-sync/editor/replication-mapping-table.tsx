import { useState } from "react";
import { Alert, Button, Checkbox, Table, type TableColumns } from "@yak-ops/yak-ui";
import type {
  DataSyncCapabilities,
  DataSyncTableMapping,
  DataSyncTransform,
  DataSyncTypeMapping,
  DataSyncWriteMode,
} from "@/service/data-sync";
import { replaceTableTransforms, transformTableSelector } from "@/service/data-sync/transforms";
import { DataSyncSearchableSelect } from "../components/searchable-select";
import { ContentSection } from "./content-layout";
import { tableKey, tablePathLabel } from "./table-catalog";
import { MappingSettingsDialog, type MappingTab } from "./mapping-settings-dialog";
import { BatchMappingDialog } from "./batch-mapping-dialog";
import { TransformRulesDialog } from "./transform-rules-dialog";
import { TypeMappingDialog } from "./type-mapping-dialog";
import type { CatalogOptions } from "./types";
import type { EditorConfirmation } from "./use-definition-restoration";

export type MappingDialog =
  | { type: "table"; key: string; tab: MappingTab }
  | { type: "batch" }
  | { type: "expressions"; selector?: string }
  | { type: "types" };

interface Props {
  mappings: DataSyncTableMapping[];
  baseline: DataSyncTableMapping[];
  targetCatalog: CatalogOptions;
  capabilities?: DataSyncCapabilities;
  writeModes: DataSyncWriteMode[];
  initialLoad: boolean;
  prepareSchema: boolean;
  schemaChanges: boolean;
  regex: boolean;
  transforms: DataSyncTransform[];
  typeMappings?: DataSyncTypeMapping[];
  dialog?: MappingDialog;
  onDialogChange: (dialog: MappingDialog | undefined) => void;
  disabled: boolean;
  onChange: (mappings: DataSyncTableMapping[]) => void;
  onTransformsChange: (rules: DataSyncTransform[]) => void;
  onTypeMappingsChange: (rules: DataSyncTypeMapping[]) => void;
  confirm: (confirmation: EditorConfirmation) => void;
}

export function ReplicationMappingTable({
  mappings,
  baseline,
  targetCatalog,
  capabilities,
  writeModes,
  initialLoad,
  prepareSchema,
  schemaChanges,
  regex,
  transforms,
  typeMappings,
  dialog,
  onDialogChange,
  disabled,
  onChange,
  onTransformsChange,
  onTypeMappingsChange,
  confirm,
}: Props) {
  const [selectedKeys, setSelectedKeys] = useState<string[]>([]);
  const [status, setStatus] = useState("ALL");
  const [onlyNew, setOnlyNew] = useState(false);
  const baselineKeys = new Set(baseline.map((mapping) => tableKey(mapping.source)));
  const selected = selectedKeys.filter((key) =>
    mappings.some((mapping) => tableKey(mapping.source) === key),
  );
  const state = (mapping: DataSyncTableMapping) =>
    !mapping.target.table
      ? "PENDING"
      : mappings.some(
            (other) => other !== mapping && tableKey(other.target) === tableKey(mapping.target),
          )
        ? "CONFLICT"
        : "READY";
  const stateLabel = { PENDING: "待配置", CONFLICT: "目标冲突", READY: "已配置" };
  const rows = mappings
    .map((mapping, index) => ({
      ...mapping,
      index,
      key: tableKey(mapping.source),
      status: state(mapping),
    }))
    .filter(
      (row) =>
        (status === "ALL" || row.status === status) && (!onlyNew || !baselineKeys.has(row.key)),
    );
  const edit = (mapping: DataSyncTableMapping, tab: MappingTab) => {
    if (!disabled) onDialogChange({ type: "table", key: tableKey(mapping.source), tab });
  };
  const action = (mapping: DataSyncTableMapping, tab: MappingTab, text: string) => (
    <Button
      size="small"
      variant="ghost"
      className="max-w-full truncate px-1 font-normal text-[var(--yak-color-primary)]"
      disabled={disabled}
      onClick={() => edit(mapping, tab)}
    >
      {text}
    </Button>
  );
  const columns: TableColumns<(typeof rows)[number]> = [
    { key: "index", title: "序号", width: 55, render: (_, row) => row.index + 1 },
    {
      key: "status",
      title: "状态",
      width: 95,
      render: (_, row) => (
        <span
          className={
            row.status === "CONFLICT" ? "text-[var(--yak-components-danger-text)]" : undefined
          }
        >
          {stateLabel[row.status as keyof typeof stateLabel]}
        </span>
      ),
    },
    {
      key: "sourceDatabase",
      title: "来源库",
      width: 160,
      render: (_, row) => row.source.database || row.source.schema || "—",
    },
    { key: "sourceTable", title: "来源表", width: 180, render: (_, row) => row.source.table },
    {
      key: "databaseMapping",
      title: "目标库名映射自定义",
      width: 170,
      render: (_, row) => action(row, "mapping", "编辑"),
    },
    {
      key: "tableMapping",
      title: "目标表名映射自定义",
      width: 170,
      render: (_, row) =>
        action(row, "mapping", row.target.table === row.source.table ? "同名映射" : "自定义"),
    },
    {
      key: "targetDatabase",
      title: "目标库名",
      width: 180,
      render: (_, row) => [row.target.database, row.target.schema].filter(Boolean).join(".") || "—",
    },
    {
      key: "targetTable",
      title: "目标表名",
      width: 200,
      render: (_, row) => action(row, "mapping", row.target.table || "请选择目标表"),
    },
    {
      key: "assignments",
      title: "目标表字段赋值",
      width: 160,
      render: (_, row) => {
        const selector = transformTableSelector(row.source);
        const count = transforms.filter((rule) => rule["source-table"] === selector).length;
        return (
          <Button
            size="small"
            variant="ghost"
            className="px-1 text-[var(--yak-color-primary)]"
            disabled={disabled}
            onClick={() => onDialogChange({ type: "expressions", selector })}
          >
            {count ? `${count} 条规则` : "配置"}
          </Button>
        );
      },
    },
    {
      key: "primaryKeys",
      title: "目标表同步主键",
      width: 180,
      render: (_, row) =>
        action(
          row,
          "sync",
          row.target.primaryKeys?.join(", ") ||
            (row.writeMode === "RETRACT" ? "逐行更新" : "来源主键"),
        ),
    },
    {
      key: "chunk",
      title: "源端切分列",
      width: 160,
      render: (_, row) =>
        initialLoad ? action(row, "sync", row.source.snapshot?.chunkKeyColumn || "自动") : "—",
    },
    {
      key: "dml",
      title: "DML 规则配置",
      width: 210,
      render: (_, row) =>
        capabilities?.dmlEvents
          ? action(row, "sync", row.source.dml?.events.join(" / ") || "全部事件")
          : "—",
    },
    {
      key: "skip",
      title: "是否跳过全量同步",
      width: 155,
      render: (_, row) =>
        initialLoad && capabilities?.snapshotSkip
          ? action(row, "sync", row.source.snapshot?.skip ? "是" : "否")
          : "—",
    },
    {
      key: "advanced",
      title: "自定义高级参数",
      width: 150,
      render: (_, row) =>
        action(
          row,
          "mapping",
          row.writeMode === "UPSERT"
            ? "按主键更新"
            : row.writeMode === "RETRACT"
              ? "逐行更新"
              : "追加写入",
        ),
    },
    {
      key: "where",
      title: "全量条件",
      width: 190,
      render: (_, row) =>
        initialLoad && capabilities?.snapshotFilter
          ? action(row, "sync", row.source.snapshot?.where || "配置")
          : "—",
    },
    {
      key: "engine",
      title: "引擎参数配置",
      width: 150,
      render: (_, row) =>
        capabilities?.sourceEngineKeys.length || capabilities?.sinkEngineKeys.length
          ? action(row, "engine", row.batchSize ? `批次 ${row.batchSize}` : "默认")
          : "—",
    },
    {
      key: "actions",
      title: "操作",
      width: 90,
      fixed: "right",
      render: (_, row) => (
        <Button
          size="small"
          variant="ghost"
          disabled={disabled}
          onClick={() => edit(row, "mapping")}
        >
          配置
        </Button>
      ),
    },
  ];
  const current =
    dialog?.type === "table"
      ? mappings.find((mapping) => tableKey(mapping.source) === dialog.key)
      : undefined;
  const remove = () =>
    confirm({
      title: "批量移除库表",
      message: `将移除 ${selected.length} 张表及其目标映射和表级配置。`,
      apply: () => {
        const removeKeys = new Set(selected);
        onChange(mappings.filter((mapping) => !removeKeys.has(tableKey(mapping.source))));
        setSelectedKeys([]);
      },
    });
  return (
    <ContentSection id="target" title="目标表映射">
      <div className="flex flex-wrap items-center gap-3">
        <div className="w-44">
          <DataSyncSearchableSelect
            ariaLabel="映射状态"
            value={status}
            placeholder="状态"
            options={[
              { value: "ALL", label: "全部状态" },
              { value: "PENDING", label: "待配置" },
              { value: "READY", label: "已配置" },
              { value: "CONFLICT", label: "目标冲突" },
            ]}
            onValueChange={setStatus}
          />
        </div>
        <label className="flex items-center gap-2 text-xs">
          <Checkbox checked={onlyNew} onCheckedChange={setOnlyNew} />
          仅展示本次编辑增加的表
        </label>
        <div className="ml-auto flex gap-2">
          {transforms.some(
            (rule) =>
              !mappings.some(
                (mapping) => transformTableSelector(mapping.source) === rule["source-table"],
              ),
          ) ? (
            <Button
              size="small"
              disabled={disabled}
              onClick={() => onDialogChange({ type: "expressions" })}
            >
              字段表达式配置
            </Button>
          ) : null}
          {capabilities?.typeMapping ? (
            <Button
              size="small"
              disabled={disabled}
              onClick={() => onDialogChange({ type: "types" })}
            >
              编辑字段类型映射
            </Button>
          ) : null}
        </div>
      </div>
      <Table
        columns={columns}
        dataSource={rows}
        rowKey="key"
        size="small"
        rowHeight={40}
        scroll={{ x: 2800, y: 420 }}
        pagination={false}
        emptyText="请选择来源库表"
        emptyClassName="!min-h-24 !py-2"
        rowSelection={{
          selectedRowKeys: selected,
          onChange: (keys) => setSelectedKeys(keys.map(String)),
          getCheckboxProps: (row) => ({
            "aria-label": `选择映射 ${tablePathLabel(row.source)}`,
            disabled,
          }),
        }}
      />
      <div className="flex flex-wrap items-center gap-2">
        <Button size="small" disabled={disabled} onClick={targetCatalog.refresh}>
          刷新映射状态
        </Button>
        <Button
          size="small"
          disabled={disabled || !selected.length || regex}
          title={regex ? "正则模式通过规则调整匹配范围" : undefined}
          onClick={remove}
        >
          批量移除
        </Button>
        <Button
          size="small"
          disabled={disabled || !selected.length || !capabilities}
          onClick={() => onDialogChange({ type: "batch" })}
        >
          批量修改
        </Button>
        <span className="ml-2 text-xs text-[var(--yak-components-muted-text)]">
          已选 {selected.length} / {mappings.length}
        </span>
      </div>
      {targetCatalog.error ? <Alert>{targetCatalog.error}</Alert> : null}
      {dialog?.type === "table" && current && capabilities ? (
        <MappingSettingsDialog
          key={dialog.key}
          value={current}
          initialTab={dialog.tab}
          capabilities={capabilities}
          prepareSchema={prepareSchema}
          initialLoad={initialLoad}
          targetTables={targetCatalog.tables}
          writeModes={writeModes}
          schemaChanges={schemaChanges}
          onClose={() => onDialogChange(undefined)}
          onApply={(next) => {
            onChange(
              mappings.map((mapping) => (tableKey(mapping.source) === dialog.key ? next : mapping)),
            );
            onDialogChange(undefined);
          }}
        />
      ) : null}
      {dialog?.type === "batch" && capabilities ? (
        <BatchMappingDialog
          mappings={mappings}
          selectedKeys={selected}
          capabilities={capabilities}
          writeModes={writeModes}
          initialLoad={initialLoad}
          schemaChanges={schemaChanges}
          onClose={() => onDialogChange(undefined)}
          onApply={(next) => {
            onChange(next);
            onDialogChange(undefined);
          }}
        />
      ) : null}
      {dialog?.type === "expressions" ? (
        <TransformRulesDialog
          value={
            dialog.selector
              ? transforms.filter((rule) => rule["source-table"] === dialog.selector)
              : transforms
          }
          selector={dialog.selector}
          onClose={() => onDialogChange(undefined)}
          onApply={(next) => {
            onTransformsChange(
              dialog.selector ? replaceTableTransforms(transforms, dialog.selector, next) : next,
            );
            onDialogChange(undefined);
          }}
        />
      ) : null}
      {dialog?.type === "types" && capabilities ? (
        <TypeMappingDialog
          value={typeMappings}
          rules={capabilities.typeMappingRules}
          onClose={() => onDialogChange(undefined)}
          onApply={(rules) => {
            onTypeMappingsChange(rules);
            onDialogChange(undefined);
          }}
        />
      ) : null}
    </ContentSection>
  );
}

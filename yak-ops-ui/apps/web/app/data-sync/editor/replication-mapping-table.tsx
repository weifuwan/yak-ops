import { useState } from "react";
import { Alert, Button, Checkbox, Table, type TableColumns } from "@yak-ops/yak-ui";
import { validateBatchSize, validateSourceOptions, validateTableDml, validateTableSnapshot } from "@/service/data-sync/table-configuration";
import type {
  DataSyncCapabilities,
  DataSyncTableMapping,
  DataSyncTransform,
  DataSyncTypeMapping,
  DataSyncWriteMode,
} from "@/service/data-sync";
import { DataSyncSearchableSelect } from "../components/searchable-select";
import { ContentSection } from "./content-layout";
import { ReplicationEditableCell } from "./replication-editable-cell";
import { catalogPath, tableKey, tablePathLabel } from "./table-catalog";
import { BatchMappingDialog } from "./batch-mapping-dialog";
import { TypeMappingDialog } from "./type-mapping-dialog";
import type { CatalogOptions } from "./types";
import type { EditorConfirmation } from "./use-definition-restoration";

export type MappingDialog = { type: "batch" } | { type: "types" };

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
  onTypeMappingsChange,
  confirm,
}: Props) {
  const [selectedKeys, setSelectedKeys] = useState<string[]>([]);
  const [status, setStatus] = useState("ALL");
  const [onlyNew, setOnlyNew] = useState(false);
  const [editError, setEditError] = useState("");
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
  const updateRow = (row: DataSyncTableMapping, next: DataSyncTableMapping): string | undefined => {
    try {
      if (next.target.table.length > 128) throw new Error("目标表名不能超过 128 个字符");
      if (
        !prepareSchema &&
        next.target.table &&
        !targetCatalog.tables.some((table) => tableKey(catalogPath(table)) === tableKey(next.target))
      )
        throw new Error("请选择已有目标表");
      if (!writeModes.includes(next.writeMode)) throw new Error("请选择支持的写入方式");
      if (
        next.target.primaryKeys &&
        (next.target.primaryKeys.some((key) => !key) ||
          new Set(next.target.primaryKeys).size !== next.target.primaryKeys.length)
      )
        throw new Error("目标主键不能为空或重复");
      if (schemaChanges && next.target.primaryKeys?.length)
        throw new Error("自定义目标主键需先关闭同步结构变更");
      validateTableSnapshot(next.source.snapshot);
      validateTableDml(next.source.dml);
      validateSourceOptions(next.source.options);
      validateBatchSize(next.batchSize);
      if (next.writeMode === "RETRACT" && next.batchSize !== undefined)
        throw new Error("逐行更新不支持设置写入批次大小");
      onChange(
        mappings.map((mapping) =>
          tableKey(mapping.source) === tableKey(row.source) ? next : mapping,
        ),
      );
      setEditError("");
      return undefined;
    } catch (failure) {
      const message = failure instanceof Error ? failure.message : "表级配置无效";
      setEditError(message);
      return message;
    }
  };
  const snapshot = (row: DataSyncTableMapping, changes: { chunkKeyColumn?: string; skip?: boolean }) => {
    const result = { ...row.source.snapshot, ...changes };
    return updateRow(row, {
      ...row,
      source: {
        ...row.source,
        snapshot: Object.values(result).some((value) => value !== undefined) ? result : undefined,
      },
    });
  };
  const editable = (
    row: DataSyncTableMapping,
    label: string,
    value: string,
    placeholder: string,
    apply: (value: string) => string | undefined,
    blocked = false,
  ) => (
    <ReplicationEditableCell
      label={label + " " + tablePathLabel(row.source)}
      value={value}
      placeholder={placeholder}
      disabled={disabled || blocked}
      onCommit={apply}
    />
  );
  const hasSchema = mappings.some((mapping) => mapping.target.schema !== undefined) ||
    targetCatalog.tables.some((table) => Boolean(table.schema));
  const showWriteMode = writeModes.length > 1 ||
    mappings.some((mapping) => mapping.writeMode !== writeModes[0]);
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
      key: "targetDatabase",
      title: "目标库名",
      width: 180,
      render: (_, row) =>
        prepareSchema
          ? editable(row, "目标库名", row.target.database || "", "目标库名", (database) =>
              updateRow(row, {
                ...row,
                target: { ...row.target, database: database.trim() || undefined },
              }),
            )
          : row.target.database || "—",
    },
    ...(hasSchema
      ? [{
          key: "targetSchema",
          title: "目标 Schema",
          width: 145,
          render: (_: unknown, row: (typeof rows)[number]) =>
            prepareSchema
              ? editable(row, "目标 Schema", row.target.schema || "", "默认 Schema", (schema) =>
                  updateRow(row, {
                    ...row,
                    target: { ...row.target, schema: schema.trim() || undefined },
                  }),
                )
              : row.target.schema || "—",
        }]
      : []),
    {
      key: "targetTable",
      title: "目标表名",
      width: 220,
      render: (_, row) =>
        prepareSchema
          ? editable(row, "目标表名", row.target.table, "请选择目标表", (table) => {
              if (!table.trim()) {
                setEditError("请选择或填写目标表");
                return "请选择或填写目标表";
              }
              return updateRow(row, { ...row, target: { ...row.target, table: table.trim() } });
            })
          : (
            <DataSyncSearchableSelect
              ariaLabel={"目标表 " + tablePathLabel(row.source)}
              value={row.target.table ? tableKey(row.target) : null}
              disabled={disabled}
              placeholder="请选择已有目标表"
              options={targetCatalog.tables.map((table) => ({
                value: tableKey(catalogPath(table)),
                label: tablePathLabel(catalogPath(table)),
              }))}
              onValueChange={(key) => {
                const selected = targetCatalog.tables.find((table) => tableKey(catalogPath(table)) === key);
                if (selected)
                  updateRow(row, {
                    ...row,
                    target: { ...catalogPath(selected), primaryKeys: row.target.primaryKeys },
                  });
              }}
            />
          ),
    },
    {
      key: "primaryKeys",
      title: "目标表同步主键",
      width: 180,
      render: (_, row) =>
        editable(
          row,
          "目标表同步主键",
          (row.target.primaryKeys ?? []).join(", "),
          row.writeMode === "RETRACT" ? "逐行更新" : "来源主键",
          (keys) =>
            updateRow(row, {
              ...row,
              target: {
                ...row.target,
                primaryKeys: keys.trim()
                  ? keys.split(",").map((key) => key.trim())
                  : undefined,
              },
            }),
          row.writeMode === "RETRACT" || schemaChanges,
        ),
    },
    {
      key: "chunk",
      title: "源端切分列",
      width: 160,
      render: (_, row) =>
        initialLoad
          ? editable(row, "源端切分列", row.source.snapshot?.chunkKeyColumn || "", "自动", (value) =>
              snapshot(row, { chunkKeyColumn: value.trim() || undefined }),
            )
          : "—",
    },
    {
      key: "skip",
      title: "是否跳过全量同步",
      width: 155,
      render: (_, row) =>
        initialLoad && capabilities?.snapshotSkip
          ? (
            <Checkbox
              aria-label={"跳过全量同步 " + tablePathLabel(row.source)}
              checked={Boolean(row.source.snapshot?.skip)}
              disabled={disabled || Boolean(row.source.snapshot?.where)}
              title={row.source.snapshot?.where ? "已有全量条件，不能同时跳过全量" : undefined}
              onCheckedChange={(checked) => snapshot(row, { skip: checked || undefined })}
            />
          )
          : "—",
    },
    ...(showWriteMode
      ? [{
          key: "writeMode",
          title: "写入方式",
          width: 150,
          render: (_: unknown, row: (typeof rows)[number]) => (
            <DataSyncSearchableSelect
              ariaLabel={"写入方式 " + tablePathLabel(row.source)}
              value={row.writeMode}
              disabled={disabled || writeModes.length < 2}
              placeholder="写入方式"
              options={writeModes.map((mode) => ({
                value: mode,
                label:
                  mode === "UPSERT" ? "按主键更新" : mode === "RETRACT" ? "逐行更新" : "追加写入",
              }))}
              onValueChange={(mode) =>
                updateRow(row, {
                  ...row,
                  writeMode: mode as DataSyncWriteMode,
                  batchSize: mode === "RETRACT" ? undefined : row.batchSize,
                  target: {
                    ...row.target,
                    ...(mode === "RETRACT" ? { primaryKeys: undefined } : {}),
                  },
                })
              }
            />
          ),
        }]
      : []),
    {
      key: "actions",
      title: "操作",
      width: 90,
      fixed: "right",
      render: (_, row) => (
        <Button
          size="small"
          variant="ghost"
          disabled={disabled || regex}
          title={regex ? "正则选表请通过规则调整匹配范围" : undefined}
          onClick={() => remove([tableKey(row.source)])}
        >
          移除
        </Button>
      ),
    },
  ];
  const remove = (keys: string[]) =>
    confirm({
      title: keys.length > 1 ? "批量移除库表" : "移除库表",
      message: "将从当前任务移除 " + keys.length + " 张表及其目标映射和表级配置，不会删除数据库中的物理表。",
      apply: () => {
        const removeKeys = new Set(keys);
        onChange(mappings.filter((mapping) => !removeKeys.has(tableKey(mapping.source))));
        setSelectedKeys((current) => current.filter((key) => !removeKeys.has(key)));
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
        scroll={{ x: 1740 + (hasSchema ? 145 : 0) + (showWriteMode ? 150 : 0), y: 420 }}
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
          onClick={() => remove(selected)}
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
      {mappings.some((mapping) => mapping.source.dml && mapping.source.dml.events.length !== 3) ? (
        <p className="text-xs text-[var(--yak-components-muted-text)]">
          部分表保留已保存的 DML 事件过滤，仍会影响同步结果；当前页面不提供编辑。
        </p>
      ) : null}
      {transforms.length > 0 ? (
        <p className="text-xs text-[var(--yak-components-muted-text)]">
          已保存的 {transforms.length} 条字段表达式仍会执行，当前页面不提供编辑。
        </p>
      ) : null}
      {editError ? <Alert>{editError}</Alert> : null}
      {targetCatalog.error ? <Alert>{targetCatalog.error}</Alert> : null}
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

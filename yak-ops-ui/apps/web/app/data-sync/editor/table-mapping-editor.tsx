import { Alert, Button, Checkbox, Input, Table, type TableColumns } from "@yak-ops/yak-ui";
import { useMemo, useState } from "react";
import type { DataSourceCatalogTable } from "@/service/datasource";
import type {
  DataSyncTableMapping,
  DataSyncTablePath,
  DataSyncWriteMode,
} from "@/service/data-sync";
import { DataSyncSearchableSelect } from "../components/searchable-select";

export const tableKey = (table: DataSyncTablePath) =>
  JSON.stringify([table.database || "", table.schema || "", table.table]);
const pathText = (table: DataSyncTablePath) =>
  [table.database, table.schema, table.table].filter(Boolean).join(".");
const catalogPath = (table: DataSourceCatalogTable): DataSyncTablePath => ({
  database: table.database,
  schema: table.schema,
  table: table.name,
});

interface Props {
  prepareSchema?: boolean;
  targetNamespace?: Omit<DataSyncTablePath, "table">;
  writeMode?: DataSyncWriteMode;
  sourceTables: DataSourceCatalogTable[];
  targetTables: DataSourceCatalogTable[];
  mappings: DataSyncTableMapping[];
  loading: boolean;
  error: string;
  refresh: () => void;
  onChange: (mappings: DataSyncTableMapping[]) => void;
}

export function ExplicitTableSelection({
  prepareSchema = false,
  targetNamespace,
  writeMode,
  sourceTables,
  targetTables,
  mappings,
  loading,
  error,
  refresh,
  onChange,
}: Props) {
  const [keyword, setKeyword] = useState("");
  const selected = new Set(mappings.map((mapping) => tableKey(mapping.source)));
  const singleOnly = mappings.some((mapping) => mapping.writeMode === "RETRACT");
  const available = useMemo(
    () =>
      sourceTables.map((table) => ({ ...catalogPath(table), id: tableKey(catalogPath(table)) })),
    [sourceTables],
  );
  const visible = available.filter((table) =>
    pathText(table).toLowerCase().includes(keyword.trim().toLowerCase()),
  );
  const add = (tables: DataSyncTablePath[]) => {
    if (!writeMode) return;
    const missing = tables.filter((table) => !selected.has(tableKey(table)));
    if (mappings.length + missing.length > 100 || (singleOnly && missing.length > 0)) return;
    onChange([
      ...mappings,
      ...missing.map((source) => {
        const matches = targetTables.filter((target) => target.name === source.table);
        let target: DataSyncTablePath = prepareSchema
          ? { ...targetNamespace, table: source.table }
          : { table: "" };
        if (matches.length === 1) target = catalogPath(matches[0]);
        return {
          source: { database: source.database, schema: source.schema, table: source.table },
          target,
          writeMode,
        };
      }),
    ]);
  };
  const columns: TableColumns<(typeof available)[number]> = [
    {
      key: "selected",
      title: "",
      width: 44,
      render: (_value, row) => (
        <Checkbox
          aria-label={`选择 ${pathText(row)}`}
          checked={selected.has(row.id)}
          disabled={
            loading ||
            !writeMode ||
            (!selected.has(row.id) && (mappings.length >= 100 || singleOnly))
          }
          onCheckedChange={(checked) =>
            checked
              ? add([row])
              : onChange(mappings.filter((mapping) => tableKey(mapping.source) !== row.id))
          }
        />
      ),
    },
    {
      key: "name",
      title: "来源表",
      render: (_value, row) => <span className="break-all">{pathText(row)}</span>,
    },
  ];
  return (
    <div className="space-y-3 rounded-lg border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-4">
      <div className="flex flex-wrap items-center gap-2">
        <Input
          size="small"
          variant="outlined"
          placeholder="搜索来源表"
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
        />
        <Button
          size="small"
          onClick={() => add(visible)}
          disabled={
            loading ||
            singleOnly ||
            !writeMode ||
            mappings.length + visible.filter((table) => !selected.has(table.id)).length > 100
          }
        >
          选择当前结果
        </Button>
        <Button size="small" onClick={refresh}>
          刷新
        </Button>
        <span className="text-xs text-[var(--yak-components-collapse-section-icon)]">
          已选 {mappings.length} / 100
        </span>
      </div>
      {singleOnly ? <Alert>逐行更新仅支持单表。请先修改写入方式，再添加其他表。</Alert> : null}
      {error ? <Alert>{error}</Alert> : null}
      <div className="max-h-72 overflow-auto">
        <Table
          columns={columns}
          dataSource={visible}
          rowKey="id"
          size="small"
          loading={loading}
          pagination={false}
          emptyText="暂无来源表"
        />
      </div>
    </div>
  );
}

interface MappingProps {
  prepareSchema?: boolean;
  writeModes: DataSyncWriteMode[];
  mappings: DataSyncTableMapping[];
  targetTables: DataSourceCatalogTable[];
  loading: boolean;
  error: string;
  refresh: () => void;
  onChange: (mappings: DataSyncTableMapping[]) => void;
}

export function TableMappingEditor({
  prepareSchema = false,
  writeModes,
  mappings,
  targetTables,
  loading,
  error,
  refresh,
  onChange,
}: MappingProps) {
  const writeOptions = writeModes.map((value) => ({
    value,
    label: value === "UPSERT" ? "按主键更新" : value === "RETRACT" ? "逐行更新" : "追加写入",
  }));
  const targetOptions = targetTables.map((table) => ({
    value: tableKey(catalogPath(table)),
    label: pathText(catalogPath(table)),
  }));
  const patch = (index: number, value: Partial<DataSyncTableMapping>) =>
    onChange(mappings.map((mapping, i) => (i === index ? { ...mapping, ...value } : mapping)));
  const rows = mappings.map((mapping, index) => ({
    ...mapping,
    id: tableKey(mapping.source),
    index,
  }));
  const columns: TableColumns<(typeof rows)[number]> = [
    {
      key: "source",
      title: "来源表",
      minWidth: 180,
      render: (_value, row) => <span className="break-all">{pathText(row.source)}</span>,
    },
    {
      key: "target",
      title: "去向表",
      minWidth: 240,
      render: (_value, row) => {
        const duplicate =
          row.target.table &&
          mappings.some(
            (mapping, i) => i !== row.index && tableKey(mapping.target) === tableKey(row.target),
          );
        return (
          <div className="space-y-1">
            {prepareSchema ? (
              <Input
                size="small"
                variant="outlined"
                aria-label={`去向表 ${pathText(row.source)}`}
                value={row.target.table}
                maxLength={128}
                disabled={loading}
                placeholder="去向表名称"
                onChange={(event) =>
                  patch(row.index, { target: { ...row.target, table: event.target.value } })
                }
              />
            ) : (
              <DataSyncSearchableSelect
                ariaLabel={`去向表 ${pathText(row.source)}`}
                value={row.target.table ? tableKey(row.target) : null}
                options={targetOptions}
                disabled={loading}
                placeholder="请选择已有目标表"
                onValueChange={(value) => {
                  const table = targetTables.find(
                    (entry) => tableKey(catalogPath(entry)) === value,
                  );
                  if (table) patch(row.index, { target: catalogPath(table) });
                }}
              />
            )}
            {duplicate ? (
              <span role="alert" className="text-xs text-[var(--yak-components-danger-text)]">
                去向表不能重复
              </span>
            ) : null}
          </div>
        );
      },
    },
    {
      key: "writeMode",
      title: "写入方式",
      width: 160,
      render: (_value, row) => (
        <DataSyncSearchableSelect
          ariaLabel={`写入方式 ${pathText(row.source)}`}
          placeholder="请选择写入方式"
          value={row.writeMode}
          disabled={writeModes.length < 2}
          options={writeOptions}
          onValueChange={(value) =>
            patch(row.index, {
              writeMode: value as DataSyncWriteMode,
              ...(value === "RETRACT" ? { target: { ...row.target, primaryKeys: undefined } } : {}),
            })
          }
        />
      ),
    },
    {
      key: "actions",
      title: "",
      width: 64,
      render: (_value, row) => (
        <Button
          size="small"
          variant="ghost"
          onClick={() => onChange(mappings.filter((_mapping, i) => i !== row.index))}
        >
          移除
        </Button>
      ),
    },
  ];
  return (
    <div className="space-y-3 rounded-lg border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-4">
      <div className="flex items-center gap-2">
        <div className="w-44">
          <DataSyncSearchableSelect
            value={null}
            ariaLabel="批量设置写入方式"
            placeholder="批量设置写入方式"
            disabled={writeModes.length < 2}
            options={writeOptions}
            onValueChange={(value) =>
              onChange(
                mappings.map((mapping) => ({
                  ...mapping,
                  writeMode: value as DataSyncWriteMode,
                  ...(value === "RETRACT"
                    ? { target: { ...mapping.target, primaryKeys: undefined } }
                    : {}),
                })),
              )
            }
          />
        </div>
        <Button size="small" onClick={refresh}>
          刷新目标表
        </Button>
      </div>
      {error ? <Alert>{error}</Alert> : null}
      <Table
        columns={columns}
        dataSource={rows}
        rowKey="id"
        size="small"
        scroll={{ x: 750 }}
        pagination={false}
        emptyText="请选择来源表"
      />
    </div>
  );
}

export function validTableMappings(
  mappings: DataSyncTableMapping[],
  targetTables: DataSourceCatalogTable[],
  writeModes: DataSyncWriteMode[],
  prepareSchema = false,
) {
  if (!mappings.length || mappings.length > 100) return false;
  const sources = new Set(mappings.map((mapping) => tableKey(mapping.source)));
  const targets = new Set(mappings.map((mapping) => tableKey(mapping.target)));
  const available = new Set(targetTables.map((table) => tableKey(catalogPath(table))));
  return (
    sources.size === mappings.length &&
    targets.size === mappings.length &&
    mappings.every(
      (mapping) =>
        writeModes.includes(mapping.writeMode) &&
        Boolean(mapping.source.table.trim()) &&
        Boolean(mapping.target.table.trim()) &&
        mapping.target.table.length <= 128 &&
        (prepareSchema || available.has(tableKey(mapping.target))),
    )
  );
}

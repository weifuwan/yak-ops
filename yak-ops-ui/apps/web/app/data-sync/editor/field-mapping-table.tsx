import { Button, Input, Table, type TableColumns } from "@yak-ops/yak-ui";
import { ArrowDown, ArrowUp } from "lucide-react";

import type { DataSourceCatalogColumn } from "@/service/datasource";
import type { DirectFieldMapping } from "@/service/data-sync/transforms";
import { DataSyncSearchableSelect } from "../components/searchable-select";

interface FieldRow extends DirectFieldMapping {
  column?: DataSourceCatalogColumn;
  selected: boolean;
}

interface Props {
  fields: DirectFieldMapping[];
  sourceColumns: DataSourceCatalogColumn[];
  targetColumns?: DataSourceCatalogColumn[];
  disabled: boolean;
  onChange: (fields: DirectFieldMapping[]) => void;
}

const columnType = (column?: DataSourceCatalogColumn) => column?.typeName || "—";

/** A selected source column produces exactly one named output column. */
export function FieldMappingTable({
  fields,
  sourceColumns,
  targetColumns,
  disabled,
  onChange,
}: Props) {
  const selected = new Set(fields.map((field) => field.source));
  const rows: FieldRow[] = [
    ...fields.map((field) => ({
      ...field,
      column: sourceColumns.find((column) => column.name === field.source),
      selected: true,
    })),
    ...sourceColumns
      .filter((column) => !selected.has(column.name))
      .map((column) => ({
        source: column.name,
        target: column.name,
        column,
        selected: false,
      })),
  ];
  const rename = (source: string, target: string) =>
    onChange(fields.map((field) => (field.source === source ? { ...field, target } : field)));
  const move = (index: number, offset: number) => {
    const next = [...fields];
    [next[index], next[index + offset]] = [next[index + offset], next[index]];
    onChange(next);
  };
  const columns: TableColumns<FieldRow> = [
    {
      key: "source",
      title: "来源字段",
      width: "24%",
      render: (_, row) => (
        <div className="min-w-0">
          <span className="break-all">{row.source}</span>
          {row.column?.primaryKey ? (
            <span className="ml-2 text-xs text-[var(--yak-components-field-description)]">
              主键
            </span>
          ) : null}
          {!row.column ? (
            <span className="ml-2 text-xs text-[var(--yak-components-field-error)]">
              字段不存在
            </span>
          ) : null}
        </div>
      ),
    },
    {
      key: "sourceType",
      title: "来源类型",
      width: "17%",
      render: (_, row) => columnType(row.column),
    },
    {
      key: "target",
      title: "输出字段",
      width: "29%",
      render: (_, row) => {
        if (!targetColumns)
          return (
            <Input
              size="small"
              variant="outlined"
              aria-label={`${row.source} 的输出字段`}
              value={row.target}
              disabled={disabled || !row.selected}
              onChange={(event) => rename(row.source, event.target.value)}
            />
          );
        const options = targetColumns.map((column) => ({ value: column.name, label: column.name }));
        if (row.target && !options.some((option) => option.value === row.target))
          options.unshift({ value: row.target, label: `${row.target}（未匹配）` });
        return (
          <DataSyncSearchableSelect
            ariaLabel={`${row.source} 的输出字段`}
            placeholder="选择去向字段"
            value={row.target || null}
            options={options}
            disabled={disabled || !row.selected}
            onValueChange={(target) => rename(row.source, target)}
          />
        );
      },
    },
    {
      key: "targetType",
      title: "去向类型",
      width: "17%",
      render: (_, row) => columnType(targetColumns?.find((column) => column.name === row.target)),
    },
    {
      key: "order",
      title: "顺序",
      width: 84,
      render: (_, row, index) => (
        <div className="flex items-center gap-1">
          <Button
            size="small"
            variant="ghost"
            className="w-7 px-0"
            aria-label={`上移 ${row.source}`}
            disabled={disabled || !row.selected || index === 0}
            onClick={() => move(index, -1)}
          >
            <ArrowUp size={14} />
          </Button>
          <Button
            size="small"
            variant="ghost"
            className="w-7 px-0"
            aria-label={`下移 ${row.source}`}
            disabled={disabled || !row.selected || index >= fields.length - 1}
            onClick={() => move(index, 1)}
          >
            <ArrowDown size={14} />
          </Button>
        </div>
      ),
    },
  ];
  return (
    <Table
      columns={columns}
      dataSource={rows}
      rowKey="source"
      size="small"
      pagination={false}
      scroll={{ x: 720 }}
      emptyText="暂无来源字段"
      rowSelection={{
        selectedRowKeys: fields.map((field) => field.source),
        hideSelectAll: true,
        getCheckboxProps: (row) => ({
          disabled: disabled || (row.selected && fields.length === 1),
          "aria-label": `同步字段 ${row.source}`,
        }),
        onChange: (keys) => {
          const included = new Set(keys);
          const next = rows
            .filter((row) => included.has(row.source))
            .map(({ source, target }) => ({ source, target }));
          if (next.length) onChange(next);
        },
      }}
    />
  );
}

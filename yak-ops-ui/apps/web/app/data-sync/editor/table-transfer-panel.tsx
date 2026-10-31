import { Button, Input, Table, type TableColumns } from "@yak-ops/yak-ui";
import { RefreshCw } from "lucide-react";
import type { ReactNode } from "react";
import type { DataSyncTablePath } from "@/service/data-sync";
import { tableKey, tablePathLabel } from "./table-catalog";

export interface TransferFilters {
  database: string;
  table: string;
}

export function TableTransferPanel({
  title,
  tables,
  checked,
  onCheckedChange,
  filters,
  onFiltersChange,
  onSearch,
  loading,
  actions,
  footer,
  onRefresh,
  disabled,
}: {
  title: string;
  tables: DataSyncTablePath[];
  checked: string[];
  onCheckedChange: (keys: string[]) => void;
  filters: TransferFilters;
  onFiltersChange: (filters: TransferFilters) => void;
  onSearch?: () => void;
  loading?: boolean;
  actions?: ReactNode;
  footer?: ReactNode;
  onRefresh?: () => void;
  disabled?: (table: DataSyncTablePath) => boolean;
}) {
  const columns: TableColumns<DataSyncTablePath> = [
    {
      key: "table",
      title: "库表名称",
      render: (_, row) => (
        <span title={tablePathLabel(row)} className="block truncate">
          {tablePathLabel(row)}
        </span>
      ),
    },
  ];
  return (
    <section
      aria-label={title}
      className="min-w-0 rounded-sm border border-[var(--yak-components-panel-border)]"
    >
      <div className="flex min-h-9 items-center justify-between gap-2 px-3">
        <h3 className="text-xs font-medium">{title}</h3>
        <div className="flex items-center gap-2">
          {onRefresh ? (
            <Button size="small" aria-label={`刷新${title}`} onClick={onRefresh}>
              <RefreshCw size={13} />
            </Button>
          ) : null}
          {actions}
        </div>
      </div>
      <div className="space-y-2 px-3 pb-3">
        <label className="grid grid-cols-[76px_minmax(0,1fr)] items-center gap-2 text-xs">
          <span>库过滤</span>
          <Input
            size="small"
            variant="outlined"
            aria-label={`${title}库过滤`}
            placeholder="库名或正则"
            maxLength={256}
            value={filters.database}
            onChange={(event) => onFiltersChange({ ...filters, database: event.target.value })}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                event.preventDefault();
                onSearch?.();
              }
            }}
          />
        </label>
        <label className="grid grid-cols-[76px_minmax(0,1fr)] items-center gap-2 text-xs">
          <span>表过滤</span>
          <Input
            size="small"
            variant="outlined"
            aria-label={`${title}表过滤`}
            placeholder="表名或正则"
            maxLength={256}
            value={filters.table}
            onChange={(event) => onFiltersChange({ ...filters, table: event.target.value })}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                event.preventDefault();
                onSearch?.();
              }
            }}
          />
        </label>
      </div>
      <Table
        columns={columns}
        dataSource={tables}
        rowKey={tableKey}
        size="small"
        pagination={false}
        loading={loading}
        scroll={{ y: 240 }}
        emptyText="没有库表"
        emptyClassName="!min-h-8 !py-1"
        rowSelection={{
          selectedRowKeys: checked,
          onChange: (keys) => onCheckedChange(keys.map(String)),
          getCheckboxProps: (row) => ({
            "aria-label": `选择 ${tablePathLabel(row)}`,
            disabled: disabled?.(row),
          }),
        }}
      />
      <div className="flex min-h-10 items-center justify-between gap-2 border-t border-[var(--yak-components-panel-border)] bg-[var(--yak-color-hover)] px-3 text-xs">
        <span>
          表 {checked.length} / {tables.length}
        </span>
        {footer}
      </div>
    </section>
  );
}

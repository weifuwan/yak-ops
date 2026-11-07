import { Button, Input, Table, type TableColumns } from "@yak-ops/yak-ui";
import { RefreshCw } from "lucide-react";
import type { ReactNode } from "react";
import type { DataSyncTablePath } from "./table-catalog";
import { tableKey, tablePathLabel } from "./table-catalog";

export function TableTransferPanel({
  title,
  tables,
  checked,
  onCheckedChange,
  filter,
  onFilterChange,
  onSearch,
  loading,
  pageNo,
  pageSize,
  total,
  onPageChange,
  actions,
  footer,
  onRefresh,
  disabled,
  hideFilters = false,
}: {
  title: string;
  tables: DataSyncTablePath[];
  checked: string[];
  onCheckedChange: (keys: string[]) => void;
  filter?: string;
  onFilterChange?: (value: string) => void;
  onSearch?: () => void;
  loading?: boolean;
  pageNo?: number;
  pageSize?: number;
  total?: number;
  onPageChange?: (pageNo: number, pageSize: number) => void;
  actions?: ReactNode;
  footer?: ReactNode;
  onRefresh?: () => void;
  disabled?: (table: DataSyncTablePath) => boolean;
  hideFilters?: boolean;
}) {
  const columns: TableColumns<DataSyncTablePath> = [
    {
      key: "table",
      title: "表名称",
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
      {!hideFilters ? (
        <div className="px-3 pb-3">
          <label className="grid grid-cols-[76px_minmax(0,1fr)] items-center gap-2 text-xs">
            <span>表过滤</span>
            <Input
              size="small"
              variant="outlined"
              aria-label={`${title}表过滤`}
              placeholder="表名"
              maxLength={256}
              value={filter || ""}
              onChange={(event) => onFilterChange?.(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  event.preventDefault();
                  onSearch?.();
                }
              }}
            />
          </label>
        </div>
      ) : null}
      <Table
        columns={columns}
        dataSource={tables}
        rowKey={tableKey}
        size="small"
        pagination={
          onPageChange && total
            ? {
                current: pageNo || 1,
                pageSize: pageSize || 100,
                total,
                pageSizeOptions: [50, 100, 200],
                pageSizeLabel: "每页显示：",
                showSizeChanger: true,
                disabled: loading,
                onChange: onPageChange,
              }
            : false
        }
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
          表 {checked.length} / {total ?? tables.length}
        </span>
        {footer}
      </div>
    </section>
  );
}

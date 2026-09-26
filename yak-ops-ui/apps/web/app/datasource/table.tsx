import {
  Badge,
  Button,
  Checkbox,
  Table,
  type BadgeProps,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { CircleCheck, CircleMinus, CircleX } from "lucide-react";
import type { ReactNode } from "react";

import { DATA_SOURCE_PAGE_SIZE_OPTIONS } from "./constants";
import DatabaseIcons from "./icons/DatabaseIcons";
import { useIntl } from "./i18n";
import type { DataSourceConnectionStatus, DataSourceRecord } from "./types";

interface DataSourceTableProps {
  records: DataSourceRecord[];
  loading: boolean;
  pageNo: number;
  pageSize: number;
  total: number;
  hasActiveFilters: boolean;
  editingId: string;
  selectedRowKeys: string[];
  batchDeleting: boolean;
  batchTesting: boolean;
  onPageChange: (page: number, pageSize: number) => void;
  onSelectionChange: (selectedRowKeys: string[]) => void;
  onEdit: (record: DataSourceRecord) => void;
  onDelete: (record: DataSourceRecord) => void;
  onBatchDelete: () => void;
  onBatchTestConnection: () => void;
}

interface StatusConfig {
  tone: NonNullable<BadgeProps["tone"]>;
  icon: ReactNode;
  messageId: string;
}

const DataSourceStatus = ({ status }: { status?: DataSourceConnectionStatus }) => {
  const intl = useIntl();
  const configMap: Record<string, StatusConfig> = {
    CONNECTED: {
      tone: "success",
      icon: <CircleCheck size={13} />,
      messageId: "pages.datasource.status.connected",
    },
    DISCONNECTED: {
      tone: "danger",
      icon: <CircleX size={13} />,
      messageId: "pages.datasource.status.disconnected",
    },
    UNKNOWN: {
      tone: "neutral",
      icon: <CircleMinus size={13} />,
      messageId: "pages.datasource.status.unknown",
    },
  };
  const config = configMap[String(status || "UNKNOWN").toUpperCase()] || configMap.UNKNOWN;

  return (
    <Badge tone={config.tone} className="gap-1.5 whitespace-nowrap">
      {config.icon}
      {intl.formatMessage({ id: config.messageId })}
    </Badge>
  );
};

const DataSourceTable = ({
  records,
  loading,
  pageNo,
  pageSize,
  total,
  hasActiveFilters,
  editingId,
  selectedRowKeys,
  batchDeleting,
  batchTesting,
  onPageChange,
  onSelectionChange,
  onEdit,
  onDelete,
  onBatchDelete,
  onBatchTestConnection,
}: DataSourceTableProps) => {
  const intl = useIntl();
  const batchBusy = batchDeleting || batchTesting;
  const currentPageIds = records.flatMap((record) => (record.id ? [String(record.id)] : []));
  const selectedKeySet = new Set(selectedRowKeys);
  const allCurrentPageSelected =
    currentPageIds.length > 0 && currentPageIds.every((id) => selectedKeySet.has(id));
  const someCurrentPageSelected = currentPageIds.some((id) => selectedKeySet.has(id));

  const toggleCurrentPageSelection = (checked: boolean) => {
    const nextSelectedKeys = new Set(selectedRowKeys);
    for (const id of currentPageIds) {
      if (checked) nextSelectedKeys.add(id);
      else nextSelectedKeys.delete(id);
    }
    onSelectionChange([...nextSelectedKeys]);
  };

  const columns: TableColumns<DataSourceRecord> = [
    {
      key: "sequence",
      title: intl.formatMessage({ id: "pages.datasource.table.sequence" }),
      width: 72,
      align: "center",
      render: (_value, _record, index) =>
        String((pageNo - 1) * pageSize + index + 1).padStart(2, "0"),
    },
    {
      key: "datasource",
      title: intl.formatMessage({ id: "pages.datasource.table.datasource" }),
      minWidth: 220,
      render: (_value, record) => (
        <div className="flex min-w-0 items-center gap-3">
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-[#eceef2] bg-white">
            <DatabaseIcons dbType={record.dbType} width="22" height="22" />
          </span>
          <div className="min-w-0">
            <div className="truncate text-[13px] font-medium text-[#252832]" title={record.name}>
              {record.name || "-"}
            </div>
            <div className="mt-0.5 truncate text-xs text-[#667085]">{record.dbType || "-"}</div>
          </div>
        </div>
      ),
    },
    {
      key: "connection",
      title: intl.formatMessage({ id: "pages.datasource.table.connection" }),
      minWidth: 360,
      render: (_value, record) => (
        <div className="min-w-0">
          <div className="truncate text-[13px] text-[#4f5561]" title={record.jdbcUrl}>
            {record.jdbcUrl || "-"}
          </div>
          <div className="mt-1.5">
            <DataSourceStatus status={record.connStatus} />
          </div>
        </div>
      ),
    },
    {
      key: "description",
      title: intl.formatMessage({ id: "pages.datasource.table.description" }),
      dataIndex: "remark",
      minWidth: 180,
      ellipsis: true,
      render: (_value, record) => record.remark || "-",
    },
    {
      key: "created",
      title: intl.formatMessage({ id: "pages.datasource.table.created" }),
      dataIndex: "createTime",
      width: 170,
      render: (_value, record) => record.createTime || "-",
    },
    {
      key: "updated",
      title: intl.formatMessage({ id: "pages.datasource.table.updated" }),
      dataIndex: "updateTime",
      width: 170,
      render: (_value, record) => record.updateTime || "-",
    },
    {
      key: "actions",
      title: intl.formatMessage({ id: "pages.datasource.table.actions" }),
      width: 120,
      align: "center",
      render: (_value, record) => {
        const id = String(record.id ?? "");

        return (
          <div className="flex items-center justify-center gap-1">
            <Button
              variant="ghost"
              size="small"
              className="px-0.5 text-xs font-normal text-[#667085] hover:text-[var(--yak-color-primary)]"
              disabled={batchBusy || (Boolean(editingId) && editingId !== id)}
              onClick={() => onEdit(record)}
            >
              {intl.formatMessage({ id: "pages.datasource.table.edit" })}
            </Button>
            <span aria-hidden="true" className="h-3 w-px shrink-0 bg-[#e4e7ec]" />
            <Button
              variant="ghost"
              size="small"
              className="px-0.5 text-xs font-normal text-[#667085] hover:text-[#d92d20]"
              disabled={batchBusy}
              onClick={() => onDelete(record)}
            >
              {intl.formatMessage({ id: "pages.datasource.table.delete" })}
            </Button>
          </div>
        );
      },
    },
  ];

  const batchFooter =
    total > 0 ? (
      <div className="flex h-full items-center gap-2">
        <Checkbox
          aria-label={intl.formatMessage({ id: "pages.datasource.batch.selectCurrentPage" })}
          checked={allCurrentPageSelected}
          indeterminate={!allCurrentPageSelected && someCurrentPageSelected}
          disabled={loading || batchBusy || currentPageIds.length === 0}
          onCheckedChange={toggleCurrentPageSelection}
        />
        <Button
          size="small"
          variant="primary"
          loading={batchDeleting}
          disabled={selectedRowKeys.length === 0 || batchBusy}
          onClick={onBatchDelete}
        >
          {intl.formatMessage({ id: "pages.datasource.batch.delete" })}
        </Button>
        <Button
          size="small"
          loading={batchTesting}
          disabled={selectedRowKeys.length === 0 || batchBusy}
          onClick={onBatchTestConnection}
        >
          {intl.formatMessage({ id: "pages.datasource.batch.testConnection" })}
        </Button>
      </div>
    ) : null;

  return (
    <Table<DataSourceRecord>
      className="min-h-full"
      columns={columns}
      dataSource={records}
      rowKey={(record) => record.id || record.name || record.jdbcUrl || "datasource"}
      loading={loading}
      bordered
      footer={batchFooter}
      rowSelection={{
        selectedRowKeys,
        columnWidth: 48,
        getCheckboxProps: (record) => ({
          disabled: loading || batchBusy || !record.id,
        }),
        onChange: (keys) => onSelectionChange(keys.map(String)),
      }}
      size="medium"
      scroll={{ x: 1280 }}
      emptyText={intl.formatMessage({
        id: hasActiveFilters ? "pages.datasource.empty.filtered" : "pages.datasource.empty.default",
      })}
      pagination={
        total > 0
          ? {
              current: pageNo,
              pageSize,
              total,
              pageSizeOptions: DATA_SOURCE_PAGE_SIZE_OPTIONS,
              pageSizeLabel: "每页显示：",
              showSizeChanger: true,
              disabled: loading || batchBusy,
              onChange: onPageChange,
            }
          : false
      }
    />
  );
};

export default DataSourceTable;

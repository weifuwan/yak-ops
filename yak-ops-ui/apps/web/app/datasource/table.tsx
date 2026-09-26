import { Badge, Button, Table, type BadgeProps, type TableColumns } from "@yak-ops/yak-ui";
import { CircleCheck, CircleMinus, CircleX, Pencil, Trash2, Unplug } from "lucide-react";
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
  testingId: string;
  onPageChange: (page: number, pageSize: number) => void;
  onEdit: (record: DataSourceRecord) => void;
  onDelete: (record: DataSourceRecord) => void;
  onTestConnection: (record: DataSourceRecord) => void;
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
  testingId,
  onPageChange,
  onEdit,
  onDelete,
  onTestConnection,
}: DataSourceTableProps) => {
  const intl = useIntl();

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
      width: 132,
      align: "right",
      render: (_value, record) => {
        const id = String(record.id ?? "");

        return (
          <div className="flex justify-end gap-1">
            <Button
              variant="ghost"
              size="small"
              title={intl.formatMessage({ id: "pages.datasource.table.testConnection" })}
              aria-label={intl.formatMessage({
                id: "pages.datasource.table.testConnection",
              })}
              className="h-8 w-8 p-0"
              loading={testingId === id}
              disabled={Boolean(testingId) && testingId !== id}
              onClick={() => onTestConnection(record)}
            >
              {testingId === id ? null : <Unplug size={14} />}
            </Button>
            <Button
              variant="ghost"
              size="small"
              title={intl.formatMessage({ id: "pages.datasource.table.edit" })}
              aria-label={intl.formatMessage({ id: "pages.datasource.table.edit" })}
              className="h-8 w-8 p-0"
              loading={editingId === id}
              disabled={Boolean(editingId) && editingId !== id}
              onClick={() => onEdit(record)}
            >
              {editingId === id ? null : <Pencil size={14} />}
            </Button>
            <Button
              variant="ghost"
              size="small"
              title={intl.formatMessage({ id: "pages.datasource.table.delete" })}
              aria-label={intl.formatMessage({ id: "pages.datasource.table.delete" })}
              className="h-8 w-8 p-0 text-[#b42318]"
              onClick={() => onDelete(record)}
            >
              <Trash2 size={14} />
            </Button>
          </div>
        );
      },
    },
  ];

  return (
    <Table<DataSourceRecord>
      className="min-h-full"
      columns={columns}
      dataSource={records}
      rowKey={(record) => record.id || record.name || record.jdbcUrl || "datasource"}
      loading={loading}
      bordered
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
              disabled: loading,
              onChange: onPageChange,
            }
          : false
      }
    />
  );
};

export default DataSourceTable;

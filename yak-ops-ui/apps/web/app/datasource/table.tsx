import { Badge, Button, type BadgeProps } from "@yak-ops/yak-ui";
import { CircleCheck, CircleMinus, CircleX, Pencil, Trash2, Unplug } from "lucide-react";
import type { ReactNode } from "react";

import DatabaseIcons from "./icons/DatabaseIcons";
import { useIntl } from "./i18n";
import type { DataSourceConnectionStatus, DataSourceRecord } from "./types";

interface DataSourceTableProps {
  records: DataSourceRecord[];
  editingId: string;
  testingId: string;
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
  editingId,
  testingId,
  onEdit,
  onDelete,
  onTestConnection,
}: DataSourceTableProps) => {
  const intl = useIntl();

  return (
    <div className="overflow-hidden rounded-xl border border-[#e9ebef] bg-white">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[900px] border-collapse text-left">
          <thead className="bg-[#fafbfc]">
            <tr className="border-b border-[#eceef2]">
              <th className="px-4 py-3 text-xs font-medium text-[#667085]">
                {intl.formatMessage({ id: "pages.datasource.table.datasource" })}
              </th>
              <th className="w-[140px] px-4 py-3 text-xs font-medium text-[#667085]">
                {intl.formatMessage({ id: "pages.datasource.table.type" })}
              </th>
              <th className="px-4 py-3 text-xs font-medium text-[#667085]">
                {intl.formatMessage({ id: "pages.datasource.table.jdbcUrl" })}
              </th>
              <th className="w-[130px] px-4 py-3 text-xs font-medium text-[#667085]">
                {intl.formatMessage({ id: "pages.datasource.table.status" })}
              </th>
              <th className="w-[170px] px-4 py-3 text-xs font-medium text-[#667085]">
                {intl.formatMessage({ id: "pages.datasource.table.updated" })}
              </th>
              <th className="w-[132px] px-4 py-3 text-right text-xs font-medium text-[#667085]">
                {intl.formatMessage({ id: "pages.datasource.table.actions" })}
              </th>
            </tr>
          </thead>
          <tbody>
            {records.map((record, index) => {
              const id = String(record.id ?? "");
              return (
                <tr
                  key={id || `${record.name || "datasource"}-${index}`}
                  className="border-b border-[#f0f1f3] last:border-b-0 hover:bg-[#fafbfc]"
                >
                  <td className="px-4 py-3">
                    <div className="flex min-w-0 items-center gap-3">
                      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-[#eceef2] bg-white">
                        <DatabaseIcons dbType={record.dbType} width="22" height="22" />
                      </span>
                      <span
                        className="truncate text-sm font-medium text-[#252832]"
                        title={record.name}
                      >
                        {record.name || "-"}
                      </span>
                    </div>
                  </td>
                  <td className="px-4 py-3 text-sm text-[#4f5561]">{record.dbType || "-"}</td>
                  <td className="px-4 py-3">
                    <span
                      className="block max-w-[520px] truncate text-sm text-[#667085]"
                      title={record.jdbcUrl}
                    >
                      {record.jdbcUrl || "-"}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <DataSourceStatus status={record.connStatus} />
                  </td>
                  <td className="px-4 py-3 text-sm text-[#667085]">{record.updateTime || "-"}</td>
                  <td className="px-4 py-3">
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
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default DataSourceTable;

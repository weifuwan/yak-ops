import {
  Badge,
  Button,
  Spinner,
  Tooltip,
  TooltipContent,
  TooltipTrigger,
  type BadgeProps,
} from "@yak-ops/yak-ui";
import {
  CircleCheck,
  CircleMinus,
  CircleX,
  Pencil,
  Trash2,
  Unplug,
} from "lucide-react";
import type { ReactNode } from "react";

import { getEnvironmentTagConfigMap } from "./constants";
import DatabaseIcons from "./icons/DatabaseIcons";
import { useIntl } from "./i18n";
import type {
  DataSourceConnectionStatus,
  DataSourcePermissions,
  DataSourceRecord,
} from "./types";
import { dataSourceRecordKey } from "./utils";

interface DataSourceTableProps {
  records: DataSourceRecord[];
  permissions: DataSourcePermissions;
  testingId: string;
  editingId: string;
  onEdit: (record: DataSourceRecord) => void;
  onDelete: (record: DataSourceRecord) => void;
  onTestConnection: (record: DataSourceRecord) => void;
}

interface StatusConfigItem {
  tone: NonNullable<BadgeProps["tone"]>;
  icon: ReactNode;
  text: string;
  tooltip: string;
}

const DataSourceStatus = ({
  status,
}: {
  status?: DataSourceConnectionStatus;
}) => {
  const intl = useIntl();

  const connected: StatusConfigItem = {
    tone: "success",
    icon: <CircleCheck size={13} />,
    text: intl.formatMessage({ id: "pages.datasource.status.connected" }),
    tooltip: intl.formatMessage({
      id: "pages.datasource.status.connectedTooltip",
    }),
  };
  const disconnected: StatusConfigItem = {
    tone: "danger",
    icon: <CircleX size={13} />,
    text: intl.formatMessage({ id: "pages.datasource.status.disconnected" }),
    tooltip: intl.formatMessage({
      id: "pages.datasource.status.disconnectedTooltip",
    }),
  };
  const unknown: StatusConfigItem = {
    tone: "neutral",
    icon: <CircleMinus size={13} />,
    text: intl.formatMessage({ id: "pages.datasource.status.unknown" }),
    tooltip: intl.formatMessage({
      id: "pages.datasource.status.unknownTooltip",
    }),
  };
  const statusMap: Record<string, StatusConfigItem> = {
    CONNECTED: connected,
    CONNECTED_SUCCESS: connected,
    DISCONNECTED: disconnected,
    CONNECTED_FAILED: disconnected,
    UNKNOWN: unknown,
    CONNECTED_NONE: unknown,
    CONNECTING: {
      tone: "info",
      icon: <Spinner size="small" label="Connecting" />,
      text: intl.formatMessage({ id: "pages.datasource.status.connecting" }),
      tooltip: intl.formatMessage({
        id: "pages.datasource.status.connectingTooltip",
      }),
    },
  };

  const normalized = String(status || "UNKNOWN").trim().toUpperCase();
  const config = statusMap[normalized] || unknown;

  return (
    <Tooltip>
      <TooltipTrigger
        className="inline-flex"
        aria-label={config.tooltip}
      >
        <Badge
          tone={config.tone}
          className="min-w-20 justify-center gap-1.5 whitespace-nowrap px-2.5 py-0.5"
        >
          {config.icon}
          {config.text}
        </Badge>
      </TooltipTrigger>
      <TooltipContent>{config.tooltip}</TooltipContent>
    </Tooltip>
  );
};

const DataSourceTable = ({
  records,
  permissions,
  testingId,
  editingId,
  onEdit,
  onDelete,
  onTestConnection,
}: DataSourceTableProps) => {
  const intl = useIntl();
  const environmentConfigMap = getEnvironmentTagConfigMap(intl);

  return (
    <div className="overflow-hidden rounded-xl border border-[#e9ebef] bg-white">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[1080px] border-collapse text-left">
          <colgroup>
            <col className="w-[220px]" />
            <col className="w-[140px]" />
            <col className="w-[120px]" />
            <col className="w-[130px]" />
            <col />
            <col className="w-[170px]" />
            <col className="w-[132px]" />
          </colgroup>
          <thead className="bg-[#fafbfc]">
            <tr className="border-b border-[#eceef2]">
              <th className="px-4 py-3 text-xs font-medium text-[#667085]">
                {intl.formatMessage({ id: "pages.datasource.table.datasource" })}
              </th>
              <th className="px-4 py-3 text-xs font-medium text-[#667085]">
                {intl.formatMessage({ id: "pages.datasource.table.type" })}
              </th>
              <th className="px-4 py-3 text-xs font-medium text-[#667085]">
                {intl.formatMessage({ id: "pages.datasource.table.environment" })}
              </th>
              <th className="px-4 py-3 text-xs font-medium text-[#667085]">
                {intl.formatMessage({ id: "pages.datasource.table.status" })}
              </th>
              <th className="px-4 py-3 text-xs font-medium text-[#667085]">
                {intl.formatMessage({ id: "pages.datasource.table.jdbcUrl" })}
              </th>
              <th className="px-4 py-3 text-xs font-medium text-[#667085]">
                {intl.formatMessage({ id: "pages.datasource.table.updated" })}
              </th>
              <th className="px-4 py-3 text-right text-xs font-medium text-[#667085]">
                {intl.formatMessage({ id: "pages.datasource.table.actions" })}
              </th>
            </tr>
          </thead>
          <tbody>
            {records.map((record, index) => {
              const currentId = dataSourceRecordKey(record.id);
              const environment = environmentConfigMap[
                record.environment || ""
              ] || {
                text:
                  record.environmentName ||
                  intl.formatMessage({
                    id: "pages.datasource.environment.uncategorized",
                  }),
                color: "#667085",
                backgroundColor: "#f2f4f7",
                icon: null,
              };

              return (
                <tr
                  key={
                    currentId ||
                    `${record.name || "data-source"}-${index}`
                  }
                  className="border-b border-[#f0f1f3] last:border-b-0 hover:bg-[#fafbfc]"
                >
                  <td className="px-4 py-3">
                    <div className="flex min-w-0 items-center gap-3">
                      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg border border-[#eceef2] bg-white">
                        <DatabaseIcons
                          dbType={record.dbType}
                          width="22"
                          height="22"
                        />
                      </span>
                      <div className="min-w-0">
                        <div
                          title={record.name}
                          className="truncate text-sm font-medium text-[#252832]"
                        >
                          {record.name ||
                            intl.formatMessage({
                              id: "pages.datasource.table.unnamed",
                            })}
                        </div>
                      </div>
                    </div>
                  </td>
                  <td className="px-4 py-3 text-sm text-[#4f5561]">
                    <span className="block truncate" title={record.dbType}>
                      {record.dbType || "-"}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <span
                      className="inline-flex h-6 items-center gap-1 whitespace-nowrap rounded-full px-2 text-xs font-medium"
                      style={{
                        color: environment.color,
                        backgroundColor: environment.backgroundColor,
                      }}
                    >
                      {environment.icon}
                      {environment.text}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <DataSourceStatus status={record.connStatus} />
                  </td>
                  <td className="px-4 py-3">
                    <span
                      title={record.jdbcUrl}
                      className="block max-w-[420px] truncate text-sm text-[#667085]"
                    >
                      {record.jdbcUrl ||
                        intl.formatMessage({
                          id: "pages.datasource.table.noJdbcUrl",
                        })}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm text-[#667085]">
                    {record.updateTime || "-"}
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center justify-end gap-1">
                      {permissions.canTest ? (
                        <Button
                          variant="ghost"
                          size="small"
                          title={intl.formatMessage({
                            id: "pages.datasource.table.testConnection",
                          })}
                          aria-label={intl.formatMessage({
                            id: "pages.datasource.table.testConnection",
                          })}
                          loading={testingId === currentId}
                          disabled={Boolean(testingId) && testingId !== currentId}
                          className="h-8 w-8 p-0 text-[#667085]"
                          onClick={() => onTestConnection(record)}
                        >
                          {testingId === currentId ? null : (
                            <Unplug size={14} strokeWidth={1.9} />
                          )}
                        </Button>
                      ) : null}

                      {permissions.canUpdate ? (
                        <Button
                          variant="ghost"
                          size="small"
                          title={intl.formatMessage({
                            id: "pages.datasource.table.edit",
                          })}
                          aria-label={intl.formatMessage({
                            id: "pages.datasource.table.edit",
                          })}
                          loading={editingId === currentId}
                          disabled={Boolean(editingId) && editingId !== currentId}
                          className="h-8 w-8 p-0 text-[#667085]"
                          onClick={() => onEdit(record)}
                        >
                          {editingId === currentId ? null : (
                            <Pencil size={14} strokeWidth={1.9} />
                          )}
                        </Button>
                      ) : null}

                      {permissions.canDelete ? (
                        <Button
                          variant="ghost"
                          size="small"
                          title={intl.formatMessage({
                            id: "pages.datasource.table.delete",
                          })}
                          aria-label={intl.formatMessage({
                            id: "pages.datasource.table.delete",
                          })}
                          className="h-8 w-8 p-0 text-[#b42318]"
                          onClick={() => onDelete(record)}
                        >
                          <Trash2 size={14} strokeWidth={1.9} />
                        </Button>
                      ) : null}
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

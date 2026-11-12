import type { ReactNode } from "react";
import {
  Table,
  type TableColumns,
} from "@yak-ops/yak-ui";

import type { ResourceItemRecord } from "@/service/resources";

import { ResourceActions } from "./resource-actions";
import { ResourceFileIcon } from "./resource-file-icon";
import { formatSize, getDateParts, getExtension, getName, isDirectoryItem } from "../utils";

interface ResourceTableProps {
  items: ResourceItemRecord[];
  selectedKeys: Set<string>;
  loading: boolean;
  emptyText: ReactNode;
  onSelectionChange: (keys: Set<string>) => void;
  onOpen: (item: ResourceItemRecord) => void;
  onDownload: (item: ResourceItemRecord) => void;
  onRename: (item: ResourceItemRecord) => void;
  onDelete: (item: ResourceItemRecord) => void;
}

export function ResourceTable({
  items,
  selectedKeys,
  loading,
  emptyText,
  onSelectionChange,
  onOpen,
  onDownload,
  onRename,
  onDelete,
}: ResourceTableProps) {
  const columns: TableColumns<ResourceItemRecord> = [
    {
      key: "name",
      title: "名称",
      minWidth: 320,
      render: (_value, item) => {
        const directory = isDirectoryItem(item);
        const name = getName(item);
        return (
          <div className="flex min-w-0 items-center gap-3">
            <ResourceFileIcon item={item} />
            <button
              type="button"
              title={name}
              disabled={!directory}
              onClick={() => {
                if (directory) onOpen(item);
              }}
              className={[
                "min-w-0 truncate text-left text-[13px] font-medium",
                directory
                  ? "cursor-pointer text-[var(--yak-components-panel-text)] hover:text-[var(--yak-color-primary)]"
                  : "cursor-default text-[var(--yak-components-panel-text)]",
              ].join(" ")}
            >
              {name}
            </button>
          </div>
        );
      },
    },
    {
      key: "size",
      title: "大小",
      width: 116,
      render: (_value, item) => (
        <span className="text-xs tabular-nums text-[var(--yak-components-muted-text)]">
          {isDirectoryItem(item) ? "—" : formatSize(item.size)}
        </span>
      ),
    },
    {
      key: "type",
      title: "类型",
      width: 112,
      render: (_value, item) => {
        const name = getName(item);
        return (
          <span className="text-xs text-[var(--yak-components-muted-text)]">
            {isDirectoryItem(item) ? "文件夹" : getExtension(name).toUpperCase() || "文件"}
          </span>
        );
      },
    },
    {
      key: "modified",
      title: "修改时间",
      width: 176,
      render: (_value, item) => {
        const date = getDateParts(item.updateTime);
        return (
          <div className="flex flex-col gap-0.5 text-xs text-[var(--yak-components-tab-text-hover)]">
            <span>{date.date}</span>
            {date.time ? (
              <span className="text-[11px] text-[var(--yak-components-muted-text)]">
                {date.time}
              </span>
            ) : null}
          </div>
        );
      },
    },
    {
      key: "actions",
      title: "操作",
      width: 72,
      align: "center",
      render: (_value, item) => (
        <ResourceActions
          item={item}
          onOpen={onOpen}
          onDownload={onDownload}
          onRename={onRename}
          onDelete={onDelete}
        />
      ),
    },
  ];

  return (
    <div className="min-h-0 flex-1 overflow-auto">
      <Table<ResourceItemRecord>
        className="min-h-full"
        columns={columns}
        dataSource={items}
        rowKey="fullName"
        loading={loading}
        rowHeight={58}
        size="medium"
        bordered={false}
        pagination={false}
        scroll={{ x: 860 }}
        rowSelection={{
          selectedRowKeys: Array.from(selectedKeys),
          columnWidth: 48,
          onChange: (keys) => {
            onSelectionChange(
              new Set(keys.filter((key): key is string => typeof key === "string")),
            );
          },
        }}
        onRow={(item) => ({
          onDoubleClick: () => {
            if (isDirectoryItem(item)) onOpen(item);
          },
        })}
        emptyText={emptyText}
        emptyClassName="min-h-[220px]"
      />
    </div>
  );
}

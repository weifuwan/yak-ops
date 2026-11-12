import type { MouseEvent, ReactNode } from "react";
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
  onSelect: (item: ResourceItemRecord) => void;
  onContextMenu: (item: ResourceItemRecord, event: MouseEvent<HTMLTableRowElement>) => void;
  onOpen: (item: ResourceItemRecord) => void;
  onOpenFile: (item: ResourceItemRecord) => void;
  onPreview: (item: ResourceItemRecord) => void;
  onEdit: (item: ResourceItemRecord) => void;
  onDownload: (item: ResourceItemRecord) => void;
  onReplace: (item: ResourceItemRecord) => void;
  onRename: (item: ResourceItemRecord) => void;
  onDelete: (item: ResourceItemRecord) => void;
}

export function ResourceTable({
  items,
  selectedKeys,
  loading,
  emptyText,
  onSelectionChange,
  onSelect,
  onContextMenu,
  onOpen,
  onOpenFile,
  onPreview,
  onEdit,
  onDownload,
  onReplace,
  onRename,
  onDelete,
}: ResourceTableProps) {
  const columns: TableColumns<ResourceItemRecord> = [
    {
      key: "name",
      title: "名称",
      minWidth: 320,
      render: (_value, item) => {
        const name = getName(item);
        return (
          <div className="flex min-w-0 items-center gap-3">
            <ResourceFileIcon item={item} />
            <button
              type="button"
              data-resource-name
              title={name}
              onClick={() => onSelect(item)}
              onDoubleClick={(event) => {
                event.stopPropagation();
                if (isDirectoryItem(item)) onOpen(item);
                else onOpenFile(item);
              }}
              className="min-w-0 cursor-pointer truncate text-left text-[13px] font-medium text-[var(--yak-components-panel-text)] hover:text-[var(--yak-color-primary)]"
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
          onPreview={onPreview}
          onEdit={onEdit}
          onDownload={onDownload}
          onReplace={onReplace}
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
          onClick: (event) => {
            const target = event.target as HTMLElement;
            if (target.closest("input, button, [role='checkbox']")) return;
            onSelect(item);
          },
          onDoubleClick: (event) => {
            const target = event.target as HTMLElement;
            if (target.closest("[data-resource-action], input")) return;
            if (isDirectoryItem(item)) onOpen(item);
            else onOpenFile(item);
          },
          onContextMenu: (event) => onContextMenu(item, event),
        })}
        emptyText={emptyText}
        emptyClassName="min-h-[220px]"
      />
    </div>
  );
}

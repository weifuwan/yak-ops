import { Button, Table, type TableColumns } from "@yak-ops/yak-ui";
import { Copy, PencilLine, Trash2 } from "lucide-react";

import type { AccessTokenRecord } from "@/service/access-token";

const PAGE_SIZE_OPTIONS = [10, 20, 50];

interface Props {
  records: AccessTokenRecord[];
  loading: boolean;
  pageNo: number;
  pageSize: number;
  total: number;
  filtered: boolean;
  deleting: boolean;
  onCopy: (token: string) => void;
  onEdit: (record: AccessTokenRecord) => void;
  onDelete: (record: AccessTokenRecord) => void;
  onPageChange: (pageNo: number, pageSize: number) => void;
}

const formatDateTime = (value?: string | null) => {
  if (!value) return "—";
  return value.replace("T", " ").slice(0, 19);
};

const isExpired = (value: string) => {
  const timestamp = new Date(value.replace(" ", "T")).getTime();
  return Number.isFinite(timestamp) && timestamp <= Date.now();
};

const maskToken = (value: string) => {
  if (value.length <= 12) return "••••••••";
  return value.slice(0, 6) + "••••••••" + value.slice(-4);
};

export function AccessTokenTable({
  records,
  loading,
  pageNo,
  pageSize,
  total,
  filtered,
  deleting,
  onCopy,
  onEdit,
  onDelete,
  onPageChange,
}: Props) {
  const columns: TableColumns<AccessTokenRecord> = [
    {
      key: "sequence",
      title: "序号",
      width: 72,
      align: "center",
      render: (_value, _record, index) =>
        String((pageNo - 1) * pageSize + index + 1).padStart(2, "0"),
    },
    {
      key: "userName",
      title: "所属用户",
      dataIndex: "userName",
      minWidth: 140,
      render: (_value, record) => (
        <span className="font-medium text-[var(--yak-components-panel-text)]">
          {record.userName || "用户 ID：" + record.userId}
        </span>
      ),
    },
    {
      key: "token",
      title: "Token",
      minWidth: 250,
      render: (_value, record) => (
        <div className="flex min-w-0 items-center gap-2">
          <code className="min-w-0 truncate text-xs text-[var(--yak-components-muted-text)]">
            {maskToken(record.token)}
          </code>
          <Button
            variant="ghost"
            size="sm"
            title="复制 Token"
            aria-label={"复制 " + (record.userName || "用户") + " 的 Token"}
            className="shrink-0 px-1 text-[var(--yak-components-muted-text)] hover:text-[var(--yak-color-primary)]"
            onClick={() => onCopy(record.token)}
          >
            <Copy size={13} />
          </Button>
        </div>
      ),
    },
    {
      key: "status",
      title: "状态",
      width: 100,
      render: (_value, record) => {
        const expired = isExpired(record.expireTime);
        return (
          <span
            className={
              expired
                ? "text-[var(--yak-color-danger)]"
                : "text-[var(--yak-color-success)]"
            }
          >
            {expired ? "已过期" : "有效"}
          </span>
        );
      },
    },
    {
      key: "expireTime",
      title: "有效期至",
      dataIndex: "expireTime",
      width: 180,
      render: (_value, record) => formatDateTime(record.expireTime),
    },
    {
      key: "createTime",
      title: "创建时间",
      dataIndex: "createTime",
      width: 180,
      render: (_value, record) => formatDateTime(record.createTime),
    },
    {
      key: "actions",
      title: "操作",
      width: 150,
      align: "center",
      render: (_value, record) => (
        <div className="flex items-center justify-center gap-1">
          <Button
            variant="ghost"
            size="sm"
            className="px-1 text-xs font-normal text-[var(--yak-components-muted-text)] hover:text-[var(--yak-color-primary)]"
            onClick={() => onEdit(record)}
          >
            <PencilLine size={13} />
            编辑
          </Button>
          <span
            aria-hidden="true"
            className="h-3 w-px shrink-0 bg-[var(--yak-components-panel-border)]"
          />
          <Button
            variant="ghost"
            size="sm"
            isDisabled={deleting}
            className="px-1 text-xs font-normal text-[var(--yak-components-muted-text)] hover:text-[var(--yak-color-danger)]"
            onClick={() => onDelete(record)}
          >
            <Trash2 size={13} />
            删除
          </Button>
        </div>
      ),
    },
  ];

  return (
    <Table<AccessTokenRecord>
      className="min-h-full"
      columns={columns}
      dataSource={records}
      rowKey="id"
      loading={loading}
      bordered
      size="medium"
      scroll={{ x: 1050 }}
      emptyText={filtered ? "未找到匹配用户的 Token" : "暂无 Token"}
      pagination={
        total > 0
          ? {
              current: pageNo,
              pageSize,
              total,
              pageSizeOptions: PAGE_SIZE_OPTIONS,
              pageSizeLabel: "每页显示：",
              showSizeChanger: true,
              disabled: loading || deleting,
              onChange: onPageChange,
            }
          : false
      }
    />
  );
}

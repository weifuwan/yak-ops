import { Button, Table, type TableColumns } from "@yak-ops/yak-ui";

import type { TenantRecord } from "@/service/tenant";

const PAGE_SIZE_OPTIONS = [10, 20, 50];

interface Props {
  records: TenantRecord[];
  loading: boolean;
  pageNo: number;
  pageSize: number;
  total: number;
  filtered: boolean;
  canManage: boolean;
  deleting: boolean;
  onEdit: (record: TenantRecord) => void;
  onDelete: (record: TenantRecord) => void;
  onPageChange: (pageNo: number, pageSize: number) => void;
}

export function TenantTable({
  records,
  loading,
  pageNo,
  pageSize,
  total,
  filtered,
  canManage,
  deleting,
  onEdit,
  onDelete,
  onPageChange,
}: Props) {
  const columns: TableColumns<TenantRecord> = [
    {
      key: "sequence",
      title: "序号",
      width: 72,
      align: "center",
      render: (_value, _record, index) =>
        String((pageNo - 1) * pageSize + index + 1).padStart(2, "0"),
    },
    {
      key: "tenantCode",
      title: "租户编码",
      dataIndex: "tenantCode",
      minWidth: 180,
      render: (_value, record) => (
        <span className="font-medium text-[var(--yak-components-panel-text)]">
          {record.tenantCode || "-"}
        </span>
      ),
    },
    {
      key: "queueName",
      title: "所属队列",
      dataIndex: "queueName",
      minWidth: 180,
      render: (_value, record) => record.queueName || "-",
    },
    {
      key: "description",
      title: "描述",
      dataIndex: "description",
      minWidth: 220,
      ellipsis: true,
      render: (_value, record) => record.description || "-",
    },
    {
      key: "createTime",
      title: "创建时间",
      dataIndex: "createTime",
      width: 180,
      render: (_value, record) => record.createTime || "-",
    },
    {
      key: "updateTime",
      title: "更新时间",
      dataIndex: "updateTime",
      width: 180,
      render: (_value, record) => record.updateTime || "-",
    },
    ...(canManage
      ? [
          {
            key: "actions",
            title: "操作",
            width: 120,
            align: "center" as const,
            render: (_value: unknown, record: TenantRecord) => (
              <div className="flex items-center justify-center gap-1">
                <Button
                  variant="ghost"
                  size="sm"
                  className="px-0.5 text-xs font-normal text-[var(--yak-components-muted-text)] hover:text-[var(--yak-color-primary)]"
                  onClick={() => onEdit(record)}
                >
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
                  className="px-0.5 text-xs font-normal text-[var(--yak-components-muted-text)] hover:text-[var(--yak-color-danger)]"
                  onClick={() => onDelete(record)}
                >
                  删除
                </Button>
              </div>
            ),
          },
        ]
      : []),
  ];

  return (
    <Table<TenantRecord>
      className="min-h-full"
      columns={columns}
      dataSource={records}
      rowKey="id"
      loading={loading}
      bordered
      size="medium"
      scroll={{ x: 1100 }}
      emptyText={filtered ? "未找到匹配租户" : "暂无租户"}
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

import { Button, Table, type TableColumns } from "@yak-ops/yak-ui";
import type { UserRecord } from "@/service/user";

const PAGE_SIZE_OPTIONS = [10, 20, 50];

interface Props {
  records: UserRecord[];
  loading: boolean;
  pageNo: number;
  pageSize: number;
  total: number;
  filtered: boolean;
  currentUserId?: string;
  deleting: boolean;
  onEdit: (record: UserRecord) => void;
  onDelete: (record: UserRecord) => void;
  onPageChange: (pageNo: number, pageSize: number) => void;
}

export function UserTable({
  records,
  loading,
  pageNo,
  pageSize,
  total,
  filtered,
  currentUserId,
  deleting,
  onEdit,
  onDelete,
  onPageChange,
}: Props) {
  const columns: TableColumns<UserRecord> = [
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
      title: "用户名",
      dataIndex: "userName",
      minWidth: 160,
      render: (_value, record) => (
        <span className="font-medium text-[var(--yak-components-panel-text)]">
          {record.userName || "-"}
        </span>
      ),
    },
    {
      key: "realName",
      title: "姓名",
      dataIndex: "realName",
      minWidth: 140,
      render: (_value, record) => record.realName || "-",
    },
    {
      key: "phone",
      title: "手机号",
      dataIndex: "phone",
      minWidth: 160,
      render: (_value, record) => record.phone || "-",
    },
    {
      key: "email",
      title: "邮箱",
      dataIndex: "email",
      minWidth: 220,
      ellipsis: true,
      render: (_value, record) => record.email || "-",
    },
    {
      key: "createTime",
      title: "创建时间",
      dataIndex: "createTime",
      width: 170,
      render: (_value, record) => record.createTime || "-",
    },
    {
      key: "actions",
      title: "操作",
      width: 120,
      align: "center",
      render: (_value, record) => {
        const deletingSelf = currentUserId === record.id;

        return (
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
              isDisabled={deletingSelf}
              title={deletingSelf ? "不能删除当前登录用户" : undefined}
              className="px-0.5 text-xs font-normal text-[var(--yak-components-muted-text)] hover:text-[var(--yak-color-danger)]"
              onClick={() => onDelete(record)}
            >
              删除
            </Button>
          </div>
        );
      },
    },
  ];
  return (
    <Table<UserRecord>
      className="min-h-full"
      columns={columns}
      dataSource={records}
      rowKey="id"
      loading={loading}
      bordered
      size="medium"
      scroll={{ x: 1120 }}
      emptyText={filtered ? "未找到匹配用户" : "暂无用户"}
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
              onChange: (nextPage, nextPageSize) => {
                onPageChange(nextPage, nextPageSize);
              },
            }
          : false
      }
    />
  );
}

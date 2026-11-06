import { Button, Table, type TableColumns } from "@yak-ops/yak-ui";
import type { WorkspaceRecord } from "@/service/workspace";

interface Props {
  records: WorkspaceRecord[];
  loading: boolean;
  currentUserId?: string;
  isAdmin: boolean;
  onMembers: (record: WorkspaceRecord) => void;
  onEdit: (record: WorkspaceRecord) => void;
  onDelete: (record: WorkspaceRecord) => void;
}

export function WorkspaceTable({
  records, loading, currentUserId, isAdmin, onMembers, onEdit, onDelete,
}: Props) {
  const columns: TableColumns<WorkspaceRecord> = [
    {
      key: "name",
      title: "工作空间名称",
      dataIndex: "name",
      minWidth: 220,
      render: (_value, record) => (
        <span className="font-medium text-[var(--yak-components-panel-text)]">{record.name}</span>
      ),
    },
    {
      key: "description",
      title: "描述",
      dataIndex: "description",
      minWidth: 260,
      ellipsis: true,
      render: (_value, record) => record.description || "-",
    },
    { key: "role", title: "我的权限", width: 110, render: (_value, record) => record.roleName },
    { key: "createTime", title: "创建时间", dataIndex: "createTime", width: 180 },
    { key: "updateTime", title: "修改时间", dataIndex: "updateTime", width: 180 },
    {
      key: "actions", title: "操作", width: 235,
      render: (_value, record) => {
        const canWrite = isAdmin || record.ownerId === currentUserId;
        return (
          <div className="flex items-center gap-1">
            <Button size="small" variant="ghost" onClick={() => onMembers(record)}>成员</Button>
            {canWrite ? (
              <>
                <Button size="small" variant="ghost" onClick={() => onEdit(record)}>编辑</Button>
                <Button size="small" variant="ghost" onClick={() => onDelete(record)}>删除</Button>
              </>
            ) : null}
          </div>
        );
      },
    },
  ];
  return (
    <Table<WorkspaceRecord>
      className="min-h-full"
      columns={columns}
      dataSource={records}
      rowKey="id"
      loading={loading}
      bordered
      size="medium"
      scroll={{ x: 1180 }}
      emptyText="暂无工作空间"
      pagination={false}
    />
  );
}

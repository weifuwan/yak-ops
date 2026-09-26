import {
  Button,
  Field,
  FieldLabel,
  Input,
  Modal,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Table,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { useEffect, useMemo, useState } from "react";

import { searchUsers, type UserBriefRecord } from "@/service/user";
import { addWorkspaceMember, type WorkspaceRole } from "@/service/workspace";

type WorkspaceMemberFormProps = {
  open: boolean;
  workspaceId?: string;
  existingUserIds: string[];
  canAssignOwner: boolean;
  onClose: () => void;
  onAdded: () => void;
};

const ROLE_LABELS: Record<WorkspaceRole, string> = {
  OWNER: "所有者",
  ADMIN: "管理员",
  MEMBER: "成员",
};

export default function WorkspaceMemberForm({
  open,
  workspaceId,
  existingUserIds,
  canAssignOwner,
  onClose,
  onAdded,
}: WorkspaceMemberFormProps) {
  const [keyword, setKeyword] = useState("");
  const [role, setRole] = useState<WorkspaceRole>("MEMBER");
  const [records, setRecords] = useState<UserBriefRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [addingUserId, setAddingUserId] = useState("");

  const existingUserIdSet = useMemo(() => new Set(existingUserIds), [existingUserIds]);

  useEffect(() => {
    if (!open) return;
    setKeyword("");
    setRole("MEMBER");
    setRecords([]);
    setAddingUserId("");
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const normalized = keyword.trim();
    if (!normalized) {
      setRecords([]);
      setLoading(false);
      return;
    }

    let active = true;
    setLoading(true);
    const timer = window.setTimeout(() => {
      searchUsers(normalized)
        .then((users) => {
          if (active) setRecords(users.filter((user) => !existingUserIdSet.has(user.id)));
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    }, 250);

    return () => {
      active = false;
      window.clearTimeout(timer);
    };
  }, [existingUserIdSet, keyword, open]);

  const handleAdd = async (user: UserBriefRecord) => {
    if (!workspaceId || addingUserId) return;
    setAddingUserId(user.id);
    try {
      await addWorkspaceMember(workspaceId, {
        userId: user.id,
        role,
      });
      onAdded();
      onClose();
    } finally {
      setAddingUserId("");
    }
  };

  const columns: TableColumns<UserBriefRecord> = [
    {
      key: "user",
      title: "用户",
      minWidth: 260,
      render: (_value, record) => (
        <div className="min-w-0">
          <div className="truncate text-[13px] font-medium text-[#252832]">
            {record.realName || record.userName}
          </div>
          <div className="mt-0.5 truncate text-xs text-[#667085]">{record.userName}</div>
        </div>
      ),
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
      key: "actions",
      title: "操作",
      width: 90,
      align: "center",
      render: (_value, record) => (
        <Button
          size="small"
          variant="primary"
          loading={addingUserId === record.id}
          disabled={Boolean(addingUserId) && addingUserId !== record.id}
          onClick={() => void handleAdd(record)}
        >
          添加
        </Button>
      ),
    },
  ];

  const roleOptions = canAssignOwner
    ? (["OWNER", "ADMIN", "MEMBER"] as WorkspaceRole[])
    : (["ADMIN", "MEMBER"] as WorkspaceRole[]);

  return (
    <Modal open={open} width={760} title="添加成员" onClose={onClose}>
      <div className="space-y-4">
        <div className="grid grid-cols-[minmax(0,1fr)_180px] gap-3 max-sm:grid-cols-1">
          <Field>
            <FieldLabel htmlFor="workspace-member-keyword">搜索用户</FieldLabel>
            <Input
              id="workspace-member-keyword"
              variant="outlined"
              value={keyword}
              placeholder="输入用户名或姓名"
              onChange={(event) => setKeyword(event.target.value)}
            />
          </Field>

          <Field>
            <FieldLabel>加入角色</FieldLabel>
            <Select<WorkspaceRole>
              items={ROLE_LABELS}
              value={role}
              onValueChange={(nextRole) => {
                if (nextRole) setRole(nextRole);
              }}
            >
              <SelectTrigger variant="outlined">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {roleOptions.map((option) => (
                  <SelectItem key={option} value={option}>
                    <SelectItemText>{ROLE_LABELS[option]}</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
        </div>

        <Table<UserBriefRecord>
          columns={columns}
          dataSource={records}
          rowKey="id"
          loading={loading}
          bordered
          size="medium"
          scroll={{ x: 620 }}
          emptyText={keyword.trim() ? "未找到可添加的用户" : "请输入用户名或姓名搜索"}
          pagination={false}
        />
      </div>
    </Modal>
  );
}

import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Button, Modal, Table, toast, type TableColumns } from "@yak-ops/yak-ui";
import { Plus } from "lucide-react";

import { useAuth } from "@/hooks/use-auth";
import {
  addWorkspaceMember,
  getWorkspaceMembers,
  listAssignableUsers,
  removeWorkspaceMember,
  type WorkspaceMemberRecord,
  type WorkspaceRecord,
} from "@/service/workspace";

interface Props {
  workspace: WorkspaceRecord;
  onClose: () => void;
}

export function MemberDialog({ workspace, onClose }: Props) {
  const { currentUser } = useAuth();
  const client = useQueryClient();
  const [adding, setAdding] = useState(false);
  const [selectedUserId, setSelectedUserId] = useState("");
  const isAdmin = currentUser?.userType === "ADMIN_USER";
  const isOwner = currentUser?.id === workspace.ownerId;
  const canAdd = isAdmin || isOwner;
  const membersKey = ["workspace-members", currentUser?.id, workspace.id];
  const membersQuery = useQuery({
    queryKey: membersKey,
    queryFn: () => getWorkspaceMembers(workspace.id),
  });
  const usersQuery = useQuery({
    queryKey: ["workspace-available-users", currentUser?.id],
    queryFn: listAssignableUsers,
    enabled: adding && canAdd,
  });
  const members = useMemo(() => {
    const rows = membersQuery.data ?? [];
    return [
      { userId: workspace.ownerId,
        userName: rows.find((row) => row.userId === workspace.ownerId)?.userName ?? "项目所有者" },
      ...rows.filter((row) => row.userId !== workspace.ownerId),
    ];
  }, [membersQuery.data, workspace.ownerId]);
  const add = useMutation({
    mutationFn: (userId: string) => addWorkspaceMember(workspace.id, userId),
    onSuccess: async () => {
      toast.success("成员授权成功");
      setAdding(false);
      setSelectedUserId("");
      await client.invalidateQueries({ queryKey: membersKey });
    },
  });
  const remove = useMutation({
    mutationFn: (userId: string) => removeWorkspaceMember(workspace.id, userId),
    onSuccess: async () => {
      toast.success("成员授权已撤销");
      await client.invalidateQueries({ queryKey: membersKey });
    },
  });
  const selectable = (usersQuery.data ?? []).filter(
    (user) => !members.some((member) => member.userId === user.id),
  );
  const columns: TableColumns<WorkspaceMemberRecord> = [
    { key: "userName", title: "用户名", dataIndex: "userName" },
    { key: "userId", title: "用户 ID", dataIndex: "userId", width: 110 },
    {
      key: "role", title: "授权", width: 140,
      render: (_value, member) =>
        member.userId === workspace.ownerId ? "所有者" : "项目访问权限",
    },
    {
      key: "actions", title: "操作", width: 110,
      render: (_value, member) =>
        isAdmin && member.userId !== workspace.ownerId ? (
          <Button size="sm" variant="ghost" isDisabled={remove.isPending}
            onClick={() => remove.mutate(member.userId)}>撤销授权</Button>
        ) : null,
    },
  ];
  return (
    <Modal open width={760} title={workspace.name + " · 成员"} onClose={onClose}>
      <div className="space-y-3">
        <div className="flex items-center justify-between gap-4">
          <span className="text-xs text-[var(--yak-components-muted-text)]">
            成员权限由现有 Project 授权体系管理。撤销授权仅系统管理员可操作。
          </span>
          {canAdd ? (
            <Button size="sm" variant="primary" onClick={() => setAdding(true)}>
              <Plus size={14} /> 添加成员
            </Button>
          ) : null}
        </div>
        {adding && canAdd ? (
          <div className="flex flex-wrap items-center gap-2 rounded-md border p-3">
            <select value={selectedUserId} onChange={(event) => setSelectedUserId(event.target.value)}
              aria-label="选择用户" className="h-8 min-w-52 rounded-md border border-black/15 bg-white px-2 text-sm">
              <option value="">请选择用户</option>
              {selectable.map((user) => (
                <option key={user.id} value={user.id}>{user.userName}</option>
              ))}
            </select>
            <Button size="sm" variant="primary" isPending={add.isPending}
              isDisabled={!selectedUserId || usersQuery.isLoading}
              onClick={() => add.mutate(selectedUserId)}>授权写入权限</Button>
            <Button variant="secondary" size="sm" isDisabled={add.isPending} onClick={() => setAdding(false)}>取消</Button>
            {usersQuery.isError ? <span role="alert" className="text-xs text-red-600">用户列表加载失败</span> : null}
          </div>
        ) : null}
        {membersQuery.isError ? (
          <div role="alert" className="text-sm">
            成员列表加载失败
            <Button variant="secondary" size="sm" onClick={() => void membersQuery.refetch()}>重试</Button>
          </div>
        ) : null}
        <Table<WorkspaceMemberRecord>
          columns={columns}
          dataSource={membersQuery.isError ? [] : members}
          rowKey="userId"
          loading={membersQuery.isPending || membersQuery.isFetching}
          bordered size="medium" pagination={false}
          emptyText="暂无已授权成员"
        />
      </div>
    </Modal>
  );
}

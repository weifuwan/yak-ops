import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  Modal,
  PageHeader,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Table,
  toast,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { Plus } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";

import { useAuth } from "@/hooks/use-auth";
import { getUsersByIds, type UserRecord } from "@/service/user";
import {
  getWorkspaceMembers,
  listWorkspaces,
  removeWorkspaceMember,
  updateWorkspaceMemberRole,
  type WorkspaceMemberRecord,
  type WorkspaceRecord,
  type WorkspaceRole,
} from "@/service/workspace";

import WorkspaceForm from "./form";
import WorkspaceMemberForm from "./member-form";

type MemberRow = WorkspaceMemberRecord & {
  userName?: string;
  realName?: string | null;
};

const ROLE_LABELS: Record<WorkspaceRole, string> = {
  OWNER: "所有者",
  ADMIN: "管理员",
  MEMBER: "成员",
};

export default function WorkspaceManagementPage() {
  const { currentUser } = useAuth();
  const memberRequestSequenceRef = useRef(0);
  const [records, setRecords] = useState<WorkspaceRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [refreshVersion, setRefreshVersion] = useState(0);
  const [formOpen, setFormOpen] = useState(false);
  const [memberWorkspace, setMemberWorkspace] = useState<WorkspaceRecord>();
  const [members, setMembers] = useState<MemberRow[]>([]);
  const [memberLoading, setMemberLoading] = useState(false);
  const [addMemberOpen, setAddMemberOpen] = useState(false);
  const [roleUpdatingUserId, setRoleUpdatingUserId] = useState("");
  const [pendingRemove, setPendingRemove] = useState<MemberRow>();
  const [removing, setRemoving] = useState(false);

  const refresh = useCallback(() => setRefreshVersion((value) => value + 1), []);

  useEffect(() => {
    let active = true;
    setLoading(true);
    listWorkspaces()
      .then((nextRecords) => {
        if (active) setRecords(nextRecords || []);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [refreshVersion]);

  const loadMembers = useCallback(async (workspace: WorkspaceRecord) => {
    const requestSequence = memberRequestSequenceRef.current + 1;
    memberRequestSequenceRef.current = requestSequence;
    setMemberLoading(true);

    try {
      const memberRecords = await getWorkspaceMembers(workspace.id);
      const ids = [...new Set(memberRecords.map((member) => member.userId).filter(Boolean))];
      const users = await getUsersByIds(ids);
      if (requestSequence !== memberRequestSequenceRef.current) return;

      const userMap = new Map<string, UserRecord>(users.map((user) => [user.id, user]));
      setMembers(
        memberRecords.map((member) => {
          const user = userMap.get(member.userId);
          return {
            ...member,
            userName: user?.userName,
            realName: user?.realName,
          };
        }),
      );
    } finally {
      if (requestSequence === memberRequestSequenceRef.current) setMemberLoading(false);
    }
  }, []);

  const openMembers = async (workspace: WorkspaceRecord) => {
    setMemberWorkspace(workspace);
    setMembers([]);
    await loadMembers(workspace);
  };

  const handleRoleChange = async (member: MemberRow, role: WorkspaceRole) => {
    if (!memberWorkspace || roleUpdatingUserId) return;
    setRoleUpdatingUserId(member.userId);
    try {
      await updateWorkspaceMemberRole(memberWorkspace.id, member.userId, { role });
      toast.success("成员角色更新成功");

      if (member.userId === currentUser?.id) {
        setMemberWorkspace({
          ...memberWorkspace,
          role,
          roleName: ROLE_LABELS[role],
        });
        refresh();
      }

      await loadMembers(memberWorkspace);
    } finally {
      setRoleUpdatingUserId("");
    }
  };

  const confirmRemove = async () => {
    if (!memberWorkspace || !pendingRemove || removing) return;
    setRemoving(true);
    try {
      await removeWorkspaceMember(memberWorkspace.id, pendingRemove.userId);
      toast.success("成员移除成功");

      if (pendingRemove.userId === currentUser?.id) {
        memberRequestSequenceRef.current += 1;
        setPendingRemove(undefined);
        setMemberWorkspace(undefined);
        setMembers([]);
        refresh();
        return;
      }

      setPendingRemove(undefined);
      await loadMembers(memberWorkspace);
    } finally {
      setRemoving(false);
    }
  };

  const columns: TableColumns<WorkspaceRecord> = [
    {
      key: "name",
      title: "工作空间名称",
      dataIndex: "name",
      minWidth: 220,
      render: (_value, record) => (
        <span className="font-medium text-[#252832]">{record.name || "-"}</span>
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
    {
      key: "role",
      title: "我的角色",
      width: 140,
      render: (_value, record) => record.roleName || record.role || "-",
    },
    {
      key: "createTime",
      title: "创建时间",
      dataIndex: "createTime",
      width: 170,
      render: (_value, record) => record.createTime || "-",
    },
    {
      key: "updateTime",
      title: "修改时间",
      dataIndex: "updateTime",
      width: 170,
      render: (_value, record) => record.updateTime || "-",
    },
    {
      key: "actions",
      title: "操作",
      width: 110,
      align: "center",
      render: (_value, record) => (
        <Button
          variant="ghost"
          size="small"
          className="px-0.5 text-xs font-normal text-[#667085] hover:text-[var(--yak-color-primary)]"
          onClick={() => void openMembers(record)}
        >
          {record.role === "MEMBER" ? "查看成员" : "管理成员"}
        </Button>
      ),
    },
  ];

  const actorRole = memberWorkspace?.role;
  const canManageMembers = actorRole === "OWNER" || actorRole === "ADMIN";
  const ownerCount = members.filter((member) => member.role === "OWNER").length;

  const memberColumns: TableColumns<MemberRow> = [
    {
      key: "user",
      title: "成员",
      minWidth: 220,
      render: (_value, record) => (
        <div className="min-w-0">
          <div className="truncate text-[13px] font-medium text-[#252832]">
            {record.realName || record.userName || record.userId}
          </div>
          <div className="mt-0.5 truncate text-xs text-[#667085]">
            {record.userName || record.userId}
          </div>
        </div>
      ),
    },
    {
      key: "role",
      title: "角色",
      width: 180,
      render: (_value, record) => {
        const targetIsOwner = record.role === "OWNER";
        const lastOwner = targetIsOwner && ownerCount <= 1;
        const canEditTarget =
          canManageMembers && (actorRole === "OWNER" || !targetIsOwner) && !lastOwner;

        if (!canEditTarget) return record.roleName || ROLE_LABELS[record.role];

        const roleOptions =
          actorRole === "OWNER"
            ? (["OWNER", "ADMIN", "MEMBER"] as WorkspaceRole[])
            : (["ADMIN", "MEMBER"] as WorkspaceRole[]);

        return (
          <Select<WorkspaceRole>
            size="small"
            items={ROLE_LABELS}
            value={record.role}
            disabled={roleUpdatingUserId === record.userId}
            onValueChange={(role) => {
              if (role) void handleRoleChange(record, role);
            }}
          >
            <SelectTrigger variant="outlined" className="w-32">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {roleOptions.map((role) => (
                <SelectItem key={role} value={role}>
                  <SelectItemText>{ROLE_LABELS[role]}</SelectItemText>
                  <SelectItemIndicator />
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        );
      },
    },
    {
      key: "joinedAt",
      title: "加入时间",
      dataIndex: "joinedAt",
      width: 180,
      render: (_value, record) => record.joinedAt || "-",
    },
    {
      key: "actions",
      title: "操作",
      width: 90,
      align: "center",
      render: (_value, record) => {
        const targetIsOwner = record.role === "OWNER";
        const lastOwner = targetIsOwner && ownerCount <= 1;
        const canRemove =
          canManageMembers && (actorRole === "OWNER" || !targetIsOwner) && !lastOwner;

        if (!canRemove) return "-";

        return (
          <Button
            variant="ghost"
            size="small"
            className="px-0.5 text-xs font-normal text-[#667085] hover:text-[#d92d20]"
            onClick={() => setPendingRemove(record)}
          >
            移除
          </Button>
        );
      },
    },
  ];

  return (
    <>
      <div className="flex min-h-full flex-col bg-[#F6F6F6] text-[#242731]">
        <PageHeader title="工作空间管理" bordered className="bg-white px-6 max-md:px-4" />

        <div className="flex min-h-0 flex-1 px-6 pb-4 pt-5 max-md:px-4">
          <div className="flex min-h-0 flex-1 flex-col bg-white p-4">
            <section className="flex shrink-0 items-center">
              <Button size="small" variant="primary" onClick={() => setFormOpen(true)}>
                <Plus size={14} />
                新建工作空间
              </Button>
            </section>

            <section className="mt-4 min-h-0 flex-1">
              <Table<WorkspaceRecord>
                className="min-h-full"
                columns={columns}
                dataSource={records}
                rowKey="id"
                loading={loading}
                bordered
                size="medium"
                scroll={{ x: 1080 }}
                emptyText="暂无工作空间"
                pagination={false}
              />
            </section>
          </div>
        </div>
      </div>

      <WorkspaceForm
        open={formOpen}
        onClose={() => setFormOpen(false)}
        onSaved={() => {
          toast.success("工作空间创建成功");
          refresh();
        }}
      />

      <Modal
        open={Boolean(memberWorkspace)}
        width={820}
        title={memberWorkspace ? `${memberWorkspace.name} · 成员` : "工作空间成员"}
        onClose={() => {
          if (removing || roleUpdatingUserId) return;
          memberRequestSequenceRef.current += 1;
          setMemberWorkspace(undefined);
          setMembers([]);
          setMemberLoading(false);
          setAddMemberOpen(false);
          setPendingRemove(undefined);
        }}
      >
        <div className="space-y-3">
          {canManageMembers ? (
            <div className="flex justify-end">
              <Button size="small" variant="primary" onClick={() => setAddMemberOpen(true)}>
                <Plus size={14} />
                添加成员
              </Button>
            </div>
          ) : null}

          <Table<MemberRow>
            columns={memberColumns}
            dataSource={members}
            rowKey="userId"
            loading={memberLoading}
            bordered
            size="medium"
            scroll={{ x: 680 }}
            emptyText="暂无成员"
            pagination={false}
          />
        </div>
      </Modal>

      <WorkspaceMemberForm
        open={addMemberOpen}
        workspaceId={memberWorkspace?.id}
        existingUserIds={members.map((member) => member.userId)}
        canAssignOwner={actorRole === "OWNER"}
        onClose={() => setAddMemberOpen(false)}
        onAdded={() => {
          toast.success("成员添加成功");
          if (memberWorkspace) void loadMembers(memberWorkspace);
        }}
      />

      <Dialog
        open={Boolean(pendingRemove)}
        onOpenChange={(open) => {
          if (!open && !removing) setPendingRemove(undefined);
        }}
      >
        <DialogContent className="w-[420px]">
          <DialogTitle className="text-base font-semibold">移除成员</DialogTitle>
          <DialogDescription className="mt-2 text-sm leading-6 text-[#667085]">
            确定将“
            {pendingRemove?.realName || pendingRemove?.userName || pendingRemove?.userId || "-"}
            ”移出当前工作空间吗？
          </DialogDescription>
          <div className="mt-6 flex justify-end gap-2">
            <Button size="small" disabled={removing} onClick={() => setPendingRemove(undefined)}>
              取消
            </Button>
            <Button
              size="small"
              variant="danger"
              loading={removing}
              onClick={() => void confirmRemove()}
            >
              移除
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}

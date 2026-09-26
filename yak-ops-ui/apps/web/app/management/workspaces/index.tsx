import { Button, Modal, PageHeader, Table, toast, type TableColumns } from "@yak-ops/yak-ui";
import { Plus } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";

import { getUsersByIds, type UserRecord } from "@/service/user";
import {
  getWorkspaceMembers,
  listWorkspaces,
  type WorkspaceMemberRecord,
  type WorkspaceRecord,
} from "@/service/workspace";

import WorkspaceForm from "./form";

type MemberRow = WorkspaceMemberRecord & {
  userName?: string;
  realName?: string | null;
};

export default function WorkspaceManagementPage() {
  const memberRequestSequenceRef = useRef(0);
  const [records, setRecords] = useState<WorkspaceRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [refreshVersion, setRefreshVersion] = useState(0);
  const [formOpen, setFormOpen] = useState(false);
  const [memberWorkspace, setMemberWorkspace] = useState<WorkspaceRecord>();
  const [members, setMembers] = useState<MemberRow[]>([]);
  const [memberLoading, setMemberLoading] = useState(false);

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

  const openMembers = async (workspace: WorkspaceRecord) => {
    const requestSequence = memberRequestSequenceRef.current + 1;
    memberRequestSequenceRef.current = requestSequence;
    setMemberWorkspace(workspace);
    setMembers([]);
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
          查看成员
        </Button>
      ),
    },
  ];

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
      width: 140,
      render: (_value, record) => record.roleName || record.role || "-",
    },
    {
      key: "joinedAt",
      title: "加入时间",
      dataIndex: "joinedAt",
      width: 180,
      render: (_value, record) => record.joinedAt || "-",
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
        width={720}
        title={memberWorkspace ? `${memberWorkspace.name} · 成员` : "工作空间成员"}
        onClose={() => {
          memberRequestSequenceRef.current += 1;
          setMemberWorkspace(undefined);
          setMembers([]);
          setMemberLoading(false);
        }}
      >
        <Table<MemberRow>
          columns={memberColumns}
          dataSource={members}
          rowKey="userId"
          loading={memberLoading}
          bordered
          size="medium"
          scroll={{ x: 560 }}
          emptyText="暂无成员"
          pagination={false}
        />
      </Modal>
    </>
  );
}

import {
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  Input,
  PageHeader,
  Table,
  toast,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { Plus } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";

import { useAuth } from "@/hooks/use-auth";
import { deleteUser, listUsers, type UserRecord } from "@/service/user";

import UserForm from "./form";

const DEFAULT_PAGE_SIZE = 10;
const PAGE_SIZE_OPTIONS = [10, 20, 50];

export default function UserManagementPage() {
  const { currentUser } = useAuth();
  const requestSequenceRef = useRef(0);
  const [records, setRecords] = useState<UserRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [total, setTotal] = useState(0);
  const [userName, setUserNameState] = useState("");
  const [realName, setRealNameState] = useState("");
  const [refreshVersion, setRefreshVersion] = useState(0);
  const [formOpen, setFormOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<UserRecord>();
  const [pendingDelete, setPendingDelete] = useState<UserRecord>();
  const [deleting, setDeleting] = useState(false);

  const refresh = useCallback(() => setRefreshVersion((value) => value + 1), []);

  const loadPage = useCallback(async () => {
    const requestSequence = requestSequenceRef.current + 1;
    requestSequenceRef.current = requestSequence;
    setLoading(true);

    try {
      const result = await listUsers({
        pageNo,
        pageSize,
        userName: userName.trim() || undefined,
        realName: realName.trim() || undefined,
      });
      if (requestSequence !== requestSequenceRef.current) return;

      const nextRecords = result?.bizData || [];
      const nextTotal = result?.pagination?.total || 0;
      if (nextRecords.length === 0 && nextTotal > 0 && pageNo > 1) {
        setPageNo((value) => Math.max(1, value - 1));
        return;
      }

      setRecords(nextRecords);
      setTotal(nextTotal);
    } finally {
      if (requestSequence === requestSequenceRef.current) setLoading(false);
    }
  }, [pageNo, pageSize, realName, refreshVersion, userName]);

  useEffect(() => {
    const timer = window.setTimeout(
      () => void loadPage(),
      userName.trim() || realName.trim() ? 300 : 0,
    );
    return () => window.clearTimeout(timer);
  }, [loadPage, realName, userName]);

  const openCreate = () => {
    setEditingRecord(undefined);
    setFormOpen(true);
  };

  const openEdit = (record: UserRecord) => {
    setEditingRecord(record);
    setFormOpen(true);
  };

  const confirmDelete = async () => {
    if (!pendingDelete?.id || deleting) return;
    setDeleting(true);
    try {
      await deleteUser(pendingDelete.id);
      toast.success("用户删除成功");
      setPendingDelete(undefined);
      refresh();
    } finally {
      setDeleting(false);
    }
  };

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
        <span className="font-medium text-[#252832]">{record.userName || "-"}</span>
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
        const deletingSelf = currentUser?.id === record.id;

        return (
          <div className="flex items-center justify-center gap-1">
            <Button
              variant="ghost"
              size="small"
              className="px-0.5 text-xs font-normal text-[#667085] hover:text-[var(--yak-color-primary)]"
              onClick={() => openEdit(record)}
            >
              编辑
            </Button>
            <span aria-hidden="true" className="h-3 w-px shrink-0 bg-[#e4e7ec]" />
            <Button
              variant="ghost"
              size="small"
              disabled={deletingSelf}
              title={deletingSelf ? "不能删除当前登录用户" : undefined}
              className="px-0.5 text-xs font-normal text-[#667085] hover:text-[#d92d20]"
              onClick={() => setPendingDelete(record)}
            >
              删除
            </Button>
          </div>
        );
      },
    },
  ];

  return (
    <>
      <div className="flex min-h-full flex-col bg-[#F6F6F6] text-[#242731]">
        <PageHeader title="用户管理" bordered className="bg-white px-6 max-md:px-4" />

        <div className="flex min-h-0 flex-1 px-6 pb-4 pt-5 max-md:px-4">
          <div className="flex min-h-0 flex-1 flex-col bg-white p-4">
            <section className="flex shrink-0 flex-wrap items-center gap-2">
              <Button size="small" variant="primary" onClick={openCreate}>
                <Plus size={14} />
                新增用户
              </Button>

              <div className="relative w-[260px]">
                <span className="pointer-events-none absolute left-2.5 top-1/2 z-10 -translate-y-1/2 text-[length:var(--yak-font-size-control-small)] text-[#4f5561]">
                  用户名
                </span>
                <Input
                  size="small"
                  variant="outlined"
                  value={userName}
                  className="pl-[62px]"
                  placeholder="请输入用户名"
                  onChange={(event) => {
                    setUserNameState(event.target.value);
                    setPageNo(1);
                  }}
                />
              </div>

              <div className="relative w-[260px]">
                <span className="pointer-events-none absolute left-2.5 top-1/2 z-10 -translate-y-1/2 text-[length:var(--yak-font-size-control-small)] text-[#4f5561]">
                  姓名
                </span>
                <Input
                  size="small"
                  variant="outlined"
                  value={realName}
                  className="pl-[50px]"
                  placeholder="请输入姓名"
                  onChange={(event) => {
                    setRealNameState(event.target.value);
                    setPageNo(1);
                  }}
                />
              </div>
            </section>

            <section className="mt-4 min-h-0 flex-1">
              <Table<UserRecord>
                className="min-h-full"
                columns={columns}
                dataSource={records}
                rowKey="id"
                loading={loading}
                bordered
                size="medium"
                scroll={{ x: 1120 }}
                emptyText={userName.trim() || realName.trim() ? "未找到匹配用户" : "暂无用户"}
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
                          setPageNo(nextPage);
                          setPageSize(nextPageSize);
                        },
                      }
                    : false
                }
              />
            </section>
          </div>
        </div>
      </div>

      <UserForm
        open={formOpen}
        record={editingRecord}
        onClose={() => setFormOpen(false)}
        onSaved={() => {
          toast.success(editingRecord ? "用户保存成功" : "用户创建成功");
          refresh();
        }}
      />

      <Dialog
        open={Boolean(pendingDelete)}
        onOpenChange={(open) => {
          if (!open && !deleting) setPendingDelete(undefined);
        }}
      >
        <DialogContent className="w-[420px]">
          <DialogTitle className="text-base font-semibold">删除用户</DialogTitle>
          <DialogDescription className="mt-2 text-sm leading-6 text-[#667085]">
            确定删除用户“{pendingDelete?.userName || "-"}”吗？删除后无法通过该账号登录。
          </DialogDescription>
          <div className="mt-6 flex justify-end gap-2">
            <Button size="small" disabled={deleting} onClick={() => setPendingDelete(undefined)}>
              取消
            </Button>
            <Button
              size="small"
              variant="danger"
              loading={deleting}
              onClick={() => void confirmDelete()}
            >
              删除
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}

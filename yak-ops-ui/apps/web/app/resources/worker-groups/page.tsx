import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Badge,
  Button,
  Combobox,
  ComboboxContent,
  ComboboxInput,
  ComboboxItem,
  ComboboxItemIndicator,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  Field,
  FieldError,
  FieldLabel,
  Input,
  PageHeader,
  Table,
  Textarea,
  toast,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { Pencil, Plus, RefreshCw, Trash2 } from "lucide-react";

import { useAuth } from "@/hooks/use-auth";
import {
  deleteWorkerGroup,
  listWorkerAddresses,
  listWorkerGroups,
  saveWorkerGroup,
  type WorkerGroupRecord,
} from "@/service/worker-group";

const PAGE_SIZE = 20;

const displayTime = (value?: string) => value || "-";

const splitAddresses = (value?: string | null) =>
  value
    ? value
        .split(",")
        .map((item) => item.trim())
        .filter(Boolean)
    : [];

export function WorkerGroupsPage() {
  const { currentUser } = useAuth();
  const queryClient = useQueryClient();

  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(PAGE_SIZE);
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<WorkerGroupRecord>();
  const [name, setName] = useState("");
  const [addresses, setAddresses] = useState<string[]>([]);
  const [description, setDescription] = useState("");
  const [nameError, setNameError] = useState("");
  const [saving, setSaving] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<WorkerGroupRecord>();
  const [deleting, setDeleting] = useState(false);

  const userId = currentUser?.id ?? "";

  const groupsQuery = useQuery({
    queryKey: ["resource", "worker-groups", userId, pageNo, pageSize, keyword],
    queryFn: ({ signal }) =>
      listWorkerGroups(
        { pageNo, pageSize, searchVal: keyword || undefined },
        signal,
      ),
    enabled: Boolean(userId),
  });

  const workerAddressesQuery = useQuery({
    queryKey: ["resource", "worker-addresses", userId],
    queryFn: ({ signal }) => listWorkerAddresses(signal),
    enabled: Boolean(userId) && formOpen,
  });

  const records = groupsQuery.data?.totalList ?? [];
  const total = groupsQuery.data?.total ?? 0;
  const availableAddresses = useMemo(
    () =>
      Array.from(
        new Set([
          ...(workerAddressesQuery.data ?? []),
          ...addresses,
        ]),
      ).sort(),
    [addresses, workerAddressesQuery.data],
  );

  const refreshGroups = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["resource", "worker-groups"] }),
      queryClient.invalidateQueries({ queryKey: ["resource", "worker-group-names"] }),
    ]);
  };

  const openCreate = () => {
    setEditing(undefined);
    setName("");
    setAddresses([]);
    setDescription("");
    setNameError("");
    setFormOpen(true);
  };

  const openEdit = (record: WorkerGroupRecord) => {
    setEditing(record);
    setName(record.name);
    setAddresses(splitAddresses(record.addrList));
    setDescription(record.description ?? "");
    setNameError("");
    setFormOpen(true);
  };

  const closeForm = () => {
    if (saving) return;
    setFormOpen(false);
  };

  const handleSave = async () => {
    const trimmedName = name.trim();
    if (!trimmedName) {
      setNameError("请输入 Worker 分组名称");
      return;
    }

    if (saving) return;

    setNameError("");
    setSaving(true);
    try {
      await saveWorkerGroup(editing?.id ?? 0, {
        name: trimmedName,
        addrList: addresses.join(","),
        description: description.trim(),
      });
      toast.success(editing ? "Worker 分组已保存" : "Worker 分组已创建");
      setFormOpen(false);
      await refreshGroups();
    } catch {
      // HTTP layer reports the backend error.
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget || deleting) return;

    setDeleting(true);
    try {
      await deleteWorkerGroup(deleteTarget.id);
      toast.success("Worker 分组已删除");
      setDeleteTarget(undefined);
      await refreshGroups();
    } catch {
      // HTTP layer reports dependency and permission errors.
    } finally {
      setDeleting(false);
    }
  };

  const applyKeyword = () => {
    setPageNo(1);
    setKeyword(keywordInput.trim());
  };

  const columns: TableColumns<WorkerGroupRecord> = [
    {
      key: "name",
      title: "Worker 分组",
      dataIndex: "name",
      minWidth: 180,
      render: (_value, record) => (
        <span className="font-medium text-[var(--yak-components-panel-text)]">
          {record.name}
        </span>
      ),
    },
    {
      key: "source",
      title: "来源",
      width: 100,
      render: (_value, record) =>
        record.systemDefault ? (
          <Badge tone="neutral">系统</Badge>
        ) : (
          <Badge tone="success">自定义</Badge>
        ),
    },
    {
      key: "addresses",
      title: "Worker 地址",
      minWidth: 260,
      ellipsis: true,
      render: (_value, record) => {
        const value = splitAddresses(record.addrList);
        return value.length > 0 ? value.join(", ") : "-";
      },
    },
    {
      key: "description",
      title: "描述",
      minWidth: 220,
      ellipsis: true,
      render: (_value, record) => record.description || "-",
    },
    {
      key: "createTime",
      title: "创建时间",
      width: 180,
      render: (_value, record) => displayTime(record.createTime),
    },
    {
      key: "actions",
      title: "操作",
      width: 150,
      align: "center",
      render: (_value, record) =>
        record.systemDefault ? (
          <span className="text-xs text-[var(--yak-components-muted-text)]">系统分组</span>
        ) : (
          <div className="flex items-center justify-center gap-1">
            <Button
              size="sm"
              variant="ghost"
              className="px-1 text-xs font-normal"
              onClick={() => openEdit(record)}
            >
              <Pencil size={13} />
              编辑
            </Button>
            <Button
              size="sm"
              variant="ghost"
              className="px-1 text-xs font-normal text-[var(--yak-color-danger)]"
              onClick={() => setDeleteTarget(record)}
            >
              <Trash2 size={13} />
              删除
            </Button>
          </div>
        ),
    },
  ];

  return (
    <>
      <div className="flex min-h-full flex-col bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
        <PageHeader
          title="Worker 分组"
          bordered
          className="bg-white px-6 max-md:px-4"
          extra={
            <Button size="sm" variant="primary" onClick={openCreate}>
              <Plus size={14} />
              新建 Worker 分组
            </Button>
          }
        />

        <div className="flex min-h-0 flex-1 px-6 pb-4 pt-5 max-md:px-4">
          <div className="flex min-h-0 flex-1 flex-col bg-white p-4">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
              <div className="text-xs text-[var(--yak-components-muted-text)]">
                Worker 分组用于指定任务的执行 Worker。系统分组来自当前在线 Worker，不能在这里编辑。
              </div>
              <div className="flex items-center gap-2">
                <div className="w-[260px] max-w-full">
                  <Input
                    size="small"
                    variant="outlined"
                    value={keywordInput}
                    placeholder="搜索 Worker 分组"
                    onChange={(event) => setKeywordInput(event.target.value)}
                    onKeyDown={(event) => {
                      if (event.key === "Enter") applyKeyword();
                    }}
                  />
                </div>
                <Button size="sm" variant="secondary" onClick={applyKeyword}>
                  搜索
                </Button>
                <Button
                  size="sm"
                  variant="secondary"
                  aria-label="刷新 Worker 分组"
                  onClick={() => void groupsQuery.refetch()}
                >
                  <RefreshCw size={14} />
                </Button>
              </div>
            </div>

            {groupsQuery.isError ? (
              <div role="alert" className="mb-3 flex items-center gap-3 text-sm">
                Worker 分组加载失败
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={() => void groupsQuery.refetch()}
                >
                  重试
                </Button>
              </div>
            ) : null}

            <section className="min-h-0 flex-1">
              <Table<WorkerGroupRecord>
                className="min-h-full"
                columns={columns}
                dataSource={records}
                rowKey={(record) =>
                  record.systemDefault
                    ? "system-" + record.name
                    : String(record.id)
                }
                loading={groupsQuery.isPending || groupsQuery.isFetching}
                bordered
                size="medium"
                scroll={{ x: 1100 }}
                emptyText={keyword ? "没有找到匹配的 Worker 分组" : "暂无 Worker 分组"}
                pagination={
                  total > 0
                    ? {
                        current: pageNo,
                        pageSize,
                        total,
                        showSizeChanger: true,
                        pageSizeOptions: [10, 20, 50],
                        onChange: (page, nextPageSize) => {
                          setPageNo(page);
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

      <Dialog
        open={formOpen}
        onOpenChange={(open) => {
          if (!open) closeForm();
        }}
      >
        <DialogContent className="w-[620px] max-w-[calc(100vw-32px)]">
          <DialogTitle className="text-base font-semibold">
            {editing ? "编辑 Worker 分组" : "新建 Worker 分组"}
          </DialogTitle>

          <div className="mt-5 space-y-4">
            <Field
              className="grid grid-cols-[112px_minmax(0,1fr)] items-start gap-3"
              invalid={Boolean(nameError)}
            >
              <FieldLabel
                required
                htmlFor="worker-group-name"
                className="pt-1.5 text-xs"
              >
                Worker 分组
              </FieldLabel>
              <div className="min-w-0">
                <Input
                  id="worker-group-name"
                  size="small"
                  variant="outlined"
                  value={name}
                  maxLength={128}
                  placeholder="请输入 Worker 分组名称"
                  aria-invalid={Boolean(nameError) || undefined}
                  onChange={(event) => {
                    setName(event.target.value);
                    setNameError("");
                  }}
                />
                <FieldError match={Boolean(nameError)} className="mt-1">
                  {nameError}
                </FieldError>
              </div>
            </Field>

            <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-start gap-3">
              <FieldLabel className="pt-1.5 text-xs">
                Worker 地址
              </FieldLabel>
              <div className="min-w-0">
                <Combobox<string, true>
                  multiple
                  size="small"
                  items={availableAddresses}
                  value={addresses}
                  disabled={workerAddressesQuery.isPending}
                  onValueChange={setAddresses}
                  onOpenChange={(open, eventDetails) => {
                    if (!open && eventDetails.reason === "item-press") {
                      eventDetails.cancel();
                    }
                  }}
                >
                  <ComboboxInput
                    variant="outlined"
                    aria-label="Worker 地址"
                    placeholder={
                      workerAddressesQuery.isPending
                        ? "正在加载 Worker 地址..."
                        : availableAddresses.length > 0
                          ? "选择 Worker 地址"
                          : "当前没有在线 Worker"
                    }
                  />
                  <ComboboxContent emptyText="没有匹配的 Worker 地址">
                    {(item: string) => (
                      <ComboboxItem key={item} value={item}>
                        <span className="min-w-0 flex-1 truncate">{item}</span>
                        <ComboboxItemIndicator />
                      </ComboboxItem>
                    )}
                  </ComboboxContent>
                </Combobox>
                <div className="mt-1.5 text-[11px] leading-5 text-[var(--yak-components-muted-text)]">
                  只展示当前在线 Worker 地址；最终地址合法性由后端校验。
                </div>
              </div>
            </Field>

            <Field className="grid grid-cols-[112px_minmax(0,1fr)] items-start gap-3">
              <FieldLabel
                htmlFor="worker-group-description"
                className="pt-1.5 text-xs"
              >
                描述
              </FieldLabel>
              <Textarea
                id="worker-group-description"
                size="small"
                variant="outlined"
                rows={3}
                maxLength={500}
                value={description}
                className="min-h-[76px] resize-none"
                placeholder="请输入 Worker 分组描述"
                onValueChange={(value) => setDescription(String(value || ""))}
              />
            </Field>
          </div>

          <div className="mt-6 flex justify-end gap-2">
            <Button size="sm" variant="secondary" isDisabled={saving} onClick={closeForm}>
              取消
            </Button>
            <Button
              size="sm"
              variant="primary"
              isPending={saving}
              onClick={() => void handleSave()}
            >
              {editing ? "保存" : "创建"}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      <Dialog
        open={Boolean(deleteTarget)}
        onOpenChange={(open) => {
          if (!open && !deleting) setDeleteTarget(undefined);
        }}
      >
        <DialogContent className="w-[440px] max-w-[calc(100vw-32px)]">
          <DialogTitle className="text-base font-semibold">删除 Worker 分组</DialogTitle>
          <DialogDescription className="mt-3 text-sm leading-6 text-[var(--yak-components-muted-text)]">
            确定删除 Worker 分组“{deleteTarget?.name}”吗？如果该分组仍被任务、调度或环境使用，后端会拒绝删除。
          </DialogDescription>
          <div className="mt-6 flex justify-end gap-2">
            <Button
              size="sm"
              variant="secondary"
              isDisabled={deleting}
              onClick={() => setDeleteTarget(undefined)}
            >
              取消
            </Button>
            <Button
              size="sm"
              variant="danger"
              isPending={deleting}
              onClick={() => void handleDelete()}
            >
              删除
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}

export default WorkerGroupsPage;

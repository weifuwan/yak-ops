import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Badge,
  Button,
  Combobox,
  ComboboxChip,
  ComboboxChipRemove,
  ComboboxChips,
  ComboboxContent,
  ComboboxInput,
  ComboboxInputGroup,
  ComboboxItem,
  ComboboxItemIndicator,
  ComboboxValue,
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
import { Pencil, Plus, RefreshCw, Trash2, X } from "lucide-react";

import { useAuth } from "@/hooks/use-auth";
import {
  createEnvironment,
  deleteEnvironment,
  listEnvironments,
  updateEnvironment,
  type EnvironmentRecord,
} from "@/service/environment";
import { listWorkerGroupNames } from "@/service/worker-group";

import { EnvironmentConfigEditor } from "./components/environment-config-editor";

const PAGE_SIZE = 20;

const displayTime = (value?: string) => value || "-";

export function EnvironmentsPage() {
  const { currentUser } = useAuth();
  const queryClient = useQueryClient();

  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(PAGE_SIZE);
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<EnvironmentRecord>();
  const [name, setName] = useState("");
  const [config, setConfig] = useState("");
  const [description, setDescription] = useState("");
  const [workerGroups, setWorkerGroups] = useState<string[]>([]);
  const [nameError, setNameError] = useState("");
  const [configError, setConfigError] = useState("");
  const [saving, setSaving] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<EnvironmentRecord>();
  const [deleting, setDeleting] = useState(false);

  const userId = currentUser?.id ?? "";

  const environmentsQuery = useQuery({
    queryKey: ["resource", "environments", userId, pageNo, pageSize, keyword],
    queryFn: ({ signal }) =>
      listEnvironments(
        { pageNo, pageSize, searchVal: keyword || undefined },
        signal,
      ),
    enabled: Boolean(userId),
  });

  const workerGroupsQuery = useQuery({
    queryKey: ["resource", "worker-group-names", userId],
    queryFn: ({ signal }) => listWorkerGroupNames(signal),
    enabled: Boolean(userId) && formOpen,
  });

  const records = environmentsQuery.data?.totalList ?? [];
  const total = environmentsQuery.data?.total ?? 0;
  const availableWorkerGroups = useMemo(
    () =>
      Array.from(
        new Set([
          ...(workerGroupsQuery.data ?? []),
          ...workerGroups,
        ]),
      ).sort(),
    [workerGroups, workerGroupsQuery.data],
  );

  const refreshEnvironments = async () => {
    await queryClient.invalidateQueries({ queryKey: ["resource", "environments"] });
  };

  const openCreate = () => {
    setEditing(undefined);
    setName("");
    setConfig("");
    setDescription("");
    setWorkerGroups([]);
    setNameError("");
    setConfigError("");
    setFormOpen(true);
  };

  const openEdit = (record: EnvironmentRecord) => {
    setEditing(record);
    setName(record.name);
    setConfig(record.config);
    setDescription(record.description ?? "");
    setWorkerGroups(record.workerGroups ?? []);
    setNameError("");
    setConfigError("");
    setFormOpen(true);
  };

  const closeForm = () => {
    if (saving) return;
    setFormOpen(false);
  };

  const handleSave = async () => {
    const trimmedName = name.trim();
    const trimmedConfig = config.trim();

    let valid = true;
    if (!trimmedName) {
      setNameError("请输入环境名称");
      valid = false;
    } else {
      setNameError("");
    }

    if (!trimmedConfig) {
      setConfigError("请输入环境配置");
      valid = false;
    } else {
      setConfigError("");
    }

    if (!valid || saving) return;

    setSaving(true);
    try {
      const payload = {
        name: trimmedName,
        config: config,
        description: description.trim(),
        workerGroups,
      };

      if (editing) {
        await updateEnvironment(editing.code, payload);
        toast.success("环境已保存");
      } else {
        await createEnvironment(payload);
        toast.success("环境已创建");
      }

      setFormOpen(false);
      await refreshEnvironments();
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
      await deleteEnvironment(deleteTarget.code);
      toast.success("环境已删除");
      setDeleteTarget(undefined);
      await refreshEnvironments();
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

  const columns: TableColumns<EnvironmentRecord> = [
    {
      key: "name",
      title: "环境名称",
      dataIndex: "name",
      minWidth: 190,
      render: (_value, record) => (
        <span className="font-medium text-[var(--yak-components-panel-text)]">
          {record.name}
        </span>
      ),
    },
    {
      key: "code",
      title: "环境 Code",
      width: 190,
      render: (_value, record) => String(record.code),
    },
    {
      key: "workerGroups",
      title: "Worker 分组",
      minWidth: 250,
      ellipsis: true,
      render: (_value, record) =>
        record.workerGroups?.length ? record.workerGroups.join(", ") : "-",
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
      render: (_value, record) => (
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
          title="环境管理"
          bordered
          className="bg-white px-6 max-md:px-4"
          extra={
            <Button size="sm" variant="primary" onClick={openCreate}>
              <Plus size={14} />
              新建环境
            </Button>
          }
        />

        <div className="flex min-h-0 flex-1 px-6 pb-4 pt-5 max-md:px-4">
          <div className="flex min-h-0 flex-1 flex-col bg-white p-4">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
              <div className="flex items-center gap-2">
                <div className="w-[260px] max-w-full">
                  <Input
                    size="small"
                    variant="outlined"
                    value={keywordInput}
                    placeholder="搜索环境名称"
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
                  aria-label="刷新环境"
                  onClick={() => void environmentsQuery.refetch()}
                >
                  <RefreshCw size={14} />
                </Button>
              </div>
            </div>

            {environmentsQuery.isError ? (
              <div role="alert" className="mb-3 flex items-center gap-3 text-sm">
                环境列表加载失败
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={() => void environmentsQuery.refetch()}
                >
                  重试
                </Button>
              </div>
            ) : null}

            <section className="min-h-0 flex-1">
              <Table<EnvironmentRecord>
                className="min-h-full"
                columns={columns}
                dataSource={records}
                rowKey={(record) => String(record.code)}
                loading={environmentsQuery.isPending || environmentsQuery.isFetching}
                bordered
                size="medium"
                scroll={{ x: 1100 }}
                emptyText={keyword ? "没有找到匹配的环境" : "暂无环境"}
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
        <DialogContent className="w-[860px] max-w-[calc(100vw-32px)]">
          <DialogTitle className="text-base font-semibold">
            {editing ? "编辑环境" : "新建环境"}
          </DialogTitle>

          <div className="mt-5 space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <Field invalid={Boolean(nameError)}>
                <FieldLabel required htmlFor="environment-name" className="text-xs">
                  环境名称
                </FieldLabel>
                <Input
                  id="environment-name"
                  size="small"
                  variant="outlined"
                  value={name}
                  maxLength={128}
                  placeholder="请输入环境名称"
                  aria-invalid={Boolean(nameError) || undefined}
                  onChange={(event) => {
                    setName(event.target.value);
                    setNameError("");
                  }}
                />
                <FieldError match={Boolean(nameError)} className="mt-1">
                  {nameError}
                </FieldError>
              </Field>

              <Field>
                <FieldLabel className="text-xs">Worker 分组</FieldLabel>
                <Combobox<string, true>
                  multiple
                  size="small"
                  items={availableWorkerGroups}
                  value={workerGroups}
                  disabled={workerGroupsQuery.isPending}
                  onValueChange={setWorkerGroups}
                  onOpenChange={(open, eventDetails) => {
                    if (!open && eventDetails.reason === "item-press") {
                      eventDetails.cancel();
                    }
                  }}
                >
                  <ComboboxInputGroup className="min-h-7 w-full rounded-[var(--yak-radius-control-small)] border border-[var(--yak-components-input-border)] bg-[var(--yak-components-input-bg-focus)] px-2 transition-[border-color,background-color] duration-150 focus-within:border-[var(--yak-components-input-border-focus)] focus-within:bg-[var(--yak-components-input-bg-focus)] data-disabled:cursor-not-allowed data-disabled:bg-[var(--yak-components-input-bg-disabled)]">
                    <ComboboxValue>
                      {(selectedValues: string[]) => (
                        <ComboboxChips
                          aria-label={selectedValues.length > 0 ? "已选择的 Worker 分组" : undefined}
                          className="flex min-w-0 flex-1 flex-wrap items-center gap-1 py-0.5"
                        >
                          {selectedValues.map((item) => (
                            <ComboboxChip
                              key={item}
                              aria-label={item}
                              aria-description="按 Backspace 或 Delete 可移除"
                              className="inline-flex max-w-[calc(100%-8px)] items-center gap-0.5 rounded-[4px] bg-[var(--yak-color-hover)] px-1.5 py-0.5 text-[11px] leading-4 text-[var(--yak-components-panel-text)]"
                            >
                              <span className="min-w-0 truncate">{item}</span>
                              <ComboboxChipRemove
                                aria-label={`移除 ${item}`}
                                className="flex size-3.5 shrink-0 items-center justify-center rounded-[3px] text-[var(--yak-components-muted-text)] hover:bg-black/5 hover:text-[var(--yak-components-panel-text)]"
                              >
                                <X size={11} strokeWidth={1.8} />
                              </ComboboxChipRemove>
                            </ComboboxChip>
                          ))}
                          <ComboboxInput
                            variant="outlined"
                            aria-label="Worker 分组"
                            aria-description={
                              selectedValues.length > 0
                                ? `已选择 ${selectedValues.length} 个 Worker 分组，可使用 Backspace 或 Delete 移除`
                                : undefined
                            }
                            placeholder={
                              selectedValues.length > 0
                                ? ""
                                : workerGroupsQuery.isPending
                                  ? "正在加载 Worker 分组..."
                                  : availableWorkerGroups.length > 0
                                    ? "选择 Worker 分组"
                                    : "暂无可用 Worker 分组"
                            }
                            className="min-w-[72px] flex-1 border-0! bg-transparent! px-0! shadow-none! focus:bg-transparent! focus:shadow-none!"
                          />
                        </ComboboxChips>
                      )}
                    </ComboboxValue>
                  </ComboboxInputGroup>
                  <ComboboxContent emptyText="没有匹配的 Worker 分组">
                    {(item: string) => (
                      <ComboboxItem key={item} value={item}>
                        <span className="min-w-0 flex-1 truncate">{item}</span>
                        <ComboboxItemIndicator />
                      </ComboboxItem>
                    )}
                  </ComboboxContent>
                </Combobox>
              </Field>
            </div>

            <Field invalid={Boolean(configError)}>
              <FieldLabel required className="text-xs">
                环境配置
              </FieldLabel>
              <div className="overflow-hidden rounded-[var(--yak-radius-control-medium)] border border-[var(--yak-components-input-border)] bg-white">
                <EnvironmentConfigEditor
                  value={config}
                  readOnly={saving}
                  onChange={(value) => {
                    setConfig(value);
                    setConfigError("");
                  }}
                />
              </div>
              <FieldError match={Boolean(configError)} className="mt-1">
                {configError}
              </FieldError>
            </Field>

            <Field>
              <FieldLabel htmlFor="environment-description" className="text-xs">
                描述
              </FieldLabel>
              <Textarea
                id="environment-description"
                size="small"
                variant="outlined"
                rows={3}
                maxLength={500}
                value={description}
                className="min-h-[76px] resize-none"
                placeholder="请输入环境描述"
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
          <DialogTitle className="text-base font-semibold">删除环境</DialogTitle>
          <DialogDescription className="mt-3 text-sm leading-6 text-[var(--yak-components-muted-text)]">
            确定删除环境“{deleteTarget?.name}”吗？如果仍有任务使用该环境，后端会拒绝删除。
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

export default EnvironmentsPage;

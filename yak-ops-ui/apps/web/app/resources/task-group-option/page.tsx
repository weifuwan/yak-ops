import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import {
  Badge,
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
  Field,
  FieldError,
  FieldLabel,
  Input,
  PageHeader,
  Switch,
  Table,
  Textarea,
  toast,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { Plus } from "lucide-react";

import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import {
  closeTaskGroup,
  createTaskGroup,
  isTaskGroupEnabled,
  listTaskGroups,
  startTaskGroup,
  updateTaskGroup,
  type TaskGroupRecord,
} from "@/service/task-group";

const PAGE_SIZE = 20;

const displayTime = (value?: string) => value || "-";

export function TaskGroupConfigurationPage() {
  const { currentUser } = useAuth();
  const { workspaces, currentWorkspace } = useWorkspace();
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(PAGE_SIZE);
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<TaskGroupRecord>();
  const [name, setName] = useState("");
  const [groupSize, setGroupSize] = useState("1");
  const [description, setDescription] = useState("");
  const [nameError, setNameError] = useState("");
  const [groupSizeError, setGroupSizeError] = useState("");
  const [saving, setSaving] = useState(false);
  const [changingStatusId, setChangingStatusId] = useState<number>();

  const userId = currentUser?.id ?? "";
  const groupsQuery = useQuery({
    queryKey: ["resource", "task-groups", userId, pageNo, pageSize, keyword],
    queryFn: ({ signal }) =>
      listTaskGroups(
        { pageNo, pageSize, name: keyword || undefined },
        signal,
      ),
    enabled: Boolean(userId),
  });

  const records = groupsQuery.data?.totalList ?? [];
  const total = groupsQuery.data?.total ?? 0;
  const workspaceNameByCode = useMemo(
    () => new Map(workspaces.map((workspace) => [String(workspace.id), workspace.name])),
    [workspaces],
  );

  const refreshGroups = async () => {
    await queryClient.invalidateQueries({ queryKey: ["resource", "task-groups"] });
  };

  const openCreate = () => {
    setEditing(undefined);
    setName("");
    setGroupSize("1");
    setDescription("");
    setNameError("");
    setGroupSizeError("");
    setFormOpen(true);
  };

  const openEdit = (record: TaskGroupRecord) => {
    setEditing(record);
    setName(record.name);
    setGroupSize(String(record.groupSize));
    setDescription(record.description ?? "");
    setNameError("");
    setGroupSizeError("");
    setFormOpen(true);
  };

  const handleSave = async () => {
    const trimmedName = name.trim();
    const parsedGroupSize = Number(groupSize);
    let valid = true;

    if (!trimmedName) {
      setNameError("请输入任务组名称");
      valid = false;
    } else {
      setNameError("");
    }

    if (!Number.isSafeInteger(parsedGroupSize) || parsedGroupSize <= 0) {
      setGroupSizeError("资源容量必须为大于 0 的整数");
      valid = false;
    } else {
      setGroupSizeError("");
    }

    if (!valid || saving) return;
    if (!editing && !currentWorkspace?.id) return;

    setSaving(true);
    try {
      const payload = {
        name: trimmedName,
        description: description.trim(),
        groupSize: parsedGroupSize,
      };
      if (editing) {
        await updateTaskGroup(editing.id, payload);
        toast.success("任务组已保存");
      } else {
        await createTaskGroup({ ...payload, projectCode: currentWorkspace!.id });
        toast.success("任务组已创建");
      }
      setFormOpen(false);
      await refreshGroups();
    } catch {
      // The HTTP layer reports the API error; keep the form open for correction.
    } finally {
      setSaving(false);
    }
  };

  const handleStatusChange = async (record: TaskGroupRecord, enabled: boolean) => {
    if (changingStatusId != null) return;
    setChangingStatusId(record.id);
    try {
      if (enabled) {
        await startTaskGroup(record.id);
        toast.success("任务组已启用");
      } else {
        await closeTaskGroup(record.id);
        toast.success("任务组已停用");
      }
      await refreshGroups();
    } catch {
      // Keep the last confirmed state and let the HTTP layer report the error.
    } finally {
      setChangingStatusId(undefined);
    }
  };

  const columns: TableColumns<TaskGroupRecord> = [
    {
      key: "name",
      title: "任务组名称",
      dataIndex: "name",
      minWidth: 190,
      render: (_value, record) => (
        <span className="font-medium text-[var(--yak-components-panel-text)]">
          {record.name}
        </span>
      ),
    },
    {
      key: "project",
      title: "所属工作空间",
      width: 180,
      render: (_value, record) => {
        const projectCode = String(record.projectCode);
        return (
          workspaceNameByCode.get(projectCode) ||
          (projectCode === "0" ? "默认资源组" : "#" + projectCode)
        );
      },
    },
    {
      key: "capacity",
      title: "资源容量",
      width: 110,
      align: "right",
      render: (_value, record) => record.groupSize,
    },
    {
      key: "used",
      title: "已用资源",
      width: 110,
      align: "right",
      render: (_value, record) => record.useSize ?? 0,
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
      key: "status",
      title: "状态",
      width: 100,
      render: (_value, record) =>
        isTaskGroupEnabled(record.status) ? (
          <Badge tone="success">已启用</Badge>
        ) : (
          <Badge tone="neutral">已停用</Badge>
        ),
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
      width: 190,
      align: "center",
      render: (_value, record) => {
        const enabled = isTaskGroupEnabled(record.status);
        return (
          <div className="flex items-center justify-center gap-2">
            <Switch
              size="small"
              checked={enabled}
              loading={changingStatusId === record.id}
              aria-label={enabled ? "停用任务组" : "启用任务组"}
              onCheckedChange={(checked) => void handleStatusChange(record, checked)}
            />
            <Button
              size="sm"
              variant="ghost"
              className="px-1 text-xs font-normal"
              isDisabled={!enabled}
              title={!enabled ? "已停用的任务组不可编辑" : undefined}
              onClick={() => openEdit(record)}
            >
              编辑
            </Button>
            <Button
              size="sm"
              variant="ghost"
              className="px-1 text-xs font-normal"
              onClick={() => navigate("/resources/task-group-queue?groupId=" + record.id)}
            >
              查看队列
            </Button>
          </div>
        );
      },
    },
  ];

  const applyKeyword = () => {
    setPageNo(1);
    setKeyword(keywordInput.trim());
  };

  return (
    <>
      <div className="flex min-h-full flex-col bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
        <PageHeader
          title="任务组配置"
          bordered
          className="bg-white px-6 max-md:px-4"
          extra={
            <Button
              size="sm"
              variant="primary"
              isDisabled={!currentWorkspace}
              onClick={openCreate}
            >
              <Plus size={14} />
              新建任务组
            </Button>
          }
        />
        <div className="flex min-h-0 flex-1 px-6 pb-4 pt-5 max-md:px-4">
          <div className="flex min-h-0 flex-1 flex-col bg-white p-4">
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
              <div className="text-xs text-[var(--yak-components-muted-text)]">
                当前工作空间：{currentWorkspace?.name || "未选择"}
                {!currentWorkspace ? "，请先创建或选择工作空间后再创建任务组。" : ""}
              </div>
              <div className="flex items-center gap-2">
                <div className="w-[260px] max-w-full">
                  <Input
                    size="small"
                    variant="outlined"
                    value={keywordInput}
                    placeholder="搜索任务组名称"
                    onChange={(event) => setKeywordInput(event.target.value)}
                    onKeyDown={(event) => {
                      if (event.key === "Enter") applyKeyword();
                    }}
                  />
                </div>
                <Button size="sm" variant="secondary" onClick={applyKeyword}>
                  搜索
                </Button>
              </div>
            </div>

            {groupsQuery.isError ? (
              <div role="alert" className="mb-3 flex items-center gap-3 text-sm">
                任务组列表加载失败
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
              <Table<TaskGroupRecord>
                className="min-h-full"
                columns={columns}
                dataSource={records}
                rowKey="id"
                loading={groupsQuery.isPending || groupsQuery.isFetching}
                bordered
                size="medium"
                scroll={{ x: 1250 }}
                emptyText={keyword ? "没有找到匹配的任务组" : "暂无任务组"}
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
          if (!open && !saving) setFormOpen(false);
        }}
      >
        <DialogContent className="w-[540px]">
          <DialogTitle className="text-base font-semibold">
            {editing ? "编辑任务组" : "新建任务组"}
          </DialogTitle>
          <DialogDescription className="mt-1 text-xs leading-5 text-[var(--yak-components-muted-text)]">
            配置任务组的并发资源容量，任务运行时会按任务组规则申请资源。
          </DialogDescription>

          <div className="mt-5 space-y-4">
            {!editing ? (
              <div className="grid grid-cols-[104px_minmax(0,1fr)] gap-3 text-xs">
                <span className="pt-1.5 text-[var(--yak-components-muted-text)]">
                  所属工作空间
                </span>
                <span className="pt-1.5 font-medium">
                  {currentWorkspace?.name || "未选择工作空间"}
                </span>
              </div>
            ) : null}

            <Field
              className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3"
              invalid={Boolean(nameError)}
            >
              <FieldLabel
                required
                htmlFor="task-group-name"
                className="pt-1.5 text-xs"
              >
                任务组名称
              </FieldLabel>
              <div className="min-w-0">
                <Input
                  id="task-group-name"
                  size="small"
                  variant="outlined"
                  value={name}
                  maxLength={128}
                  placeholder="请输入任务组名称"
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

            <Field
              className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3"
              invalid={Boolean(groupSizeError)}
            >
              <FieldLabel
                required
                htmlFor="task-group-size"
                className="pt-1.5 text-xs"
              >
                资源容量
              </FieldLabel>
              <div className="min-w-0">
                <Input
                  id="task-group-size"
                  type="number"
                  min={1}
                  step={1}
                  size="small"
                  variant="outlined"
                  value={groupSize}
                  aria-invalid={Boolean(groupSizeError) || undefined}
                  placeholder="请输入正整数"
                  onChange={(event) => {
                    setGroupSize(event.target.value);
                    setGroupSizeError("");
                  }}
                />
                <FieldError match={Boolean(groupSizeError)} className="mt-1">
                  {groupSizeError}
                </FieldError>
                <div className="mt-1 text-[11px] text-[var(--yak-components-muted-text)]">
                  表示该任务组允许同时占用的资源槽位数量。
                </div>
              </div>
            </Field>

            <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-start gap-3">
              <FieldLabel
                htmlFor="task-group-description"
                className="pt-1.5 text-xs"
              >
                描述
              </FieldLabel>
              <Textarea
                id="task-group-description"
                size="small"
                variant="outlined"
                rows={3}
                maxLength={500}
                value={description}
                className="min-h-[76px] resize-none"
                placeholder="请输入任务组描述"
                onValueChange={(value) => setDescription(String(value || ""))}
              />
            </Field>
          </div>

          <div className="mt-6 flex justify-end gap-2">
            <Button
              size="sm"
              variant="secondary"
              isDisabled={saving}
              onClick={() => setFormOpen(false)}
            >
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
    </>
  );
}

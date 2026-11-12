import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useSearchParams } from "react-router-dom";
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
import { Pencil, Play, RefreshCw } from "lucide-react";

import { useAuth } from "@/hooks/use-auth";
import {
  forceStartTaskGroupQueue,
  listTaskGroupQueue,
  listTaskGroups,
  modifyTaskGroupQueuePriority,
  type TaskGroupQueueRecord,
  type TaskGroupRecord,
} from "@/service/task-group";

const PAGE_SIZE = 20;

type QueueFilters = {
  groupId: string;
  workflowInstanceName: string;
  taskInstanceName: string;
};

const QUEUE_STATUS: Record<
  string,
  { label: string; tone: "neutral" | "success" | "warning" }
> = {
  WAIT_QUEUE: { label: "等待入队", tone: "warning" },
  ACQUIRE_SUCCESS: { label: "已获取资源", tone: "success" },
  RELEASE: { label: "已释放", tone: "neutral" },
  "-1": { label: "等待入队", tone: "warning" },
  "1": { label: "已获取资源", tone: "success" },
  "2": { label: "已释放", tone: "neutral" },
};

const isFlagEnabled = (value: number | string) => Number(value) === 1;
const displayTime = (value?: string) => value || "-";

function QueueStatusBadge({ status }: { status: TaskGroupQueueRecord["status"] }) {
  const item = QUEUE_STATUS[String(status)] ?? {
    label: String(status),
    tone: "neutral" as const,
  };
  return <Badge tone={item.tone}>{item.label}</Badge>;
}

export function TaskGroupQueuePage() {
  const { currentUser } = useAuth();
  const [searchParams] = useSearchParams();
  const queryClient = useQueryClient();

  const initialGroupId = searchParams.get("groupId") ?? "ALL";
  const [pageNo, setPageNo] = useState(1);
  const [pageSize, setPageSize] = useState(PAGE_SIZE);
  const [groupIdInput, setGroupIdInput] = useState(initialGroupId);
  const [workflowNameInput, setWorkflowNameInput] = useState("");
  const [taskNameInput, setTaskNameInput] = useState("");
  const [filters, setFilters] = useState<QueueFilters>({
    groupId: initialGroupId,
    workflowInstanceName: "",
    taskInstanceName: "",
  });
  const [priorityRecord, setPriorityRecord] = useState<TaskGroupQueueRecord>();
  const [priorityValue, setPriorityValue] = useState("0");
  const [priorityError, setPriorityError] = useState("");
  const [savingPriority, setSavingPriority] = useState(false);
  const [startingQueueId, setStartingQueueId] = useState<number>();

  const userId = currentUser?.id ?? "";
  const groupsQuery = useQuery({
    queryKey: ["resource", "task-group-options", userId],
    queryFn: ({ signal }) => listTaskGroups({ pageNo: 1, pageSize: 1000 }, signal),
    enabled: Boolean(userId),
  });
  const queueQuery = useQuery({
    queryKey: [
      "resource",
      "task-group-queue",
      userId,
      pageNo,
      pageSize,
      filters.groupId,
      filters.workflowInstanceName,
      filters.taskInstanceName,
    ],
    queryFn: ({ signal }) =>
      listTaskGroupQueue(
        {
          pageNo,
          pageSize,
          groupId: filters.groupId === "ALL" ? -1 : Number(filters.groupId),
          workflowInstanceName: filters.workflowInstanceName || undefined,
          taskInstanceName: filters.taskInstanceName || undefined,
        },
        signal,
      ),
    enabled: Boolean(userId),
  });

  const groups = groupsQuery.data?.totalList ?? [];
  const records = queueQuery.data?.totalList ?? [];
  const total = queueQuery.data?.total ?? 0;
  const groupNameById = useMemo(
    () => new Map(groups.map((group: TaskGroupRecord) => [group.id, group.name])),
    [groups],
  );
  const groupOptions = useMemo(
    () => ({
      ALL: "全部任务组",
      ...Object.fromEntries(groups.map((group) => [String(group.id), group.name])),
    }),
    [groups],
  );

  const refreshQueue = async () => {
    await queryClient.invalidateQueries({ queryKey: ["resource", "task-group-queue"] });
  };

  const applyFilters = () => {
    setPageNo(1);
    setFilters({
      groupId: groupIdInput || "ALL",
      workflowInstanceName: workflowNameInput.trim(),
      taskInstanceName: taskNameInput.trim(),
    });
  };

  const openPriorityEditor = (record: TaskGroupQueueRecord) => {
    setPriorityRecord(record);
    setPriorityValue(String(record.priority ?? 0));
    setPriorityError("");
  };

  const savePriority = async () => {
    const priority = Number(priorityValue);
    if (!Number.isSafeInteger(priority) || priority < 0) {
      setPriorityError("优先级必须为大于等于 0 的整数");
      return;
    }
    if (!priorityRecord || savingPriority) return;

    setSavingPriority(true);
    try {
      await modifyTaskGroupQueuePriority(priorityRecord.id, priority);
      toast.success("任务优先级已更新");
      setPriorityRecord(undefined);
      await refreshQueue();
    } catch {
      // The HTTP layer reports the API error; keep the dialog open.
    } finally {
      setSavingPriority(false);
    }
  };

  const forceStart = async (record: TaskGroupQueueRecord) => {
    if (startingQueueId != null) return;
    setStartingQueueId(record.id);
    try {
      await forceStartTaskGroupQueue(record.id);
      toast.success("已提交强制启动请求");
      await refreshQueue();
    } catch {
      // The HTTP layer reports the API error.
    } finally {
      setStartingQueueId(undefined);
    }
  };

  const columns: TableColumns<TaskGroupQueueRecord> = [
    {
      key: "project",
      title: "项目",
      dataIndex: "projectName",
      width: 150,
      render: (_value, record) => record.projectName || "-",
    },
    {
      key: "task",
      title: "任务名称",
      dataIndex: "taskName",
      minWidth: 190,
      render: (_value, record) => (
        <span className="font-medium text-[var(--yak-components-panel-text)]">
          {record.taskName || "-"}
        </span>
      ),
    },
    {
      key: "workflow",
      title: "工作流实例",
      dataIndex: "workflowInstanceName",
      minWidth: 190,
      render: (_value, record) => record.workflowInstanceName || "-",
    },
    {
      key: "group",
      title: "任务组",
      width: 160,
      render: (_value, record) =>
        groupNameById.get(record.groupId) || "#" + record.groupId,
    },
    {
      key: "priority",
      title: "组内优先级",
      width: 110,
      align: "right",
      render: (_value, record) => record.priority ?? 0,
    },
    {
      key: "forceStart",
      title: "强制启动",
      width: 100,
      render: (_value, record) => (isFlagEnabled(record.forceStart) ? "是" : "否"),
    },
    {
      key: "inQueue",
      title: "排队中",
      width: 90,
      render: (_value, record) => (isFlagEnabled(record.inQueue) ? "是" : "否"),
    },
    {
      key: "status",
      title: "任务状态",
      width: 130,
      render: (_value, record) => <QueueStatusBadge status={record.status} />,
    },
    {
      key: "createTime",
      title: "进入时间",
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
            onClick={() => openPriorityEditor(record)}
          >
            <Pencil size={13} />
            优先级
          </Button>
          <Button
            size="sm"
            variant="ghost"
            className="px-1 text-xs font-normal"
            isPending={startingQueueId === record.id}
            onClick={() => void forceStart(record)}
          >
            <Play size={13} />
            强制启动
          </Button>
        </div>
      ),
    },
  ];

  return (
    <>
      <div className="flex min-h-full flex-col bg-[var(--yak-color-hover)] text-[var(--yak-components-panel-text)]">
        <PageHeader title="任务组队列" bordered className="bg-white px-6 max-md:px-4" />
        <div className="flex min-h-0 flex-1 px-6 pb-4 pt-5 max-md:px-4">
          <div className="flex min-h-0 flex-1 flex-col bg-white p-4">
            <div className="mb-4 flex flex-wrap items-center gap-2">
              <div className="w-[190px] shrink-0">
                <Select<string>
                  size="small"
                  items={groupOptions}
                  value={groupIdInput}
                  onValueChange={(value) => setGroupIdInput(value ?? "ALL")}
                >
                  <SelectTrigger variant="outlined" className="w-full">
                    <SelectValue placeholder="任务组" />
                  </SelectTrigger>
                  <SelectContent>
                    {Object.entries(groupOptions).map(([value, label]) => (
                      <SelectItem key={value} value={value}>
                        <SelectItemText>{label}</SelectItemText>
                        <SelectItemIndicator />
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="w-[220px] shrink-0">
                <Input
                  size="small"
                  variant="outlined"
                  value={workflowNameInput}
                  placeholder="工作流实例名称"
                  onChange={(event) => setWorkflowNameInput(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") applyFilters();
                  }}
                />
              </div>
              <div className="w-[220px] shrink-0">
                <Input
                  size="small"
                  variant="outlined"
                  value={taskNameInput}
                  placeholder="任务实例名称"
                  onChange={(event) => setTaskNameInput(event.target.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") applyFilters();
                  }}
                />
              </div>
              <Button size="sm" variant="secondary" onClick={applyFilters}>
                搜索
              </Button>
              <Button
                size="sm"
                variant="ghost"
                className="ml-auto"
                isPending={queueQuery.isFetching}
                onClick={() => void queueQuery.refetch()}
              >
                <RefreshCw size={14} />
                刷新
              </Button>
            </div>

            {queueQuery.isError ? (
              <div role="alert" className="mb-3 flex items-center gap-3 text-sm">
                任务组队列加载失败
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={() => void queueQuery.refetch()}
                >
                  重试
                </Button>
              </div>
            ) : null}

            {groupsQuery.isError ? (
              <div className="mb-3 text-xs text-[var(--yak-components-muted-text)]">
                任务组名称加载失败，列表中将暂时显示任务组编号。
              </div>
            ) : null}

            <section className="min-h-0 flex-1">
              <Table<TaskGroupQueueRecord>
                className="min-h-full"
                columns={columns}
                dataSource={records}
                rowKey="id"
                loading={queueQuery.isPending || queueQuery.isFetching}
                bordered
                size="medium"
                scroll={{ x: 1450 }}
                emptyText="暂无任务组队列记录"
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
        open={Boolean(priorityRecord)}
        onOpenChange={(open) => {
          if (!open && !savingPriority) setPriorityRecord(undefined);
        }}
      >
        <DialogContent className="w-[420px]">
          <DialogTitle className="text-base font-semibold">修改组内优先级</DialogTitle>
          <DialogDescription className="mt-1 text-xs leading-5 text-[var(--yak-components-muted-text)]">
            优先级数值越大，任务在同一任务组中的调度优先级越高。
          </DialogDescription>
          <div className="mt-5">
            <Field invalid={Boolean(priorityError)}>
              <FieldLabel required htmlFor="task-group-queue-priority">
                优先级
              </FieldLabel>
              <Input
                id="task-group-queue-priority"
                type="number"
                min={0}
                step={1}
                size="small"
                variant="outlined"
                value={priorityValue}
                aria-invalid={Boolean(priorityError) || undefined}
                onChange={(event) => {
                  setPriorityValue(event.target.value);
                  setPriorityError("");
                }}
              />
              <FieldError match={Boolean(priorityError)}>{priorityError}</FieldError>
            </Field>
          </div>
          <div className="mt-6 flex justify-end gap-2">
            <Button
              size="sm"
              variant="secondary"
              isDisabled={savingPriority}
              onClick={() => setPriorityRecord(undefined)}
            >
              取消
            </Button>
            <Button
              size="sm"
              variant="primary"
              isPending={savingPriority}
              onClick={() => void savePriority()}
            >
              保存
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}

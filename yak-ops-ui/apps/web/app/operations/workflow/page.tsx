import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Button,
  DateField,
  DateI18nProvider,
  DateRangePicker,
  Input,
  Modal,
  RangeCalendar,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Switch,
  Table,
  toast,
  type DateRange,
  type TableColumns,
} from "@yak-ops/yak-ui";

import { ExecutionStatusIndicator } from "@/app/components/execution-status-indicator";
import { useWorkspace } from "@/hooks/use-workspace";
import { listDataSyncWorkflowNames } from "@/service/data-sync";
import { listWorkflowDefinitionNames } from "@/service/workflow/definition";
import { stopWorkflowInstance } from "@/service/workflow/execution";
import {
  getWorkflowInstanceTasks,
  listWorkflowInstances,
  listWorkflowSchedules,
  offlineWorkflowSchedule,
  publishWorkflowSchedule,
  type WorkflowBusinessType,
  type WorkflowInstanceSummary,
  type WorkflowInstanceTask,
  type WorkflowScheduleRecord,
} from "@/service/workflow/management";
import { OperationsPageShell } from "../components/operations-page-shell";
import {
  ACTIVE_WORKFLOW_STATES,
  WORKFLOW_STATE_ITEMS,
  durationText,
  triggerText,
} from "../presentation";

const PAGE_SIZE = 20;
const BUSINESS_ITEMS: Record<string, string> = {
  ALL: "全部业务",
  WORKFLOW: "工作流",
  DATA_SYNC_OFFLINE: "离线同步",
  DATA_SYNC_REALTIME: "实时同步",
};

function SelectField({
  value,
  items,
  onChange,
  className = "w-[150px]",
}: {
  value: string;
  items: Record<string, string>;
  onChange: (value: string) => void;
  className?: string;
}) {
  return (
    <div className={className}>
      <Select
        size="small"
        items={items}
        value={value}
        onValueChange={(next) => onChange(String(next ?? ""))}
      >
        <SelectTrigger variant="outlined">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {Object.entries(items).map(([key, label]) => (
            <SelectItem key={key} value={key}>
              <SelectItemText>{label}</SelectItemText>
              <SelectItemIndicator />
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
}

function WorkflowInstanceDateRangePicker({
  value,
  onChange,
}: {
  value: DateRange | null;
  onChange: (value: DateRange | null) => void;
}) {
  return (
    <DateI18nProvider locale="zh-CN">
      <DateRangePicker
        aria-label="实例开始时间范围"
        value={value}
        onChange={onChange}
        className="w-[236px] shrink-0"
      >
        <DateField.Group fullWidth>
          <DateField.Input slot="start" className="min-w-0 px-2">
            {(segment) => <DateField.Segment segment={segment} />}
          </DateField.Input>
          <DateRangePicker.RangeSeparator />
          <DateField.Input slot="end" className="min-w-0 px-2">
            {(segment) => <DateField.Segment segment={segment} />}
          </DateField.Input>
          <DateField.Suffix>
            <DateRangePicker.Trigger aria-label="选择实例开始时间范围">
              <DateRangePicker.TriggerIndicator />
            </DateRangePicker.Trigger>
          </DateField.Suffix>
        </DateField.Group>
        <DateRangePicker.Popover className="w-auto">
          <RangeCalendar aria-label="实例开始时间范围" firstDayOfWeek="mon">
            <RangeCalendar.Header>
              <RangeCalendar.NavButton slot="previous" aria-label="上个月" />
              <RangeCalendar.Heading />
              <RangeCalendar.NavButton slot="next" aria-label="下个月" />
            </RangeCalendar.Header>
            <RangeCalendar.Grid>
              <RangeCalendar.GridHeader>
                {(day) => <RangeCalendar.HeaderCell>{day}</RangeCalendar.HeaderCell>}
              </RangeCalendar.GridHeader>
              <RangeCalendar.GridBody>
                {(date) => <RangeCalendar.Cell date={date} />}
              </RangeCalendar.GridBody>
            </RangeCalendar.Grid>
          </RangeCalendar>
        </DateRangePicker.Popover>
      </DateRangePicker>
    </DateI18nProvider>
  );
}

export function WorkflowInstancesOperationsPage() {
  const { currentWorkspace } = useWorkspace();
  const scope = currentWorkspace?.id ?? "";
  const queryClient = useQueryClient();
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [executorInput, setExecutorInput] = useState("");
  const [executorName, setExecutorName] = useState("");
  const [dateRange, setDateRange] = useState<DateRange | null>(null);
  const [state, setState] = useState("ALL");
  const [businessType, setBusinessType] = useState("ALL");
  const [pageNo, setPageNo] = useState(1);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [selected, setSelected] = useState<WorkflowInstanceSummary>();
  const refreshSeconds = 5;

  const query = useQuery({
    queryKey: [
      "operations",
      "workflow-instances",
      scope,
      pageNo,
      keyword,
      executorName,
      dateRange?.start.toString() ?? "",
      dateRange?.end.toString() ?? "",
      state,
      businessType,
    ],
    queryFn: ({ signal }) =>
      listWorkflowInstances(undefined, pageNo, PAGE_SIZE, signal, {
        searchVal: keyword || undefined,
        executorName: executorName || undefined,
        startDate: dateRange ? `${dateRange.start.toString()} 00:00:00` : undefined,
        endDate: dateRange ? `${dateRange.end.toString()} 23:59:59` : undefined,
        stateType: state === "ALL" ? undefined : state,
        businessType:
          businessType === "ALL" ? undefined : (businessType as WorkflowBusinessType),
      }),
    enabled: Boolean(scope),
    refetchInterval: autoRefresh ? refreshSeconds * 1000 : false,
  });

  const definitionsQuery = useQuery({
    queryKey: ["operations", "workflow-definition-names", scope],
    queryFn: ({ signal }) => listWorkflowDefinitionNames(signal),
    enabled: Boolean(scope),
    staleTime: 30_000,
  });

  const offlineTasks = useQuery({
    queryKey: ["operations", "data-sync-workflow-names", scope, "OFFLINE"],
    queryFn: ({ signal }) => listDataSyncWorkflowNames(signal, "OFFLINE"),
    enabled: Boolean(scope),
    staleTime: 30_000,
  });
  const realtimeTasks = useQuery({
    queryKey: ["operations", "data-sync-workflow-names", scope, "REALTIME"],
    queryFn: ({ signal }) => listDataSyncWorkflowNames(signal, "REALTIME"),
    enabled: Boolean(scope),
    staleTime: 30_000,
  });

  const workflowNames = useMemo(
    () =>
      new Map(
        (definitionsQuery.data ?? []).map((item) => [String(item.code), item.name] as const),
      ),
    [definitionsQuery.data],
  );
  const syncWorkflowMeta = useMemo(() => {
    const meta = new Map<string, { name: string; type: WorkflowBusinessType }>();
    for (const task of offlineTasks.data ?? []) {
      meta.set(String(task.workflowCode), { name: task.name, type: "DATA_SYNC_OFFLINE" });
    }
    for (const task of realtimeTasks.data ?? []) {
      meta.set(String(task.workflowCode), { name: task.name, type: "DATA_SYNC_REALTIME" });
    }
    return meta;
  }, [offlineTasks.data, realtimeTasks.data]);

  const stopMutation = useMutation({
    mutationFn: stopWorkflowInstance,
    onSuccess: async () => {
      toast.success("停止指令已提交");
      await queryClient.invalidateQueries({ queryKey: ["operations", "workflow-instances"] });
    },
    onError: () => toast.error("停止工作流失败"),
  });

  const taskQuery = useQuery({
    queryKey: ["operations", "workflow-instance-tasks", scope, selected?.id],
    queryFn: ({ signal }) => getWorkflowInstanceTasks(selected!.id, signal),
    enabled: Boolean(scope && selected?.id),
  });

  const displayName = (record: WorkflowInstanceSummary) => {
    const sync = syncWorkflowMeta.get(String(record.workflowDefinitionCode));
    if (sync?.name) return sync.name;
    return workflowNames.get(String(record.workflowDefinitionCode)) || record.name || `工作流 #${record.workflowDefinitionCode}`;
  };
  const businessLabel = (record: WorkflowInstanceSummary) => {
    const type =
      record.businessType ??
      syncWorkflowMeta.get(String(record.workflowDefinitionCode))?.type ??
      "WORKFLOW";
    return BUSINESS_ITEMS[type] ?? "工作流";
  };

  const columns: TableColumns<WorkflowInstanceSummary> = [
    {
      key: "name",
      title: "工作流 / 实例",
      minWidth: 250,
      render: (_value, record) => (
        <div className="flex min-w-0 flex-col gap-1">
          <div className="truncate text-sm font-medium text-[var(--yak-components-panel-text)]">
            {displayName(record)}
          </div>
          <div className="truncate text-xs leading-4 text-[var(--yak-components-muted-text)]">
            <span>实例 #{record.id}</span>
            <span className="mx-1.5">·</span>
            <span>定义 #{record.workflowDefinitionCode}</span>
          </div>
        </div>
      ),
    },
    { key: "business", title: "业务类型", width: 110, render: (_v, r) => businessLabel(r) },
    {
      key: "state",
      title: "实例状态",
      width: 110,
      render: (_v, r) => (
        <ExecutionStatusIndicator status={r.state} />
      ),
    },
    {
      key: "commandType",
      title: "启动方式",
      width: 120,
      render: (_v, r) => (
        <span className="whitespace-nowrap text-xs">{triggerText(r.commandType)}</span>
      ),
    },
    {
      key: "executorName",
      title: "执行人",
      width: 120,
      render: (_v, r) => (
        <span className="whitespace-nowrap text-xs">{r.executorName || "-"}</span>
      ),
    },
    {
      key: "duration",
      title: "运行耗时",
      width: 110,
      render: (_v, r) => (
        <span className="whitespace-nowrap text-xs font-medium tabular-nums">
          {r.duration || durationText(r.startTime, r.endTime)}
        </span>
      ),
    },
    {
      key: "startTime",
      title: "开始时间",
      width: 180,
      render: (_v, r) => (
        <span className="whitespace-nowrap text-xs tabular-nums">{r.startTime || "-"}</span>
      ),
    },
    {
      key: "endTime",
      title: "结束时间",
      width: 180,
      render: (_v, r) => (
        <span className="whitespace-nowrap text-xs tabular-nums">{r.endTime || "-"}</span>
      ),
    },
    {
      key: "actions",
      title: "操作",
      width: 150,
      fixed: "right",
      align: "center",
      render: (_v, record) => (
        <div className="flex items-center justify-center gap-1">
          <Button
            variant="ghost"
            size="sm"
            className="px-2 text-xs font-medium !text-[var(--yak-color-primary)] hover:!text-[var(--yak-color-primary)]"
            onClick={() => setSelected(record)}
          >
            详情
          </Button>
          {ACTIVE_WORKFLOW_STATES.has(record.state) ? (
            <Button
              variant="ghost"
              size="sm"
              className="px-2 text-xs !text-[var(--yak-color-danger)] hover:!text-[var(--yak-color-danger)]"
              isPending={stopMutation.isPending}
              onClick={() => stopMutation.mutate(record.id)}
            >
              停止
            </Button>
          ) : null}
        </div>
      ),
    },
  ];

  const taskColumns: TableColumns<WorkflowInstanceTask> = [
    { key: "name", title: "节点名称", minWidth: 180, dataIndex: "name" },
    { key: "taskType", title: "类型", width: 110, dataIndex: "taskType" },
    {
      key: "state",
      title: "状态",
      width: 110,
      render: (_v, r) => (
        <ExecutionStatusIndicator status={r.state} />
      ),
    },
    { key: "duration", title: "耗时", width: 100, render: (_v, r) => r.duration || durationText(r.startTime, r.endTime) },
    { key: "startTime", title: "开始时间", width: 170, render: (_v, r) => r.startTime || "-" },
    { key: "endTime", title: "结束时间", width: 170, render: (_v, r) => r.endTime || "-" },
  ];

  return (
    <OperationsPageShell
      title="工作流实例"
      extra={<Button size="sm" variant="secondary" onClick={() => void query.refetch()}>刷新</Button>}
    >
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <SelectField
            value={businessType}
            items={BUSINESS_ITEMS}
            className="w-[150px] shrink-0"
            onChange={(value) => {
              setBusinessType(value || "ALL");
              setPageNo(1);
            }}
          />
          <SelectField
            value={state}
            items={WORKFLOW_STATE_ITEMS as unknown as Record<string, string>}
            className="w-[150px] shrink-0"
            onChange={(value) => {
              setState(value || "ALL");
              setPageNo(1);
            }}
          />
          <div className="w-[150px] shrink-0">
            <Input
              size="small"
              variant="outlined"
              className="w-full"
              placeholder="执行人"
              value={executorInput}
              onChange={(event) => setExecutorInput(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  setExecutorName(executorInput.trim());
                  setKeyword(keywordInput.trim());
                  setPageNo(1);
                }
              }}
            />
          </div>
          <WorkflowInstanceDateRangePicker
            value={dateRange}
            onChange={(next) => {
              setDateRange(next);
              setPageNo(1);
            }}
          />
          <div className="w-[280px] max-w-full shrink-0">
            <Input
              size="small"
              variant="outlined"
              className="w-full"
              placeholder="工作流名称 / 实例名称"
              value={keywordInput}
              onChange={(event) => setKeywordInput(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") {
                  setKeyword(keywordInput.trim());
                  setExecutorName(executorInput.trim());
                  setPageNo(1);
                }
              }}
            />
          </div>
          <Button
            size="sm"
            variant="secondary"
            className="shrink-0"
            onClick={() => {
              setKeyword(keywordInput.trim());
              setExecutorName(executorInput.trim());
              setPageNo(1);
            }}
          >
            搜索
          </Button>
        </div>
        <label className="inline-flex cursor-pointer items-center gap-2 text-xs text-[var(--yak-components-muted-text)]">
          <span>自动刷新</span>
          <Switch
            size="small"
            checked={autoRefresh}
            onCheckedChange={setAutoRefresh}
            aria-label="自动刷新"
          />
          {autoRefresh ? <span>每 {refreshSeconds} 秒</span> : null}
        </label>
      </div>
      <div className="min-h-0 flex-1">
        <Table<WorkflowInstanceSummary>
          className="min-h-full [&_th]:!text-[var(--yak-components-muted-text)]"
          columns={columns}
          dataSource={query.data?.totalList ?? []}
          rowKey="id"
          bordered
          size="medium"
          loading={query.isPending}
          scroll={{ x: 1380 }}
          emptyText={query.isError ? "工作流实例加载失败" : "暂无工作流实例"}
          pagination={
            (query.data?.total ?? 0) > 0
              ? { current: pageNo, pageSize: PAGE_SIZE, total: query.data?.total ?? 0, onChange: setPageNo }
              : false
          }
        />
      </div>

      <Modal
        open={Boolean(selected)}
        centered
        width={920}
        title={selected ? `${displayName(selected)} · 实例 #${selected.id}` : "实例详情"}
        onClose={() => setSelected(undefined)}
        footer={<Button size="sm" variant="secondary" onClick={() => setSelected(undefined)}>关闭</Button>}
      >
        <Table<WorkflowInstanceTask>
          className="[&_td]:!text-xs"
          columns={taskColumns}
          dataSource={taskQuery.data?.taskList ?? []}
          rowKey="id"
          size="small"
          loading={taskQuery.isPending}
          scroll={{ x: 900 }}
          emptyText={taskQuery.isError ? "节点信息加载失败" : "暂无节点实例"}
        />
      </Modal>
    </OperationsPageShell>
  );
}

export function WorkflowSchedulesOperationsPage() {
  const { currentWorkspace } = useWorkspace();
  const scope = currentWorkspace?.id ?? "";
  const queryClient = useQueryClient();
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [pageNo, setPageNo] = useState(1);

  const query = useQuery({
    queryKey: ["operations", "workflow-schedules", scope, pageNo, keyword],
    queryFn: ({ signal }) => listWorkflowSchedules(pageNo, PAGE_SIZE, keyword, signal),
    enabled: Boolean(scope),
  });
  const stateMutation = useMutation({
    mutationFn: ({ id, online }: { id: number; online: boolean }) =>
      online ? publishWorkflowSchedule(id) : offlineWorkflowSchedule(id),
    onSuccess: async () => {
      toast.success("调度状态已更新");
      await queryClient.invalidateQueries({ queryKey: ["operations", "workflow-schedules"] });
    },
    onError: () => toast.error("调度状态更新失败"),
  });

  const columns: TableColumns<WorkflowScheduleRecord> = [
    {
      key: "workflow",
      title: "工作流",
      minWidth: 230,
      render: (_v, r) => (
        <div>
          <div className="font-medium">{r.workflowDefinitionName || `工作流 #${r.workflowDefinitionCode}`}</div>
          <div className="mt-0.5 text-xs text-[var(--yak-components-muted-text)]">定义 #{r.workflowDefinitionCode}</div>
        </div>
      ),
    },
    { key: "releaseState", title: "调度状态", width: 100, render: (_v, r) => r.releaseState === "ONLINE" ? "已上线" : "已下线" },
    { key: "crontab", title: "Cron 表达式", minWidth: 190, dataIndex: "crontab" },
    { key: "timezoneId", title: "时区", width: 170, render: (_v, r) => r.timezoneId || "-" },
    { key: "startTime", title: "生效开始", width: 175, render: (_v, r) => r.startTime || "-" },
    { key: "endTime", title: "生效结束", width: 175, render: (_v, r) => r.endTime || "-" },
    { key: "workerGroup", title: "Worker Group", width: 140, render: (_v, r) => r.workerGroup || "default" },
    { key: "failureStrategy", title: "失败策略", width: 100, render: (_v, r) => r.failureStrategy || "END" },
    {
      key: "actions",
      title: "操作",
      width: 110,
      fixed: "right",
      align: "center",
      render: (_v, r) => (
        <Button
          size="sm"
          variant="ghost"
          isPending={stateMutation.isPending}
          onClick={() => stateMutation.mutate({ id: r.id, online: r.releaseState !== "ONLINE" })}
        >
          {r.releaseState === "ONLINE" ? "下线" : "上线"}
        </Button>
      ),
    },
  ];

  return (
    <OperationsPageShell
      title="周期调度"
      extra={<Button size="sm" variant="secondary" onClick={() => void query.refetch()}>刷新</Button>}
    >
      <div className="mb-4 flex items-center gap-2">
        <Input
          size="small"
          variant="outlined"
          className="w-[280px]"
          placeholder="搜索工作流名称"
          value={keywordInput}
          onChange={(event) => setKeywordInput(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") {
              setKeyword(keywordInput.trim());
              setPageNo(1);
            }
          }}
        />
        <Button size="sm" variant="secondary" onClick={() => { setKeyword(keywordInput.trim()); setPageNo(1); }}>
          搜索
        </Button>
      </div>
      <div className="min-h-0 flex-1">
        <Table<WorkflowScheduleRecord>
          className="min-h-full [&_td]:!text-xs"
          columns={columns}
          dataSource={query.data?.totalList ?? []}
          rowKey="id"
          bordered
          size="medium"
          loading={query.isPending || query.isFetching}
          scroll={{ x: 1460 }}
          emptyText={query.isError ? "调度列表加载失败" : "暂无周期调度"}
          pagination={
            (query.data?.total ?? 0) > 0
              ? { current: pageNo, pageSize: PAGE_SIZE, total: query.data?.total ?? 0, onChange: setPageNo }
              : false
          }
        />
      </div>
    </OperationsPageShell>
  );
}

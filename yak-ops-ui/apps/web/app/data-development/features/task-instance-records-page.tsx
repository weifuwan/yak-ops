import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Button,
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
import { Download, Eye, RefreshCw } from "lucide-react";

import { useWorkspace } from "@/hooks/use-workspace";
import {
  getTaskInstanceDetail,
  listTaskInstances,
  type TaskInstanceRecord,
} from "@/service/workflow/task-instances";
import {
  downloadTaskInstanceLog,
  getTaskInstanceLog,
} from "@/service/workflow/log";
import { OperationsPageShell } from "@/app/operations/components/operations-page-shell";

const PAGE_SIZE = 20;
const TYPE_ITEMS = { BATCH: "离线任务", STREAM: "实时任务" };
const STATUS_ITEMS: Record<string, string> = {
  ALL: "全部状态",
  SUBMITTED_SUCCESS: "已提交",
  DISPATCH: "待分发",
  RUNNING_EXECUTION: "运行中",
  DELAY_EXECUTION: "延迟执行",
  NEED_FAULT_TOLERANCE: "容错中",
  SUCCESS: "成功",
  FORCED_SUCCESS: "强制成功",
  FAILURE: "失败",
  PAUSE: "已暂停",
  KILL: "已停止",
};

function statusLabel(value?: string | null) {
  return STATUS_ITEMS[value ?? ""] ?? value ?? "-";
}

function dateBoundary(value: string, end = false) {
  return value ? value + (end ? " 23:59:59" : " 00:00:00") : undefined;
}

export function TaskInstanceRecordsPage() {
  const { currentWorkspace } = useWorkspace();
  const scope = currentWorkspace?.id ?? "";
  const [executeType, setExecuteType] = useState<"BATCH" | "STREAM">("BATCH");
  const [status, setStatus] = useState("ALL");
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [executorInput, setExecutorInput] = useState("");
  const [executorName, setExecutorName] = useState("");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [pageNo, setPageNo] = useState(1);
  const [selected, setSelected] = useState<TaskInstanceRecord | null>(null);
  const [log, setLog] = useState("");
  const [logLoading, setLogLoading] = useState(false);

  const query = useQuery({
    queryKey: ["data-development", "task-instances", scope, executeType, status, keyword, executorName, startDate, endDate, pageNo],
    queryFn: ({ signal }) =>
      listTaskInstances(
        {
          pageNo,
          pageSize: PAGE_SIZE,
          taskExecuteType: executeType,
          stateType: status === "ALL" ? undefined : status,
          searchVal: keyword || undefined,
          executorName: executorName || undefined,
          startDate: dateBoundary(startDate),
          endDate: dateBoundary(endDate, true),
        },
        signal,
      ),
    enabled: Boolean(scope),
    refetchInterval: 8000,
  });

  const detailQuery = useQuery({
    queryKey: ["data-development", "task-instance-detail", selected?.id],
    queryFn: ({ signal }) => getTaskInstanceDetail(selected!.id, signal),
    enabled: Boolean(selected?.id),
  });

  const loadLog = async () => {
    if (!selected) return;
    setLogLoading(true);
    try {
      const result = await getTaskInstanceLog(selected.id, 0, 1000);
      setLog(result.message || "");
    } catch (error) {
      setLog(error instanceof Error ? error.message : "日志加载失败");
    } finally {
      setLogLoading(false);
    }
  };

  const downloadLog = async () => {
    if (!selected) return;
    const blob = await downloadTaskInstanceLog(selected.id);
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `task-${selected.id}.log`;
    anchor.click();
    URL.revokeObjectURL(url);
  };

  const columns: TableColumns<TaskInstanceRecord> = [
    {
      key: "name",
      title: "任务名称",
      minWidth: 220,
      render: (_value, record) => (
        <div className="min-w-0">
          <div className="truncate text-xs font-medium">{record.name || `任务 #${record.id}`}</div>
          <div className="mt-0.5 text-[11px] text-[var(--yak-components-muted-text)]">
            任务 #{record.id} · 定义 #{record.taskCode}
          </div>
        </div>
      ),
    },
    {
      key: "workflow",
      title: "所属工作流",
      minWidth: 180,
      render: (_value, record) => (
        <div className="min-w-0">
          <div className="truncate text-xs">{record.workflowInstanceName || "-"}</div>
          <div className="mt-0.5 text-[11px] text-[var(--yak-components-muted-text)]">
            实例 #{record.workflowInstanceId}
          </div>
        </div>
      ),
    },
    { key: "taskType", title: "节点类型", width: 110, dataIndex: "taskType" },
    { key: "state", title: "执行状态", width: 100, render: (_v, r) => statusLabel(r.state) },
    { key: "executorName", title: "执行人", width: 110, render: (_v, r) => r.executorName || "-" },
    { key: "host", title: "Worker", minWidth: 160, render: (_v, r) => r.host || "-" },
    { key: "duration", title: "耗时", width: 100, render: (_v, r) => r.duration || "-" },
    { key: "startTime", title: "开始时间", width: 170, render: (_v, r) => r.startTime || "-" },
    {
      key: "actions",
      title: "操作",
      width: 100,
      fixed: "right",
      align: "center",
      render: (_v, record) => (
        <Button size="sm" variant="ghost" onClick={() => { setSelected(record); setLog(""); }}>
          <Eye size={13} />
          详情
        </Button>
      ),
    },
  ];

  return (
    <OperationsPageShell
      title="任务运行记录"
      extra={
        <Button size="sm" variant="secondary" onClick={() => void query.refetch()} isDisabled={query.isFetching}>
          <RefreshCw size={13} />
          刷新
        </Button>
      }
    >
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <div className="flex shrink-0 rounded-md border border-[#e1e5eb] bg-[#f7f8fa] p-0.5">
          {(["BATCH", "STREAM"] as const).map((value) => (
            <button
              key={value}
              type="button"
              onClick={() => { setExecuteType(value); setPageNo(1); }}
              className={[
                "h-7 cursor-pointer rounded px-3 text-xs transition-colors",
                executeType === value ? "bg-white font-medium text-[#30343a] shadow-sm" : "text-[#858d99] hover:text-[#4b5563]",
              ].join(" ")}
            >
              {TYPE_ITEMS[value]}
            </button>
          ))}
        </div>
        <div className="w-[140px] shrink-0">
          <Select
            size="small"
            items={STATUS_ITEMS}
            value={status}
            onValueChange={(value) => { setStatus(String(value ?? "ALL")); setPageNo(1); }}
          >
            <SelectTrigger variant="outlined"><SelectValue /></SelectTrigger>
            <SelectContent>
              {Object.entries(STATUS_ITEMS).map(([value, label]) => (
                <SelectItem key={value} value={value}><SelectItemText>{label}</SelectItemText><SelectItemIndicator /></SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <Input
          size="small"
          variant="outlined"
          className="w-[220px]"
          placeholder="任务名称 / 任务 ID / 工作流"
          value={keywordInput}
          onChange={(event) => setKeywordInput(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") { setKeyword(keywordInput.trim()); setPageNo(1); }
          }}
        />
        <Input
          size="small"
          variant="outlined"
          className="w-[130px]"
          placeholder="执行人"
          value={executorInput}
          onChange={(event) => setExecutorInput(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter") { setExecutorName(executorInput.trim()); setPageNo(1); }
          }}
        />
        <label className="flex items-center gap-1 text-[11px] text-[#7d8591]">
          开始
          <Input size="small" variant="outlined" type="date" className="w-[140px]" value={startDate} onChange={(event) => { setStartDate(event.target.value); setPageNo(1); }} />
        </label>
        <label className="flex items-center gap-1 text-[11px] text-[#7d8591]">
          结束
          <Input size="small" variant="outlined" type="date" className="w-[140px]" value={endDate} onChange={(event) => { setEndDate(event.target.value); setPageNo(1); }} />
        </label>
        <Button size="sm" variant="secondary" onClick={() => { setKeyword(keywordInput.trim()); setExecutorName(executorInput.trim()); setPageNo(1); }}>
          搜索
        </Button>
      </div>

      <div className="min-h-0 flex-1">
        <Table<TaskInstanceRecord>
          className="min-h-full [&_td]:!text-xs"
          columns={columns}
          dataSource={query.data?.totalList ?? []}
          rowKey="id"
          bordered
          size="medium"
          loading={query.isPending || query.isFetching}
          scroll={{ x: 1380 }}
          emptyText={query.isError ? "任务运行记录加载失败" : "暂无任务运行记录"}
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
        width={900}
        title={selected ? `${selected.name} · 任务实例 #${selected.id}` : "任务实例详情"}
        onClose={() => { setSelected(null); setLog(""); }}
        footer={
          <div className="flex w-full justify-between">
            <Button size="sm" variant="secondary" onClick={() => void loadLog()} isPending={logLoading}>
              查看日志
            </Button>
            <div className="flex gap-2">
              <Button size="sm" variant="secondary" onClick={() => void downloadLog()} isDisabled={!selected}>
                <Download size={13} />下载日志
              </Button>
              <Button size="sm" variant="secondary" onClick={() => { setSelected(null); setLog(""); }}>关闭</Button>
            </div>
          </div>
        }
      >
        {selected ? (
          <div className="space-y-4">
            <div className="grid grid-cols-3 gap-3 max-sm:grid-cols-1">
              {[
                ["执行状态", statusLabel(detailQuery.data?.state ?? selected.state)],
                ["所属工作流", detailQuery.data?.workflowInstanceName ?? selected.workflowInstanceName],
                ["执行人", detailQuery.data?.executorName ?? selected.executorName],
                ["Worker", detailQuery.data?.host ?? selected.host],
                ["开始时间", detailQuery.data?.startTime ?? selected.startTime],
                ["结束时间", detailQuery.data?.endTime ?? selected.endTime],
                ["运行耗时", detailQuery.data?.duration ?? selected.duration],
                ["重试次数", String(detailQuery.data?.retryTimes ?? selected.retryTimes ?? 0)],
                ["Worker Group", detailQuery.data?.workerGroup ?? selected.workerGroup],
              ].map(([label, value]) => (
                <div key={label} className="rounded-md border border-[#edf0f3] px-3 py-2">
                  <div className="text-[10px] text-[#8a9099]">{label}</div>
                  <div className="mt-1 break-all text-xs text-[#30343a]">{value || "-"}</div>
                </div>
              ))}
            </div>
            <div className="overflow-hidden rounded-md border border-[#e5e8ed]">
              <div className="border-b border-[#e5e8ed] bg-[#f8f9fb] px-3 py-2 text-xs font-medium text-[#4b5563]">任务日志</div>
              <pre className="m-0 max-h-[320px] min-h-[100px] overflow-auto bg-[#111315] p-3 text-[11px] leading-5 text-[#e7e9ed]">
                {log || "点击“查看日志”加载任务执行日志"}
              </pre>
            </div>
          </div>
        ) : null}
      </Modal>
    </OperationsPageShell>
  );
}

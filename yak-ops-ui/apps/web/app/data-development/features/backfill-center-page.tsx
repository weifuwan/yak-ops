import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Button,
  Field,
  FieldLabel,
  Input,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Textarea,
  toast,
} from "@yak-ops/yak-ui";
import { CalendarClock, Play, Workflow } from "lucide-react";

import { listEnvironments } from "@/service/environment";
import { listWorkerGroupNames } from "@/service/worker-group";
import {
  listWorkflowDefinitions,
  type WorkflowDefinitionRecord,
} from "@/service/workflow/definition";
import {
  startWorkflowBackfill,
  type WorkflowBackfillRunMode,
} from "@/service/workflow/execution";
import { OperationsPageShell } from "@/app/operations/components/operations-page-shell";
import { useDataDevelopmentWorkbench } from "../workbench-context";

type RecentSubmission = {
  id: string;
  workflowName: string;
  workflowCode: number;
  instanceIds: number[];
  createdAt: string;
};

const RUN_MODE_ITEMS = {
  RUN_MODE_SERIAL: "串行补数",
  RUN_MODE_PARALLEL: "并行补数",
};
const DEPENDENCY_ITEMS = {
  OFF_MODE: "不补上下游",
  ALL_DEPENDENT: "补齐依赖工作流",
};
const ORDER_ITEMS = {
  ASC_ORDER: "正序执行",
  DESC_ORDER: "倒序执行",
};
const FAILURE_ITEMS = {
  END: "失败即结束",
  CONTINUE: "失败后继续",
};
const PRIORITY_ITEMS = {
  HIGHEST: "最高",
  HIGH: "高",
  MEDIUM: "中",
  LOW: "低",
  LOWEST: "最低",
};

function parseStartParams(value: string): Record<string, string> {
  return Object.fromEntries(
    value
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean)
      .map((line) => {
        const separator = line.indexOf("=");
        return separator < 0
          ? [line, ""]
          : [line.slice(0, separator).trim(), line.slice(separator + 1).trim()];
      })
      .filter(([key]) => Boolean(key)),
  );
}

function normalizeDateTime(value: string): string {
  return value.length === 10 ? value + " 00:00:00" : value;
}

export function BackfillCenterPage() {
  const { openWorkflowInstance } = useDataDevelopmentWorkbench();
  const [workflowKeyword, setWorkflowKeyword] = useState("");
  const [workflowCode, setWorkflowCode] = useState("");
  const [dateMode, setDateMode] = useState<"range" | "manual">("range");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [manualDates, setManualDates] = useState("");
  const [runMode, setRunMode] = useState<WorkflowBackfillRunMode>("RUN_MODE_SERIAL");
  const [parallelism, setParallelism] = useState("0");
  const [dependentMode, setDependentMode] = useState<"OFF_MODE" | "ALL_DEPENDENT">("OFF_MODE");
  const [allLevelDependent, setAllLevelDependent] = useState(false);
  const [executionOrder, setExecutionOrder] = useState<"ASC_ORDER" | "DESC_ORDER">("ASC_ORDER");
  const [failureStrategy, setFailureStrategy] = useState<"END" | "CONTINUE">("END");
  const [priority, setPriority] = useState<keyof typeof PRIORITY_ITEMS>("MEDIUM");
  const [workerGroup, setWorkerGroup] = useState("default");
  const [environmentCode, setEnvironmentCode] = useState("-1");
  const [startParamsText, setStartParamsText] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [recentSubmissions, setRecentSubmissions] = useState<RecentSubmission[]>([]);

  const workflowsQuery = useQuery({
    queryKey: ["data-development", "backfill", "workflows", workflowKeyword],
    queryFn: ({ signal }) =>
      listWorkflowDefinitions(
        { pageNo: 1, pageSize: 100, searchVal: workflowKeyword.trim() || undefined },
        signal,
      ),
  });
  const workerGroupsQuery = useQuery({
    queryKey: ["data-development", "backfill", "worker-groups"],
    queryFn: ({ signal }) => listWorkerGroupNames(signal),
  });
  const environmentsQuery = useQuery({
    queryKey: ["data-development", "backfill", "environments"],
    queryFn: ({ signal }) => listEnvironments({ pageNo: 1, pageSize: 100 }, signal),
  });

  const workflows = workflowsQuery.data?.totalList ?? [];
  const selectedWorkflow = workflows.find(
    (workflow) => String(workflow.code) === workflowCode,
  );
  const workerGroups = useMemo(() => {
    const values = workerGroupsQuery.data ?? [];
    return values.includes("default") ? values : ["default", ...values];
  }, [workerGroupsQuery.data]);
  const environments = environmentsQuery.data?.totalList ?? [];

  const submit = async () => {
    if (!selectedWorkflow) {
      toast.error("请先选择工作流");
      return;
    }
    if (selectedWorkflow.releaseState !== "ONLINE") {
      toast.error("补数只支持已上线的工作流");
      return;
    }

    let backfillTime:
      | { complementStartDate: string; complementEndDate: string }
      | { complementScheduleDateList: string };
    if (dateMode === "range") {
      if (!startDate || !endDate || startDate > endDate) {
        toast.error("请选择有效的补数开始和结束日期");
        return;
      }
      backfillTime = {
        complementStartDate: startDate + " 00:00:00",
        complementEndDate: endDate + " 23:59:59",
      };
    } else {
      const dates = manualDates
        .split(/[\n,，]+/)
        .map((value) => normalizeDateTime(value.trim()))
        .filter(Boolean);
      if (!dates.length) {
        toast.error("请至少填写一个补数日期");
        return;
      }
      backfillTime = { complementScheduleDateList: Array.from(new Set(dates)).join(",") };
    }

    const requestedParallelism = Number(parallelism);
    if (!Number.isInteger(requestedParallelism) || requestedParallelism < 0) {
      toast.error("并行度必须是大于或等于 0 的整数");
      return;
    }
    if (runMode === "RUN_MODE_PARALLEL" && requestedParallelism < 1) {
      toast.error("并行补数的并行度至少为 1");
      return;
    }

    setSubmitting(true);
    try {
      const instanceIds = await startWorkflowBackfill(String(selectedWorkflow.code), {
        backfillTime,
        runMode,
        expectedParallelismNumber: runMode === "RUN_MODE_SERIAL" ? 0 : requestedParallelism,
        complementDependentMode: dependentMode,
        allLevelDependent,
        executionOrder,
        failureStrategy,
        workflowInstancePriority: priority,
        workerGroup,
        environmentCode: Number(environmentCode),
        startParams: parseStartParams(startParamsText),
      });
      const submission: RecentSubmission = {
        id: String(Date.now()),
        workflowName: selectedWorkflow.name,
        workflowCode: selectedWorkflow.code,
        instanceIds,
        createdAt: new Date().toLocaleString(),
      };
      setRecentSubmissions((current) => [submission, ...current].slice(0, 8));
      toast.success(`补数指令已提交，共生成 ${instanceIds.length} 个工作流实例`);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "补数提交失败");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <OperationsPageShell
      title="补数中心"
      extra={
        <Button
          size="sm"
          variant="secondary"
          onClick={() => void workflowsQuery.refetch()}
          isDisabled={workflowsQuery.isFetching}
        >
          刷新工作流
        </Button>
      }
    >
      <div className="grid min-h-0 min-w-0 flex-1 grid-cols-[minmax(280px,320px)_minmax(0,1fr)] gap-4 max-lg:grid-cols-1 max-lg:overflow-y-auto">
        <section className="flex min-h-0 min-w-0 flex-col rounded-md border border-[#e9ebef] bg-white max-lg:min-h-[240px] max-lg:max-h-[280px]">
          <div className="border-b border-[#eceef2] px-4 py-3">
            <div className="flex items-center gap-2 text-[13px] font-semibold text-[#30343a]">
              <Workflow size={15} />
              选择工作流
            </div>
            <p className="mb-0 mt-1 text-[11px] leading-5 text-[#8a9099]">
              仅支持已上线的工作流。补数日期将结合工作流已发布的 Cron 调度规则计算。
            </p>
            <Input
              size="small"
              variant="outlined"
              className="mt-3 w-full"
              placeholder="搜索工作流名称"
              value={workflowKeyword}
              onChange={(event) => setWorkflowKeyword(event.target.value)}
            />
          </div>
          <div className="min-h-0 flex-1 overflow-y-auto p-2">
            {workflowsQuery.isPending ? (
              <div className="px-3 py-8 text-center text-xs text-[#8b919b]">加载工作流...</div>
            ) : workflows.length === 0 ? (
              <div className="px-3 py-8 text-center text-xs text-[#8b919b]">没有找到工作流</div>
            ) : (
              workflows.map((workflow: WorkflowDefinitionRecord) => {
                const active = String(workflow.code) === workflowCode;
                const online = workflow.releaseState === "ONLINE";
                return (
                  <button
                    key={workflow.code}
                    type="button"
                    onClick={() => setWorkflowCode(String(workflow.code))}
                    className={[
                      "mb-1 flex w-full cursor-pointer items-center gap-2 rounded-md border px-3 py-2.5 text-left transition-colors",
                      active ? "border-[#b9c9ff] bg-[#f0f4ff]" : "border-transparent hover:bg-[#f7f8fa]",
                    ].join(" ")}
                  >
                    <Workflow size={15} className="shrink-0 text-[#6f7c92]" />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-xs font-medium text-[#30343a]">{workflow.name}</span>
                      <span className="mt-1 block text-[10px] text-[#8a9099]">
                        #{workflow.code} · {online ? "已上线" : "未上线"}
                      </span>
                    </span>
                    <span className={online ? "text-[10px] text-[#238b57]" : "text-[10px] text-[#a0a4aa]"}>
                      {online ? "可补数" : "不可补数"}
                    </span>
                  </button>
                );
              })
            )}
          </div>
        </section>

        <div className="flex min-h-0 min-w-0 flex-col gap-4 overflow-y-auto pr-1">
          <section className="rounded-md border border-[#e9ebef] bg-white p-4">
            <div className="mb-4 flex items-center gap-2">
              <CalendarClock size={16} className="text-[#667085]" />
              <h2 className="m-0 text-[13px] font-semibold text-[#30343a]">补数配置</h2>
              {selectedWorkflow ? (
                <span className="ml-auto max-w-[50%] truncate text-xs text-[#8a9099]">
                  {selectedWorkflow.name}
                </span>
              ) : null}
            </div>

            <div className="mb-4 flex flex-wrap items-center gap-2">
              <Button
                size="sm"
                variant={dateMode === "range" ? "primary" : "secondary"}
                onClick={() => setDateMode("range")}
              >
                时间范围
              </Button>
              <Button
                size="sm"
                variant={dateMode === "manual" ? "primary" : "secondary"}
                onClick={() => setDateMode("manual")}
              >
                指定日期
              </Button>
            </div>

            {dateMode === "range" ? (
              <div className="grid grid-cols-2 gap-3 max-sm:grid-cols-1">
                <Field>
                  <FieldLabel>开始日期</FieldLabel>
                  <Input size="small" variant="outlined" type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} />
                </Field>
                <Field>
                  <FieldLabel>结束日期</FieldLabel>
                  <Input size="small" variant="outlined" type="date" value={endDate} onChange={(event) => setEndDate(event.target.value)} />
                </Field>
              </div>
            ) : (
              <Field>
                <FieldLabel>补数日期列表</FieldLabel>
                <Textarea
                  size="small"
                  variant="outlined"
                  rows={4}
                  value={manualDates}
                  onValueChange={setManualDates}
                  placeholder={"每行一个日期，也支持逗号分隔\n2026-11-20\n2026-11-21 00:00:00"}
                />
              </Field>
            )}

            <div className="mt-4 grid grid-cols-2 gap-3 max-sm:grid-cols-1 xl:grid-cols-3 2xl:grid-cols-4">
              <Field>
                <FieldLabel>运行模式</FieldLabel>
                <Select
                  size="small"
                  items={RUN_MODE_ITEMS}
                  value={runMode}
                  onValueChange={(value) => setRunMode(String(value) as WorkflowBackfillRunMode)}
                >
                  <SelectTrigger variant="outlined"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {Object.entries(RUN_MODE_ITEMS).map(([value, label]) => (
                      <SelectItem key={value} value={value}>
                        <SelectItemText>{label}</SelectItemText><SelectItemIndicator />
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel>预期并行度</FieldLabel>
                <Input
                  size="small"
                  variant="outlined"
                  type="number"
                  min={runMode === "RUN_MODE_SERIAL" ? 0 : 1}
                  value={parallelism}
                  onChange={(event) => setParallelism(event.target.value)}
                  disabled={runMode === "RUN_MODE_SERIAL"}
                />
              </Field>
              <Field>
                <FieldLabel>依赖补数</FieldLabel>
                <Select
                  size="small"
                  items={DEPENDENCY_ITEMS}
                  value={dependentMode}
                  onValueChange={(value) => setDependentMode(String(value) as "OFF_MODE" | "ALL_DEPENDENT")}
                >
                  <SelectTrigger variant="outlined"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {Object.entries(DEPENDENCY_ITEMS).map(([value, label]) => (
                      <SelectItem key={value} value={value}>
                        <SelectItemText>{label}</SelectItemText><SelectItemIndicator />
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel>执行顺序</FieldLabel>
                <Select
                  size="small"
                  items={ORDER_ITEMS}
                  value={executionOrder}
                  onValueChange={(value) => setExecutionOrder(String(value) as "ASC_ORDER" | "DESC_ORDER")}
                >
                  <SelectTrigger variant="outlined"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {Object.entries(ORDER_ITEMS).map(([value, label]) => (
                      <SelectItem key={value} value={value}>
                        <SelectItemText>{label}</SelectItemText><SelectItemIndicator />
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel>失败策略</FieldLabel>
                <Select
                  size="small"
                  items={FAILURE_ITEMS}
                  value={failureStrategy}
                  onValueChange={(value) => setFailureStrategy(String(value) as "END" | "CONTINUE")}
                >
                  <SelectTrigger variant="outlined"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {Object.entries(FAILURE_ITEMS).map(([value, label]) => (
                      <SelectItem key={value} value={value}>
                        <SelectItemText>{label}</SelectItemText><SelectItemIndicator />
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel>优先级</FieldLabel>
                <Select
                  size="small"
                  items={PRIORITY_ITEMS}
                  value={priority}
                  onValueChange={(value) => setPriority(String(value) as keyof typeof PRIORITY_ITEMS)}
                >
                  <SelectTrigger variant="outlined"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {Object.entries(PRIORITY_ITEMS).map(([value, label]) => (
                      <SelectItem key={value} value={value}>
                        <SelectItemText>{label}</SelectItemText><SelectItemIndicator />
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel>Worker Group</FieldLabel>
                <Select
                  size="small"
                  items={Object.fromEntries(workerGroups.map((group) => [group, group]))}
                  value={workerGroup}
                  onValueChange={(value) => setWorkerGroup(String(value ?? "default"))}
                >
                  <SelectTrigger variant="outlined"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {workerGroups.map((group) => (
                      <SelectItem key={group} value={group}>
                        <SelectItemText>{group}</SelectItemText><SelectItemIndicator />
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>
              <Field>
                <FieldLabel>Environment</FieldLabel>
                <Select
                  size="small"
                  items={Object.fromEntries([
                    ["-1", "不使用环境"],
                    ...environments.map((environment) => [String(environment.code), environment.name]),
                  ])}
                  value={environmentCode}
                  onValueChange={(value) => setEnvironmentCode(String(value ?? "-1"))}
                >
                  <SelectTrigger variant="outlined"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="-1"><SelectItemText>不使用环境</SelectItemText><SelectItemIndicator /></SelectItem>
                    {environments.map((environment) => (
                      <SelectItem key={String(environment.code)} value={String(environment.code)}>
                        <SelectItemText>{environment.name}</SelectItemText><SelectItemIndicator />
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>
            </div>

            <label className="mt-3 flex cursor-pointer items-center gap-2 text-xs text-[#555b65]">
              <input type="checkbox" checked={allLevelDependent} onChange={(event) => setAllLevelDependent(event.target.checked)} />
              递归补齐所有层级依赖
            </label>

            <div className="max-w-4xl">
              <Field>
                <FieldLabel>启动参数</FieldLabel>
              <Textarea
                size="small"
                variant="outlined"
                rows={3}
                value={startParamsText}
                onValueChange={setStartParamsText}
                placeholder={"每行一个参数，例如：\nbiz_date=2026-11-20"}
                />
              </Field>
            </div>

            <div className="mt-4 flex justify-end">
              <Button
                size="sm"
                variant="primary"
                isPending={submitting}
                isDisabled={submitting || !selectedWorkflow || selectedWorkflow.releaseState !== "ONLINE"}
                onClick={() => void submit()}
              >
                <Play size={13} fill="currentColor" />
                {submitting ? "提交中..." : "提交补数"}
              </Button>
            </div>
          </section>

          <section className="rounded-md border border-[#e9ebef] bg-white p-4">
            <div className="mb-3 flex items-center justify-between">
              <h2 className="m-0 text-[13px] font-semibold text-[#30343a]">本次会话提交记录</h2>
              <span className="text-[11px] text-[#8a9099]">刷新页面后不保留</span>
            </div>
            {recentSubmissions.length === 0 ? (
              <div className="rounded-md bg-[#f8f9fb] px-4 py-8 text-center text-xs text-[#8b919b]">
                暂无补数提交记录
              </div>
            ) : (
              <div className="space-y-2">
                {recentSubmissions.map((submission) => (
                  <div key={submission.id} className="flex flex-wrap items-center gap-3 rounded-md border border-[#edf0f3] px-3 py-2.5">
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-xs font-medium text-[#30343a]">{submission.workflowName}</div>
                      <div className="mt-1 text-[11px] text-[#8a9099]">
                        {submission.createdAt} · {submission.instanceIds.length} 个实例 · 工作流 #{submission.workflowCode}
                      </div>
                    </div>
                    <div className="flex flex-wrap gap-1">
                      {submission.instanceIds.map((id) => (
                        <Button key={id} size="sm" variant="secondary" onClick={() => openWorkflowInstance(id, submission.workflowName + " · 补数 #" + id)}>
                          实例 #{id}
                        </Button>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>
        </div>
      </div>
    </OperationsPageShell>
  );
}

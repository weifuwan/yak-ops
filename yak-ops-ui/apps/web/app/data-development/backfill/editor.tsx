import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { CalendarClock, Play } from "lucide-react";
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

import { listEnvironments } from "@/service/environment";
import { listWorkerGroupNames } from "@/service/worker-group";
import type { WorkflowDefinitionRecord } from "@/service/workflow/definition";
import {
  startWorkflowBackfill,
  type WorkflowBackfillRunMode,
} from "@/service/workflow/execution";
import { useDataDevelopmentWorkbench } from "../workbench-context";

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

export function BackfillEditor({
  workflow,
}: {
  workflow: WorkflowDefinitionRecord;
}) {
  const { openWorkflowInstance } = useDataDevelopmentWorkbench();
  const [dateMode, setDateMode] = useState<"range" | "manual">("range");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [manualDates, setManualDates] = useState("");
  const [runMode, setRunMode] =
    useState<WorkflowBackfillRunMode>("RUN_MODE_SERIAL");
  const [parallelism, setParallelism] = useState("0");
  const [dependentMode, setDependentMode] =
    useState<"OFF_MODE" | "ALL_DEPENDENT">("OFF_MODE");
  const [allLevelDependent, setAllLevelDependent] = useState(false);
  const [executionOrder, setExecutionOrder] =
    useState<"ASC_ORDER" | "DESC_ORDER">("ASC_ORDER");
  const [failureStrategy, setFailureStrategy] =
    useState<"END" | "CONTINUE">("END");
  const [priority, setPriority] =
    useState<keyof typeof PRIORITY_ITEMS>("MEDIUM");
  const [workerGroup, setWorkerGroup] = useState("default");
  const [environmentCode, setEnvironmentCode] = useState("-1");
  const [startParamsText, setStartParamsText] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [instanceIds, setInstanceIds] = useState<number[]>([]);

  const workerGroupsQuery = useQuery({
    queryKey: ["data-development", "backfill-editor", "worker-groups"],
    queryFn: ({ signal }) => listWorkerGroupNames(signal),
  });
  const environmentsQuery = useQuery({
    queryKey: ["data-development", "backfill-editor", "environments"],
    queryFn: ({ signal }) =>
      listEnvironments({ pageNo: 1, pageSize: 100 }, signal),
  });

  const workerGroups = useMemo(() => {
    const values = workerGroupsQuery.data ?? [];
    return values.includes("default") ? values : ["default", ...values];
  }, [workerGroupsQuery.data]);
  const environments = environmentsQuery.data?.totalList ?? [];
  const online = workflow.releaseState === "ONLINE";

  const submit = async () => {
    if (!online) {
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
      backfillTime = {
        complementScheduleDateList: Array.from(new Set(dates)).join(","),
      };
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
      const ids = await startWorkflowBackfill(String(workflow.code), {
        backfillTime,
        runMode,
        expectedParallelismNumber:
          runMode === "RUN_MODE_SERIAL" ? 0 : requestedParallelism,
        complementDependentMode: dependentMode,
        allLevelDependent,
        executionOrder,
        failureStrategy,
        workflowInstancePriority: priority,
        workerGroup,
        environmentCode: Number(environmentCode),
        startParams: parseStartParams(startParamsText),
      });
      setInstanceIds(ids);
      toast.success(`补数指令已提交，共生成 ${ids.length} 个工作流实例`);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "补数提交失败");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="h-full min-h-0 w-full min-w-0 overflow-y-auto bg-[#f7f8fa] p-5">
      <div className="mx-auto flex w-full max-w-[1400px] flex-col gap-4">
        <section className="rounded-lg border border-[#e6e9ee] bg-white p-5">
          <div className="mb-5 flex items-start justify-between gap-4">
            <div>
              <div className="flex items-center gap-2 text-sm font-semibold text-[#30343a]">
                <CalendarClock size={16} />
                补数配置
              </div>
              <div className="mt-1 text-xs text-[#8a9099]">
                {workflow.name} · #{workflow.code} · {online ? "已上线" : "未上线"}
              </div>
            </div>
            {!online ? (
              <span className="rounded bg-[#f5f5f5] px-2 py-1 text-[11px] text-[#999]">
                当前工作流不可补数
              </span>
            ) : null}
          </div>

          <div className="mb-4 flex items-center gap-2">
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
            <div className="grid grid-cols-2 gap-4 max-md:grid-cols-1">
              <Field>
                <FieldLabel>开始日期</FieldLabel>
                <Input
                  size="small"
                  variant="outlined"
                  type="date"
                  value={startDate}
                  onChange={(event) => setStartDate(event.target.value)}
                />
              </Field>
              <Field>
                <FieldLabel>结束日期</FieldLabel>
                <Input
                  size="small"
                  variant="outlined"
                  type="date"
                  value={endDate}
                  onChange={(event) => setEndDate(event.target.value)}
                />
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

          <div className="mt-4 grid grid-cols-2 gap-4 xl:grid-cols-3 2xl:grid-cols-4 max-md:grid-cols-1">
            <Field>
              <FieldLabel>运行模式</FieldLabel>
              <Select
                size="small"
                items={RUN_MODE_ITEMS}
                value={runMode}
                onValueChange={(value) =>
                  setRunMode(String(value) as WorkflowBackfillRunMode)
                }
              >
                <SelectTrigger variant="outlined"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {Object.entries(RUN_MODE_ITEMS).map(([value, label]) => (
                    <SelectItem key={value} value={value}>
                      <SelectItemText>{label}</SelectItemText>
                      <SelectItemIndicator />
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
                onValueChange={(value) =>
                  setDependentMode(String(value) as "OFF_MODE" | "ALL_DEPENDENT")
                }
              >
                <SelectTrigger variant="outlined"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {Object.entries(DEPENDENCY_ITEMS).map(([value, label]) => (
                    <SelectItem key={value} value={value}>
                      <SelectItemText>{label}</SelectItemText>
                      <SelectItemIndicator />
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
                onValueChange={(value) =>
                  setExecutionOrder(String(value) as "ASC_ORDER" | "DESC_ORDER")
                }
              >
                <SelectTrigger variant="outlined"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {Object.entries(ORDER_ITEMS).map(([value, label]) => (
                    <SelectItem key={value} value={value}>
                      <SelectItemText>{label}</SelectItemText>
                      <SelectItemIndicator />
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
                onValueChange={(value) =>
                  setFailureStrategy(String(value) as "END" | "CONTINUE")
                }
              >
                <SelectTrigger variant="outlined"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {Object.entries(FAILURE_ITEMS).map(([value, label]) => (
                    <SelectItem key={value} value={value}>
                      <SelectItemText>{label}</SelectItemText>
                      <SelectItemIndicator />
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
                onValueChange={(value) =>
                  setPriority(String(value) as keyof typeof PRIORITY_ITEMS)
                }
              >
                <SelectTrigger variant="outlined"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {Object.entries(PRIORITY_ITEMS).map(([value, label]) => (
                    <SelectItem key={value} value={value}>
                      <SelectItemText>{label}</SelectItemText>
                      <SelectItemIndicator />
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
                      <SelectItemText>{group}</SelectItemText>
                      <SelectItemIndicator />
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
                  ...environments.map((environment) => [
                    String(environment.code),
                    environment.name,
                  ]),
                ])}
                value={environmentCode}
                onValueChange={(value) =>
                  setEnvironmentCode(String(value ?? "-1"))
                }
              >
                <SelectTrigger variant="outlined"><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="-1">
                    <SelectItemText>不使用环境</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                  {environments.map((environment) => (
                    <SelectItem
                      key={String(environment.code)}
                      value={String(environment.code)}
                    >
                      <SelectItemText>{environment.name}</SelectItemText>
                      <SelectItemIndicator />
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </Field>
          </div>

          <label className="mt-4 flex cursor-pointer items-center gap-2 text-xs text-[#555b65]">
            <input
              type="checkbox"
              checked={allLevelDependent}
              onChange={(event) => setAllLevelDependent(event.target.checked)}
            />
            递归补齐所有层级依赖
          </label>

          <div className="mt-4 max-w-4xl">
            <Field>
              <FieldLabel>启动参数</FieldLabel>
              <Textarea
                size="small"
                variant="outlined"
                rows={4}
                value={startParamsText}
                onValueChange={setStartParamsText}
                placeholder={"每行一个参数，例如：\nbiz_date=2026-11-20"}
              />
            </Field>
          </div>

          <div className="mt-5 flex justify-end">
            <Button
              size="sm"
              variant="primary"
              isPending={submitting}
              isDisabled={submitting || !online}
              onClick={() => void submit()}
            >
              <Play size={13} fill="currentColor" />
              {submitting ? "提交中..." : "提交补数"}
            </Button>
          </div>
        </section>

        {instanceIds.length > 0 ? (
          <section className="rounded-lg border border-[#e6e9ee] bg-white p-5">
            <div className="mb-3 text-sm font-semibold text-[#30343a]">
              本次提交实例
            </div>
            <div className="flex flex-wrap gap-2">
              {instanceIds.map((id) => (
                <Button
                  key={id}
                  size="sm"
                  variant="secondary"
                  onClick={() =>
                    openWorkflowInstance(id, workflow.name + " · 补数 #" + id)
                  }
                >
                  实例 #{id}
                </Button>
              ))}
            </div>
          </section>
        ) : null}
      </div>
    </div>
  );
}

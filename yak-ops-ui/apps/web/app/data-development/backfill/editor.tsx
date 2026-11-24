import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Braces,
  CalendarClock,
  GitBranch,
  Play,
  Settings2,
} from "lucide-react";
import {
  Alert,
  Badge,
  Button,
  Card,
  CardContent,
  CardHeader,
  Checkbox,
  DateField,
  DateI18nProvider,
  DateRangePicker,
  Field,
  FieldDescription,
  FieldLabel,
  Input,
  RangeCalendar,
  Segmented,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
  Textarea,
  toast,
  type DateRange,
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
const DATE_MODE_OPTIONS = [
  { value: "range", label: "时间范围" },
  { value: "manual", label: "指定日期" },
] as const;

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
  const [dateRange, setDateRange] = useState<DateRange | null>(null);
  const [manualDates, setManualDates] = useState("");
  const [runMode, setRunMode] =
    useState<WorkflowBackfillRunMode>("RUN_MODE_SERIAL");
  const [parallelism, setParallelism] = useState("0");
  const [dependentMode, setDependentMode] =
    useState<"OFF_MODE" | "ALL_DEPENDENT">("OFF_MODE");
  const [allLevelDependent, setAllLevelDependent] = useState(false);
  const [executionOrder, setExecutionOrder] =
    useState<"ASC_ORDER" | "DESC_ORDER">("ASC_ORDER");
  const [failureStrategy, setFailureStrategy] = useState<"END" | "CONTINUE">("END");
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
      const startDate = dateRange?.start.toString() ?? "";
      const endDate = dateRange?.end.toString() ?? "";
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
    <div className="h-full min-h-0 w-full min-w-0 overflow-y-auto bg-[var(--yak-color-surface)] p-4 md:p-6">
      <div className="mx-auto flex w-full max-w-[1280px] flex-col gap-4">
        <Card className="shadow-[var(--yak-shadow-sm)]">
          <CardHeader
            className="items-center px-5 py-4"
            title={
              <div className="flex flex-wrap items-center gap-2.5">
                <span className="flex size-8 items-center justify-center rounded-lg bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]">
                  <CalendarClock size={17} />
                </span>
                <span className="text-[15px] font-semibold">补数配置</span>
                <Badge tone={online ? "success" : "neutral"}>
                  {online ? "已上线" : "未上线"}
                </Badge>
              </div>
            }
            description={`${workflow.name} · 工作流 #${workflow.code}`}
          />

          <CardContent className="space-y-6 p-5 md:p-6">
            {!online ? (
              <Alert>
                当前工作流尚未上线，补数仅支持已上线的工作流。请先发布工作流后再提交补数。
              </Alert>
            ) : null}

            <section className="rounded-lg border border-[var(--yak-components-panel-border)] bg-[var(--yak-color-surface)] p-4 md:p-5">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="flex items-start gap-2.5">
                  <span className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-md bg-[var(--yak-components-panel-bg)] text-[var(--yak-components-muted-text)] shadow-sm">
                    <CalendarClock size={15} />
                  </span>
                  <div>
                    <h3 className="m-0 text-sm font-semibold text-[var(--yak-components-panel-text)]">
                      补数日期
                    </h3>
                    <p className="mb-0 mt-1 text-xs leading-5 text-[var(--yak-components-muted-text)]">
                      选择需要重新执行的业务日期
                    </p>
                  </div>
                </div>
                <Segmented
                  aria-label="补数日期方式"
                  value={dateMode}
                  options={DATE_MODE_OPTIONS}
                  onValueChange={(value) => setDateMode(value)}
                />
              </div>

              {dateMode === "range" ? (
                <Field className="mt-4 max-w-[560px]">
                  <FieldLabel>补数日期范围</FieldLabel>
                  <DateI18nProvider locale="zh-CN">
                    <DateRangePicker
                      aria-label="补数日期范围"
                      value={dateRange}
                      onChange={setDateRange}
                      className="w-full"
                    >
                      <DateField.Group fullWidth variant="outlined">
                        <DateField.Input slot="start" className="min-w-0 px-2">
                          {(segment) => <DateField.Segment segment={segment} />}
                        </DateField.Input>
                        <DateRangePicker.RangeSeparator />
                        <DateField.Input slot="end" className="min-w-0 px-2">
                          {(segment) => <DateField.Segment segment={segment} />}
                        </DateField.Input>
                        <DateField.Suffix>
                          <DateRangePicker.Trigger aria-label="打开补数日期范围选择器">
                            <DateRangePicker.TriggerIndicator />
                          </DateRangePicker.Trigger>
                        </DateField.Suffix>
                      </DateField.Group>
                      <DateRangePicker.Popover className="w-auto">
                        <RangeCalendar
                          aria-label="补数日期范围"
                          firstDayOfWeek="mon"
                        >
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
                  <FieldDescription>
                    将按工作流已发布的 Cron 调度规则计算需要补数的实例
                  </FieldDescription>
                </Field>
              ) : (
                <Field className="mt-4 max-w-[720px]">
                  <FieldLabel>指定补数日期</FieldLabel>
                  <Textarea
                    size="small"
                    variant="outlined"
                    rows={4}
                    value={manualDates}
                    onValueChange={setManualDates}
                    placeholder={"每行一个日期，也支持逗号分隔\nYYYY-MM-DD\nYYYY-MM-DD HH:mm:ss"}
                  />
                  <FieldDescription>
                    支持多日期输入，日期之间可使用换行或逗号分隔
                  </FieldDescription>
                </Field>
              )}
            </section>

            <section>
              <div className="mb-4 flex items-start gap-2.5">
                <span className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-md bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]">
                  <Settings2 size={15} />
                </span>
                <div>
                  <h3 className="m-0 text-sm font-semibold text-[var(--yak-components-panel-text)]">
                    执行配置
                  </h3>
                  <p className="mb-0 mt-1 text-xs leading-5 text-[var(--yak-components-muted-text)]">
                    设置补数实例的运行方式、依赖策略与调度资源
                  </p>
                </div>
              </div>

              <div className="grid grid-cols-1 gap-x-5 gap-y-4 sm:grid-cols-2 xl:grid-cols-4">
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
                    <SelectTrigger variant="outlined" className="w-full">
                      <SelectValue />
                    </SelectTrigger>
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
                  <FieldDescription>
                    {runMode === "RUN_MODE_SERIAL" ? "串行模式固定为 0" : "并行度至少为 1"}
                  </FieldDescription>
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
                    <SelectTrigger variant="outlined" className="w-full">
                      <SelectValue />
                    </SelectTrigger>
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
                    <SelectTrigger variant="outlined" className="w-full">
                      <SelectValue />
                    </SelectTrigger>
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
                    <SelectTrigger variant="outlined" className="w-full">
                      <SelectValue />
                    </SelectTrigger>
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
                    <SelectTrigger variant="outlined" className="w-full">
                      <SelectValue />
                    </SelectTrigger>
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
                    <SelectTrigger variant="outlined" className="w-full">
                      <SelectValue />
                    </SelectTrigger>
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
                    onValueChange={(value) => setEnvironmentCode(String(value ?? "-1"))}
                  >
                    <SelectTrigger variant="outlined" className="w-full">
                      <SelectValue />
                    </SelectTrigger>
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
            </section>

            <section className="grid grid-cols-1 gap-4 border-t border-[var(--yak-components-panel-border)] pt-5 xl:grid-cols-[minmax(260px,0.8fr)_minmax(0,1.2fr)]">
              <div className="rounded-lg border border-[var(--yak-components-panel-border)] bg-[var(--yak-color-surface)] p-4">
                <div className="flex items-start gap-2.5">
                  <span className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-md bg-[var(--yak-components-panel-bg)] text-[var(--yak-components-muted-text)] shadow-sm">
                    <GitBranch size={15} />
                  </span>
                  <div>
                    <h3 className="m-0 text-sm font-semibold text-[var(--yak-components-panel-text)]">
                      依赖控制
                    </h3>
                    <p className="mb-0 mt-1 text-xs leading-5 text-[var(--yak-components-muted-text)]">
                      控制是否递归补齐上游工作流依赖
                    </p>
                  </div>
                </div>
                <label className="mt-4 flex cursor-pointer items-start gap-2.5 rounded-md border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] px-3 py-3 transition-colors hover:bg-[var(--yak-color-hover)]">
                  <Checkbox
                    checked={allLevelDependent}
                    onCheckedChange={(checked) => setAllLevelDependent(checked === true)}
                    className="mt-0.5"
                  />
                  <span className="min-w-0">
                    <span className="block text-xs font-medium text-[var(--yak-components-panel-text)]">
                      递归补齐所有层级依赖
                    </span>
                    <span className="mt-1 block text-[11px] leading-4 text-[var(--yak-components-muted-text)]">
                      开启后将继续补齐依赖工作流的上游层级
                    </span>
                  </span>
                </label>
              </div>

              <div className="rounded-lg border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-4">
                <div className="mb-3 flex items-center gap-2">
                  <span className="flex size-7 shrink-0 items-center justify-center rounded-md bg-[var(--yak-color-primary-subtle)] text-[var(--yak-color-primary)]">
                    <Braces size={15} />
                  </span>
                  <div>
                    <h3 className="m-0 text-sm font-semibold text-[var(--yak-components-panel-text)]">
                      启动参数
                    </h3>
                    <p className="mb-0 mt-0.5 text-[11px] text-[var(--yak-components-muted-text)]">
                      每行填写一个参数，格式为 key=value
                    </p>
                  </div>
                </div>
                <Field>
                  <Textarea
                    size="small"
                    variant="outlined"
                    rows={4}
                    value={startParamsText}
                    onValueChange={setStartParamsText}
                    placeholder={"biz_date=2026-11-24\nregion=cn-east"}
                  />
                </Field>
              </div>
            </section>
          </CardContent>

          <div className="flex items-center justify-end border-t border-[var(--yak-components-panel-border)] bg-[var(--yak-color-surface)] px-5 py-3.5">
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
        </Card>

        {instanceIds.length > 0 ? (
          <Card className="shadow-[var(--yak-shadow-sm)]">
            <CardHeader
              title="本次提交实例"
              description={`已生成 ${instanceIds.length} 个工作流实例`}
            />
            <CardContent className="flex flex-wrap gap-2">
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
            </CardContent>
          </Card>
        ) : null}
      </div>
    </div>
  );
}

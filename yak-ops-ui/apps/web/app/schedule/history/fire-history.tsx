import {
  Button,
  Field,
  FieldError,
  FieldLabel,
  Input,
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
import { useState } from "react";
import {
  scheduleErrorMessage,
  type ScheduleFireOutcome,
  type ScheduleFireRecord,
} from "@/service/schedule";
import { FIRE_OUTCOMES, formatScheduleTime, scheduleReasonText } from "../schedule-presentation";
import { useFireHistory } from "./use-fire-history";

export function ScheduleFireHistory({
  scheduleId,
  onViewInstance,
}: {
  scheduleId: string;
  onViewInstance: (id: string) => void;
}) {
  const [pageNo, setPageNo] = useState(1);
  const [outcome, setOutcome] = useState<ScheduleFireOutcome | "ALL">("ALL");
  const [from, setFrom] = useState("");
  const [until, setUntil] = useState("");
  const invalidRange = Boolean(from && until && from >= until);
  const query = useFireHistory(scheduleId, { pageNo, outcome, from, until });
  const page = query.data;
  const loading = !invalidRange && (query.isPending || query.isFetching);
  const error = query.isError ? scheduleErrorMessage(query.error) : "";

  const columns: TableColumns<ScheduleFireRecord> = [
    {
      key: "scheduledTime",
      title: "计划时间（UTC）",
      minWidth: 210,
      render: (_value, row) => formatScheduleTime(row.scheduledTime),
    },
    {
      key: "fireTime",
      title: "触发时间（UTC）",
      minWidth: 210,
      render: (_value, row) => formatScheduleTime(row.fireTime),
    },
    {
      key: "outcome",
      title: "提交结果",
      width: 100,
      render: (_value, row) => FIRE_OUTCOMES[row.outcome] || "未知",
    },
    {
      key: "reason",
      title: "原因",
      minWidth: 150,
      render: (_value, row) => scheduleReasonText(row.reason),
    },
    {
      key: "instanceId",
      title: "执行实例",
      width: 110,
      render: (_value, row) =>
        row.outcome === "ADMITTED" && row.instanceId ? (
          <Button size="sm" variant="ghost" onClick={() => onViewInstance(row.instanceId!)}>
            查看实例
          </Button>
        ) : (
          "—"
        ),
    },
  ];
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end gap-3">
        <Field>
          <FieldLabel>提交结果</FieldLabel>
          <Select
            size="small"
            value={outcome}
            items={{ ALL: "全部", ...FIRE_OUTCOMES }}
            onValueChange={(value) => {
              setOutcome(value as ScheduleFireOutcome | "ALL");
              setPageNo(1);
            }}
          >
            <SelectTrigger variant="outlined" className="min-w-28">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {Object.entries({ ALL: "全部", ...FIRE_OUTCOMES }).map(([value, label]) => (
                <SelectItem key={value} value={value}>
                  <SelectItemText>{label}</SelectItemText>
                  <SelectItemIndicator />
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Field>
        <Field>
          <FieldLabel>起始计划时间（UTC）</FieldLabel>
          <Input
            size="small"
            variant="outlined"
            type="datetime-local"
            value={from}
            onChange={(event) => {
              setFrom(event.target.value);
              setPageNo(1);
            }}
          />
        </Field>
        <Field invalid={invalidRange}>
          <FieldLabel>结束计划时间（UTC，不含）</FieldLabel>
          <Input
            size="small"
            variant="outlined"
            type="datetime-local"
            value={until}
            onChange={(event) => {
              setUntil(event.target.value);
              setPageNo(1);
            }}
          />
          {invalidRange ? <FieldError match>结束时间须晚于开始时间</FieldError> : null}
        </Field>
        <Button variant="secondary"
          size="sm"
          isDisabled={loading || invalidRange}
          onClick={() => void query.refetch()}
        >
          刷新
        </Button>
      </div>
      {error ? (
        <div role="alert" className="text-xs text-[var(--yak-color-danger)]">
          {error}
        </div>
      ) : null}
      <Table<ScheduleFireRecord>
        columns={columns}
        dataSource={page?.bizData || []}
        rowKey="id"
        size="small"
        loading={loading}
        scroll={{ x: 820 }}
        emptyText={error ? "无法加载触发记录" : "暂无触发记录"}
        pagination={
          page && page.pagination.total > 0
            ? { current: pageNo, pageSize: 20, total: page.pagination.total, onChange: setPageNo }
            : false
        }
      />
    </div>
  );
}

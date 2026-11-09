import { useQuery } from "@tanstack/react-query";
import {
  Badge,
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
  type BadgeProps,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { useMemo, useState } from "react";

import { useAuth } from "@/hooks/use-auth";
import { useWorkspace } from "@/hooks/use-workspace";
import {
  listAlertGroups,
  listAlertRecords,
  type AlertGroupRecord,
  type AlertRecord,
  type AlertRecordStatus,
  type AlertRecordType,
} from "@/service/alert";

const PAGE_SIZE = 20;

const STATUS_LABELS: Record<AlertRecordStatus, string> = {
  WAIT_EXECUTION: "待发送",
  EXECUTION_SUCCESS: "发送成功",
  EXECUTION_FAILURE: "发送失败",
  EXECUTION_PARTIAL_SUCCESS: "部分成功",
};

const STATUS_TONES: Record<AlertRecordStatus, BadgeProps["tone"]> = {
  WAIT_EXECUTION: "warning",
  EXECUTION_SUCCESS: "success",
  EXECUTION_FAILURE: "danger",
  EXECUTION_PARTIAL_SUCCESS: "warning",
};

const TYPE_LABELS: Record<AlertRecordType, string> = {
  WORKFLOW_INSTANCE_FAILURE: "工作流失败",
  WORKFLOW_INSTANCE_SUCCESS: "工作流成功",
  WORKFLOW_INSTANCE_BLOCKED: "工作流阻塞",
  WORKFLOW_INSTANCE_TIMEOUT: "工作流超时",
  FAULT_TOLERANCE_WARNING: "服务异常",
  TASK_FAILURE: "任务失败",
  TASK_SUCCESS: "任务成功",
  TASK_TIMEOUT: "任务超时",
};

const STATUS_OPTIONS: Record<string, string> = {
  ALL: "全部状态",
  ...STATUS_LABELS,
};

const TYPE_OPTIONS: Record<string, string> = {
  ALL: "全部类型",
  ...TYPE_LABELS,
};

function parseAlertContent(content?: string | null): Record<string, unknown> | undefined {
  if (!content) return undefined;
  try {
    const parsed: unknown = JSON.parse(content);
    const value = Array.isArray(parsed) ? parsed[0] : parsed;
    return value && typeof value === "object" ? (value as Record<string, unknown>) : undefined;
  } catch {
    return undefined;
  }
}

function contentTitle(record: AlertRecord): string {
  const content = parseAlertContent(record.content);
  const taskName = content?.taskName;
  if (typeof taskName === "string" && taskName) return taskName;
  const workflowName = content?.workflowInstanceName;
  if (typeof workflowName === "string" && workflowName) return workflowName;
  return record.title || "告警";
}

function contentSummary(record: AlertRecord): string {
  const content = parseAlertContent(record.content);
  const event = content?.event;
  const owner = content?.owner;
  const parts = [
    typeof event === "string" ? event : undefined,
    typeof owner === "string" ? "负责人 " + owner : undefined,
  ].filter(Boolean);
  return parts.length ? parts.join(" · ") : record.title || "-";
}

function pretty(value?: string | null): string {
  if (!value) return "-";
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function AlertStatusBadge({ status }: { status?: AlertRecordStatus | null }) {
  if (!status) return <span>-</span>;
  return <Badge tone={STATUS_TONES[status]}>{STATUS_LABELS[status]}</Badge>;
}

export function AlertRecordsPanel() {
  const { currentUser } = useAuth();
  const { currentWorkspace } = useWorkspace();
  const [pageNo, setPageNo] = useState(1);
  const [keywordInput, setKeywordInput] = useState("");
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState<AlertRecordStatus>();
  const [type, setType] = useState<AlertRecordType>();
  const [detail, setDetail] = useState<AlertRecord>();

  const scope = currentUser?.id && currentWorkspace?.id
    ? currentUser.id + ":" + currentWorkspace.id
    : "";

  const recordsQuery = useQuery({
    queryKey: ["management", "alerts", "records", scope, pageNo, keyword, status, type],
    queryFn: ({ signal }) =>
      listAlertRecords(
        {
          pageNo,
          pageSize: PAGE_SIZE,
          keyword: keyword || undefined,
          alertStatus: status,
          alertType: type,
        },
        signal,
      ),
    enabled: Boolean(scope),
  });

  const groupsQuery = useQuery({
    queryKey: ["management", "alerts", "groups", currentUser?.id ?? ""],
    queryFn: listAlertGroups,
    enabled: Boolean(currentUser?.id),
  });

  const groupNameById = useMemo(
    () => new Map((groupsQuery.data ?? []).map((group: AlertGroupRecord) => [group.id, group.groupName])),
    [groupsQuery.data],
  );

  const records = recordsQuery.data?.totalList ?? [];
  const total = recordsQuery.data?.total ?? 0;

  const columns: TableColumns<AlertRecord> = [
    {
      key: "time",
      title: "告警时间",
      dataIndex: "createTime",
      width: 180,
      render: (_value, record) => record.createTime || "-",
    },
    {
      key: "source",
      title: "来源",
      minWidth: 260,
      render: (_value, record) => (
        <div className="min-w-0">
          <div className="truncate text-[13px] font-medium text-[var(--yak-components-panel-text)]">
            {contentTitle(record)}
          </div>
          <div className="mt-0.5 truncate text-xs text-[var(--yak-components-muted-text)]">
            {contentSummary(record)}
          </div>
        </div>
      ),
    },
    {
      key: "type",
      title: "类型",
      width: 130,
      render: (_value, record) =>
        record.alertType ? TYPE_LABELS[record.alertType] : "-",
    },
    {
      key: "group",
      title: "通知组",
      width: 180,
      render: (_value, record) =>
        record.alertGroupId ? groupNameById.get(record.alertGroupId) || "#" + record.alertGroupId : "-",
    },
    {
      key: "status",
      title: "发送状态",
      width: 120,
      render: (_value, record) => <AlertStatusBadge status={record.alertStatus} />,
    },
    {
      key: "workflow",
      title: "工作流实例",
      width: 130,
      render: (_value, record) =>
        record.workflowInstanceId ? "#" + record.workflowInstanceId : "-",
    },
    {
      key: "actions",
      title: "操作",
      width: 90,
      align: "center",
      render: (_value, record) => (
        <Button
          size="sm"
          variant="ghost"
          className="px-1 text-xs font-normal"
          onClick={() => setDetail(record)}
        >
          详情
        </Button>
      ),
    },
  ];

  const applyKeyword = () => {
    setPageNo(1);
    setKeyword(keywordInput.trim());
  };

  return (
    <>
      <div className="mb-4 flex min-w-0 items-center gap-3 overflow-x-auto">
        <div className="flex min-w-max items-center gap-2">
          <div className="w-[240px] shrink-0">
            <Input
              size="small"
              variant="outlined"
              value={keywordInput}
              placeholder="搜索告警标题或内容"
              onChange={(event) => setKeywordInput(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") applyKeyword();
              }}
            />
          </div>
          <Button size="sm" variant="secondary" onClick={applyKeyword}>
            搜索
          </Button>
          <div className="w-[150px] shrink-0">
            <Select<string>
              size="small"
              items={STATUS_OPTIONS}
              value={status ?? "ALL"}
              onValueChange={(value) => {
                setPageNo(1);
                setStatus(value && value !== "ALL" ? (value as AlertRecordStatus) : undefined);
              }}
            >
              <SelectTrigger variant="outlined" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {Object.entries(STATUS_OPTIONS).map(([value, label]) => (
                  <SelectItem key={value} value={value}>
                    <SelectItemText>{label}</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="w-[160px] shrink-0">
            <Select<string>
              size="small"
              items={TYPE_OPTIONS}
              value={type ?? "ALL"}
              onValueChange={(value) => {
                setPageNo(1);
                setType(value && value !== "ALL" ? (value as AlertRecordType) : undefined);
              }}
            >
              <SelectTrigger variant="outlined" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {Object.entries(TYPE_OPTIONS).map(([value, label]) => (
                  <SelectItem key={value} value={value}>
                    <SelectItemText>{label}</SelectItemText>
                    <SelectItemIndicator />
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
        <Button
          className="ml-auto shrink-0"
          size="sm"
          variant="secondary"
          isPending={recordsQuery.isFetching}
          onClick={() => void recordsQuery.refetch()}
        >
          刷新
        </Button>
      </div>

      {recordsQuery.isError ? (
        <div
          role="alert"
          className="mb-4 flex items-center gap-3 text-sm text-[var(--yak-components-danger-text)]"
        >
          告警记录加载失败
          <Button size="sm" variant="secondary" onClick={() => void recordsQuery.refetch()}>
            重试
          </Button>
        </div>
      ) : null}

      <Table<AlertRecord>
        className="min-h-full"
        columns={columns}
        dataSource={records}
        rowKey={(record) => String(record.id)}
        loading={recordsQuery.isPending || recordsQuery.isFetching}
        bordered
        size="medium"
        scroll={{ x: 1100 }}
        emptyText="暂无告警记录"
        pagination={
          total > 0
            ? {
                current: pageNo,
                pageSize: PAGE_SIZE,
                total,
                disabled: recordsQuery.isFetching,
                onChange: setPageNo,
              }
            : false
        }
      />

      <Modal
        open={Boolean(detail)}
        centered
        width={760}
        title="告警详情"
        onClose={() => setDetail(undefined)}
        footer={
          <Button size="sm" variant="secondary" onClick={() => setDetail(undefined)}>
            关闭
          </Button>
        }
      >
        {detail ? (
          <div className="space-y-4 text-sm">
            <div className="grid grid-cols-[100px_minmax(0,1fr)] gap-x-4 gap-y-2">
              <div className="text-[var(--yak-components-muted-text)]">告警来源</div>
              <div>{contentTitle(detail)}</div>
              <div className="text-[var(--yak-components-muted-text)]">告警类型</div>
              <div>{detail.alertType ? TYPE_LABELS[detail.alertType] : "-"}</div>
              <div className="text-[var(--yak-components-muted-text)]">发送状态</div>
              <div><AlertStatusBadge status={detail.alertStatus} /></div>
              <div className="text-[var(--yak-components-muted-text)]">通知组</div>
              <div>
                {detail.alertGroupId
                  ? groupNameById.get(detail.alertGroupId) || "#" + detail.alertGroupId
                  : "-"}
              </div>
              <div className="text-[var(--yak-components-muted-text)]">工作流实例</div>
              <div>{detail.workflowInstanceId ? "#" + detail.workflowInstanceId : "-"}</div>
              <div className="text-[var(--yak-components-muted-text)]">告警时间</div>
              <div>{detail.createTime || "-"}</div>
            </div>

            <div>
              <div className="mb-1.5 text-xs font-medium text-[var(--yak-components-muted-text)]">
                告警内容
              </div>
              <pre className="max-h-[260px] overflow-auto whitespace-pre-wrap break-words rounded-md border border-[var(--yak-components-panel-border)] bg-[var(--yak-color-hover)] p-3 text-xs leading-5">
                {pretty(detail.content)}
              </pre>
            </div>

            <div>
              <div className="mb-1.5 text-xs font-medium text-[var(--yak-components-muted-text)]">
                发送日志
              </div>
              <pre className="max-h-[220px] overflow-auto whitespace-pre-wrap break-words rounded-md border border-[var(--yak-components-panel-border)] bg-[var(--yak-color-hover)] p-3 text-xs leading-5">
                {pretty(detail.log)}
              </pre>
            </div>
          </div>
        ) : null}
      </Modal>
    </>
  );
}

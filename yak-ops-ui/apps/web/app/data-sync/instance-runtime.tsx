import {
  Badge,
  Button,
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
import { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";

import {
  cancelDataSyncInstance,
  getDataSyncInstance,
  listDataSyncAttempts,
  listDataSyncInstances,
  type DataSyncAttemptRecord,
  type DataSyncInstanceRecord,
  type DataSyncInstanceStatus,
  type DataSyncType,
} from "@/service/data-sync";

import {
  DataSyncExecutionDetailContent,
  dataSyncDurationText,
  dataSyncInstanceStatusMeta,
  dataSyncTriggerText,
  isActiveDataSyncInstance,
} from "./execution-detail";

const PAGE_SIZE = 20;
const POLL_INTERVAL_MILLIS = 2000;

interface DataSyncInstancesProps {
  syncType: DataSyncType;
  basePath: string;
  taskId?: string;
}

export function DataSyncInstances({ syncType, basePath, taskId }: DataSyncInstancesProps) {
  const realtime = syncType === "REALTIME";
  const navigate = useNavigate();
  const [records, setRecords] = useState<DataSyncInstanceRecord[]>([]);
  const [keyword, setKeyword] = useState("");
  const [status, setStatus] = useState<DataSyncInstanceStatus | "ALL">("ALL");
  const [pageNo, setPageNo] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [cancelingId, setCancelingId] = useState<string>();

  const loadInstances = useCallback(async () => {
    setLoading(true);
    try {
      const result = await listDataSyncInstances({
        pageNo,
        pageSize: PAGE_SIZE,
        taskId,
        syncType,
        keyword: keyword.trim() || undefined,
        status: status === "ALL" ? undefined : status,
      });
      setRecords(result?.bizData || []);
      setTotal(result?.pagination?.total || 0);
    } finally {
      setLoading(false);
    }
  }, [keyword, pageNo, status, syncType, taskId]);

  useEffect(() => {
    const timer = window.setTimeout(() => void loadInstances(), keyword.trim() ? 250 : 0);
    return () => window.clearTimeout(timer);
  }, [keyword, loadInstances]);

  useEffect(() => {
    if (!records.some(isActiveDataSyncInstance)) return;
    const timer = window.setInterval(() => void loadInstances(), POLL_INTERVAL_MILLIS);
    return () => window.clearInterval(timer);
  }, [loadInstances, records]);

  const cancel = async (record: DataSyncInstanceRecord) => {
    if (cancelingId) return;
    setCancelingId(record.id);
    try {
      await cancelDataSyncInstance(record.id);
      toast.success(realtime ? "实时同步实例已停止" : "同步实例已停止");
      await loadInstances();
    } finally {
      setCancelingId(undefined);
    }
  };

  const columns: TableColumns<DataSyncInstanceRecord> = [
    {
      key: "id",
      title: "实例 ID",
      minWidth: 180,
      render: (_value, record) => (
        <button
          type="button"
          className="cursor-pointer border-0 bg-transparent p-0 text-left text-[13px] text-[var(--yak-color-primary)]"
          onClick={() => navigate(`${basePath}/instances/${record.id}`)}
        >
          {record.id}
        </button>
      ),
    },
    {
      key: "task",
      title: "任务",
      minWidth: 220,
      render: (_value, record) => (
        <div>
          <div className="text-[13px] font-medium text-[#344054]">{record.taskName}</div>
          <div className="text-xs text-[#98a2b3]">v{record.taskVersion}</div>
        </div>
      ),
    },
    {
      key: "status",
      title: "状态",
      width: 110,
      render: (_value, record) => {
        const meta = dataSyncInstanceStatusMeta(record.status, realtime);
        return <Badge tone={meta.tone}>{meta.label}</Badge>;
      },
    },
    {
      key: "triggerType",
      title: "启动方式",
      width: 100,
      render: (_value, record) =>
        record.triggerType === "MANUAL"
          ? "手动"
          : record.triggerType === "SCHEDULE"
            ? "调度"
            : record.triggerType === "AUTO_RECOVERY"
              ? "自动恢复"
              : record.triggerType,
    },
    {
      key: "readRows",
      title: realtime ? "读取事件" : "读取",
      width: 110,
      align: "right",
      render: (_value, record) => (record.readRows ?? 0).toLocaleString(),
    },
    {
      key: "writeRows",
      title: realtime ? "写入事件" : "写入",
      width: 110,
      align: "right",
      render: (_value, record) => (record.writeRows ?? 0).toLocaleString(),
    },
    {
      key: "startTime",
      title: "开始时间",
      width: 180,
      render: (_value, record) => record.startTime || record.createTime || "-",
    },
    {
      key: "duration",
      title: "耗时",
      width: 100,
      render: (_value, record) => dataSyncDurationText(record),
    },
    {
      key: "actions",
      title: "操作",
      width: 130,
      align: "center",
      render: (_value, record) =>
        isActiveDataSyncInstance(record) ? (
          <Button
            size="small"
            variant="ghost"
            loading={cancelingId === record.id}
            className="px-1 text-xs font-normal text-[#d92d20]"
            onClick={() => void cancel(record)}
          >
            停止
          </Button>
        ) : (
          <Button
            size="small"
            variant="ghost"
            className="px-1 text-xs font-normal text-[#667085] hover:text-[var(--yak-color-primary)]"
            onClick={() => navigate(`${basePath}/instances/${record.id}`)}
          >
            详情
          </Button>
        ),
    },
  ];

  const canceledLabel = realtime ? "已停止" : "已取消";
  const statusOptions: Array<[DataSyncInstanceStatus | "ALL", string]> = [
    ["ALL", "全部状态"],
    ["PENDING", "等待"],
    ["RUNNING", "运行中"],
    ["RETRY_WAITING", "等待重试"],
    ["SUCCEEDED", "成功"],
    ["FAILED", "失败"],
    ["CANCELED", canceledLabel],
    ["LOST", "已丢失"],
  ];
  const statusItems = Object.fromEntries(statusOptions);

  return (
    <div className="flex min-h-0 flex-1 flex-col">
      <div className="flex shrink-0 flex-wrap items-center gap-2">
        <div className="w-[300px]">
          <Input
            size="small"
            variant="outlined"
            value={keyword}
            placeholder="搜索任务名称"
            onChange={(event) => {
              setKeyword(event.target.value);
              setPageNo(1);
            }}
          />
        </div>
        <div className="w-[150px]">
          <Select
            size="small"
            items={statusItems}
            value={status}
            onValueChange={(value) => {
              setStatus(String(value || "ALL") as DataSyncInstanceStatus | "ALL");
              setPageNo(1);
            }}
          >
            <SelectTrigger variant="outlined">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {statusOptions.map(([value, label]) => (
                <SelectItem key={value} value={value}>
                  <SelectItemText>{label}</SelectItemText>
                  <SelectItemIndicator />
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="mt-4 min-h-0 flex-1">
        <Table<DataSyncInstanceRecord>
          className="min-h-full"
          columns={columns}
          dataSource={records}
          rowKey="id"
          loading={loading}
          bordered
          size="medium"
          scroll={{ x: 1260 }}
          emptyText={taskId ? "这个任务还没有运行实例" : "还没有同步实例"}
          pagination={
            total > 0
              ? {
                  current: pageNo,
                  pageSize: PAGE_SIZE,
                  total,
                  disabled: loading,
                  onChange: (page) => setPageNo(page),
                }
              : false
          }
        />
      </div>
    </div>
  );
}

interface DataSyncInstanceDetailPageProps {
  syncType: DataSyncType;
  basePath: string;
  listPath?: string;
  backLabel?: string;
}

export function DataSyncInstanceDetailPage({
  syncType,
  basePath,
  listPath,
  backLabel = "返回实例列表",
}: DataSyncInstanceDetailPageProps) {
  const realtime = syncType === "REALTIME";
  const resolvedListPath = listPath ?? `${basePath}?tab=instances`;
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [record, setRecord] = useState<DataSyncInstanceRecord>();
  const [attempts, setAttempts] = useState<DataSyncAttemptRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [canceling, setCanceling] = useState(false);

  const load = useCallback(async () => {
    if (!id) return;
    const [value, attemptItems] = await Promise.all([
      getDataSyncInstance(id),
      listDataSyncAttempts(id),
    ]);
    if (value.syncType !== syncType) {
      toast.error("实例类型与当前页面不匹配");
      navigate(resolvedListPath, { replace: true });
      return;
    }
    setRecord(value);
    setAttempts(attemptItems || []);
    setLoading(false);
  }, [id, navigate, resolvedListPath, syncType]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!isActiveDataSyncInstance(record)) return;
    const timer = window.setInterval(() => void load(), POLL_INTERVAL_MILLIS);
    return () => window.clearInterval(timer);
  }, [load, record]);

  const cancel = async () => {
    if (!record || canceling) return;
    setCanceling(true);
    try {
      setRecord(await cancelDataSyncInstance(record.id));
    } finally {
      setCanceling(false);
    }
  };

  if (loading || !record) {
    return <div className="p-8 text-sm text-[#667085]">正在加载同步实例...</div>;
  }

  return (
    <div className="min-h-full bg-[#f6f6f6] text-[#242731]">
      <PageHeader
        title={record.taskName}
        description={`任务版本 v${record.taskVersion} · ${dataSyncTriggerText(record.triggerType)}`}
        bordered
        className="bg-white px-6 max-md:px-4"
        extra={
          <>
            {isActiveDataSyncInstance(record) ? (
              <Button
                size="small"
                variant="danger"
                loading={canceling}
                onClick={() => void cancel()}
              >
                停止
              </Button>
            ) : null}
            <Button size="small" onClick={() => navigate(resolvedListPath)}>
              {backLabel}
            </Button>
          </>
        }
      />

      <div className="px-6 pb-8 pt-5 max-md:px-4">
        <DataSyncExecutionDetailContent
          record={record}
          attempts={attempts}
          realtime={realtime}
          showRealtimeStopCaution
        />
      </div>
    </div>
  );
}

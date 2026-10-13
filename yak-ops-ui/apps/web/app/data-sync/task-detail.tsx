import {
  Badge,
  Button,
  PageHeader,
  SectionCard,
  Table,
  toast,
  type TableColumns,
} from "@yak-ops/yak-ui";
import { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";

import {
  cancelDataSyncInstance,
  getDataSyncInstance,
  getDataSyncTask,
  listDataSyncAttempts,
  listDataSyncInstances,
  runDataSyncTask,
  type DataSyncAttemptRecord,
  type DataSyncInstanceRecord,
  type DataSyncTaskRecord,
  type DataSyncType,
} from "@/service/data-sync";
import { canCancelTaskInstance } from "@/service/data-sync/execution";

import {
  DataSyncExecutionConfigContent,
  DataSyncExecutionDetailContent,
  dataSyncInstanceStatusMeta,
  isActiveDataSyncInstance,
} from "./execution-detail";

const PAGE_SIZE = 20;
const POLL_MILLIS = 2000;

interface Props {
  syncType: DataSyncType;
  basePath: string;
  title: string;
  localScroll?: boolean;
}

export function DataSyncTaskDetailPage({ syncType, basePath, localScroll = false }: Props) {
  const realtime = syncType === "REALTIME";
  const { taskId } = useParams<{ taskId: string }>();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const executionId = searchParams.get("executionId");
  const [task, setTask] = useState<DataSyncTaskRecord>();
  const [instances, setInstances] = useState<DataSyncInstanceRecord[]>([]);
  const [selected, setSelected] = useState<DataSyncInstanceRecord>();
  const [attempts, setAttempts] = useState<DataSyncAttemptRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [pageNo, setPageNo] = useState(1);
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const [canceling, setCanceling] = useState(false);

  const loadInstances = useCallback(async () => {
    if (!taskId) return;
    const result = await listDataSyncInstances({ pageNo, pageSize: PAGE_SIZE, taskId, syncType });
    setInstances(result.bizData);
    setTotal(result.pagination.total);
  }, [pageNo, syncType, taskId]);

  useEffect(() => {
    if (!taskId) return;
    void getDataSyncTask(taskId)
      .then((value) => {
        if (value.syncType !== syncType) {
          navigate(basePath, { replace: true });
          return;
        }
        setTask(value);
      })
      .finally(() => setLoading(false));
  }, [basePath, navigate, syncType, taskId]);

  useEffect(() => {
    void loadInstances();
  }, [loadInstances]);

  useEffect(() => {
    if (!executionId) {
      setSelected(undefined);
      setAttempts([]);
      return;
    }
    void Promise.all([getDataSyncInstance(executionId), listDataSyncAttempts(executionId)]).then(
      ([instance, records]) => {
        if (instance.taskId !== taskId || instance.syncType !== syncType) {
          setSearchParams({}, { replace: true });
          return;
        }
        setSelected(instance);
        setAttempts(records);
      },
    );
  }, [executionId, setSearchParams, syncType, taskId]);

  useEffect(() => {
    if (!instances.some(isActiveDataSyncInstance) && !isActiveDataSyncInstance(selected)) return;
    const timer = window.setInterval(() => {
      void loadInstances();
      if (executionId) {
        void Promise.all([
          getDataSyncInstance(executionId),
          listDataSyncAttempts(executionId),
        ]).then(([instance, records]) => {
          setSelected(instance);
          setAttempts(records);
        });
      }
    }, POLL_MILLIS);
    return () => window.clearInterval(timer);
  }, [executionId, instances, loadInstances, selected]);

  const run = async () => {
    if (!taskId || running) return;
    setRunning(true);
    try {
      const submitted = await runDataSyncTask(taskId);
      toast.success("运行请求已提交");
      setSearchParams({ executionId: submitted.instanceId });
      await loadInstances();
    } finally {
      setRunning(false);
    }
  };

  const cancel = async () => {
    if (!selected || canceling) return;
    setCanceling(true);
    try {
      await cancelDataSyncInstance(selected.id);
      toast.success("已发送停止请求");
      setSelected(await getDataSyncInstance(selected.id));
    } finally {
      setCanceling(false);
    }
  };

  if (loading || !task) {
    return <div className="p-6 text-sm text-[#667085]">加载中...</div>;
  }

  const columns: TableColumns<DataSyncInstanceRecord> = [
    {
      key: "id",
      title: "实例 ID",
      minWidth: 190,
      render: (_value, record) => (
        <Button
          variant="ghost"
          size="small"
          onClick={() => setSearchParams({ executionId: record.id })}
        >
          {record.id}
        </Button>
      ),
    },
    {
      key: "status",
      title: "状态",
      width: 120,
      render: (_value, record) => {
        const meta = dataSyncInstanceStatusMeta(record.status, realtime);
        return <Badge tone={meta.tone}>{meta.label}</Badge>;
      },
    },
    {
      key: "definitionVersion",
      title: "版本",
      width: 90,
      render: (_value, record) => `v${record.taskVersion}`,
    },
    {
      key: "createTime",
      title: "提交时间",
      width: 190,
      render: (_value, record) => record.createTime || "-",
    },
    {
      key: "endTime",
      title: "完成时间",
      width: 190,
      render: (_value, record) => record.finishTime || "-",
    },
  ];

  return (
    <div
      className={localScroll ? "h-full overflow-y-auto bg-[#f6f6f6]" : "min-h-full bg-[#f6f6f6]"}
    >
      <PageHeader
        title={task.name}
        bordered
        className="bg-white px-6 max-md:px-4"
        extra={
          <div className="flex gap-2">
            <Button size="small" onClick={() => navigate(basePath)}>
              返回任务列表
            </Button>
            {task.status === "PUBLISHED" ? (
              <Button size="small" loading={running} onClick={() => void run()}>
                运行
              </Button>
            ) : null}
          </div>
        }
      />
      <div className="space-y-4 px-6 pb-8 pt-5 max-md:px-4">
        <SectionCard title="任务配置">
          <div className="grid grid-cols-3 gap-4 max-md:grid-cols-1">
            <div>
              <div className="text-xs text-[#98a2b3]">版本</div>
              <div className="mt-1 text-sm">v{task.definitionVersion}</div>
            </div>
            <div>
              <div className="text-xs text-[#98a2b3]">来源</div>
              <div className="mt-1 break-all text-sm">
                {task.sourceDataSourceId} · {task.sourceTable}
              </div>
            </div>
            <div>
              <div className="text-xs text-[#98a2b3]">去向</div>
              <div className="mt-1 break-all text-sm">
                {task.targetDataSourceId} · {task.targetTable}
              </div>
            </div>
          </div>
        </SectionCard>
        <SectionCard title="运行实例">
          <Table<DataSyncInstanceRecord>
            columns={columns}
            dataSource={instances}
            rowKey="id"
            bordered
            size="small"
            scroll={{ x: 850 }}
            pagination={
              total > PAGE_SIZE
                ? { current: pageNo, pageSize: PAGE_SIZE, total, onChange: setPageNo }
                : false
            }
          />
        </SectionCard>
        {selected ? (
          <>
            {canCancelTaskInstance(selected.status) ? (
              <div className="flex justify-end">
                <Button
                  size="small"
                  variant="danger"
                  loading={canceling}
                  onClick={() => void cancel()}
                >
                  停止
                </Button>
              </div>
            ) : null}
            <DataSyncExecutionDetailContent
              record={selected}
              attempts={attempts}
              realtime={realtime}
            />
            <DataSyncExecutionConfigContent record={selected} realtime={realtime} />
          </>
        ) : null}
      </div>
    </div>
  );
}

interface DataSyncLegacyInstanceDetailRedirectProps {
  syncType: DataSyncType;
  basePath: string;
}

export function DataSyncLegacyInstanceDetailRedirect({
  syncType,
  basePath,
}: DataSyncLegacyInstanceDetailRedirectProps) {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();

  useEffect(() => {
    if (!id) {
      navigate(basePath, { replace: true });
      return;
    }
    void getDataSyncInstance(id)
      .then((record) => {
        if (record.syncType !== syncType) {
          navigate(basePath, { replace: true });
          return;
        }
        navigate(`${basePath}/${record.taskId}/detail?executionId=${record.id}`, {
          replace: true,
        });
      })
      .catch(() => navigate(basePath, { replace: true }));
  }, [basePath, id, navigate, syncType]);

  return <div className="p-6 text-sm text-[#667085]">加载中...</div>;
}

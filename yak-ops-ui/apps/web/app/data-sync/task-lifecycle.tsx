import { Badge, Button, type BadgeProps } from "@yak-ops/yak-ui";
import { Fragment, useCallback, useEffect, useState } from "react";

import {
  listDataSyncInstances,
  type DataSyncInstanceRecord,
  type DataSyncTaskRecord,
  type DataSyncTaskStatus,
  type DataSyncType,
} from "@/service/data-sync";

const POLL_INTERVAL_MILLIS = 2000;

export const DATA_SYNC_TASK_STATUS_ITEMS: Record<DataSyncTaskStatus | "ALL", string> = {
  ALL: "全部状态",
  PUBLISHED: "已上线",
  UNPUBLISHED: "已下线",
};

export const isPublishedTask = (record: DataSyncTaskRecord) => record.status === "PUBLISHED";

export const taskStatusMeta = (
  status: DataSyncTaskStatus | string,
): { label: string; tone: BadgeProps["tone"] } =>
  status === "PUBLISHED"
    ? { label: "已上线", tone: "success" }
    : { label: "已下线", tone: "neutral" };

export function DataSyncTaskStatusBadge({ status }: { status: DataSyncTaskStatus | string }) {
  const meta = taskStatusMeta(status);
  return <Badge tone={meta.tone}>{meta.label}</Badge>;
}

export function useActiveTaskInstances(syncType: DataSyncType, enabled: boolean) {
  const [activeByTask, setActiveByTask] = useState<Map<string, DataSyncInstanceRecord>>(new Map());

  const refresh = useCallback(async () => {
    if (!enabled) {
      setActiveByTask(new Map<string, DataSyncInstanceRecord>());
      return;
    }

    const [pending, running, retryWaiting] = await Promise.all([
      listDataSyncInstances({
        pageNo: 1,
        pageSize: 200,
        syncType,
        status: "PENDING",
      }),
      listDataSyncInstances({
        pageNo: 1,
        pageSize: 200,
        syncType,
        status: "RUNNING",
      }),
      listDataSyncInstances({
        pageNo: 1,
        pageSize: 200,
        syncType,
        status: "RETRY_WAITING",
      }),
    ]);

    const next = new Map<string, DataSyncInstanceRecord>();
    [
      ...(pending?.bizData || []),
      ...(running?.bizData || []),
      ...(retryWaiting?.bizData || []),
    ].forEach((record) => {
      if (!next.has(record.taskId)) next.set(record.taskId, record);
    });
    setActiveByTask(next);
  }, [enabled, syncType]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (!enabled || activeByTask.size === 0) return;
    const timer = window.setInterval(() => void refresh(), POLL_INTERVAL_MILLIS);
    return () => window.clearInterval(timer);
  }, [activeByTask.size, enabled, refresh]);

  return { activeByTask, refresh };
}

interface LifecycleAction {
  key: string;
  label: string;
  className: string;
  disabled?: boolean;
  loading?: boolean;
  onClick: () => void;
}

export interface DataSyncTaskRuntimeAction {
  label: string;
  className: string;
  disabled?: boolean;
  loading?: boolean;
  onClick: () => void;
}

interface DataSyncTaskLifecycleActionsProps {
  record: DataSyncTaskRecord;
  activeInstance?: DataSyncInstanceRecord;
  actionKey?: string;
  runtimeAction?: DataSyncTaskRuntimeAction;
  onRun?: (record: DataSyncTaskRecord) => void;
  onPublish: (record: DataSyncTaskRecord) => void;
  onUnpublish: (record: DataSyncTaskRecord) => void;
  onEdit: (record: DataSyncTaskRecord) => void;
  onDetail: (record: DataSyncTaskRecord) => void;
  onDelete: (record: DataSyncTaskRecord) => void;
}

export function DataSyncTaskLifecycleActions({
  record,
  activeInstance,
  actionKey,
  runtimeAction,
  onRun,
  onPublish,
  onUnpublish,
  onEdit,
  onDetail,
  onDelete,
}: DataSyncTaskLifecycleActionsProps) {
  const published = isPublishedTask(record);
  const lifecycleAction = published ? "unpublish" : "publish";
  const actions: LifecycleAction[] = [
    {
      key: "lifecycle",
      label: published ? "下线" : "上线",
      className: "text-[var(--yak-color-primary)]",
      disabled: published && Boolean(activeInstance),
      loading: actionKey === `${record.id}:${lifecycleAction}`,
      onClick: () => (published ? onUnpublish(record) : onPublish(record)),
    },
    {
      key: "edit",
      label: "编辑",
      className: published
        ? "text-[#667085]"
        : "text-[#667085] hover:text-[var(--yak-color-primary)]",
      disabled: published,
      onClick: () => onEdit(record),
    },
    {
      key: "detail",
      label: "详情",
      className: "text-[#667085] hover:text-[var(--yak-color-primary)]",
      onClick: () => onDetail(record),
    },
    {
      key: "delete",
      label: "删除",
      className: published ? "text-[#667085]" : "text-[#667085] hover:text-[#d92d20]",
      disabled: published,
      onClick: () => onDelete(record),
    },
  ];

  if (runtimeAction) {
    actions.unshift({
      key: "runtime",
      ...runtimeAction,
    });
  } else if (onRun) {
    actions.unshift({
      key: "run",
      label: "运行",
      className:
        published && !activeInstance ? "text-[var(--yak-color-primary)]" : "text-[#667085]",
      disabled: !published || Boolean(activeInstance),
      loading: actionKey === `${record.id}:run`,
      onClick: () => onRun(record),
    });
  }

  return (
    <div className="flex items-center justify-center gap-0">
      {actions.map((action, index) => (
        <Fragment key={action.key}>
          {index > 0 ? <span className="mx-0.5 h-3 w-px bg-[#e4e7ec]" /> : null}
          <Button
            variant="ghost"
            size="small"
            loading={action.loading}
            disabled={action.disabled || (Boolean(actionKey) && !action.loading)}
            className={`px-1 text-xs font-normal ${action.className}`}
            onClick={action.onClick}
          >
            {action.label}
          </Button>
        </Fragment>
      ))}
    </div>
  );
}

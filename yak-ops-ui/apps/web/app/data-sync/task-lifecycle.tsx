import { Badge, Button, type BadgeProps } from "@yak-ops/yak-ui";
import { Fragment, useCallback, useEffect, useRef, useState } from "react";

import { listLatestTaskInstances, type TaskInstanceRecord } from "@/service/task-execution";

import {
  type DataSyncInstanceRecord,
  type DataSyncTaskRecord,
  type DataSyncTaskStatus,
} from "@/service/data-sync";

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

/** Shared page-level execution summaries, queried in batches without Definition lookups. */
export function useActiveTaskInstances(enabled: boolean, taskIds: string[]) {
  const [latestByTask, setLatestByTask] = useState<Map<string, TaskInstanceRecord>>(new Map());
  const [activeByTask, setActiveByTask] = useState<Map<string, TaskInstanceRecord>>(new Map());
  const requestId = useRef(0);
  const pendingRequest = useRef<AbortController>();

  const refresh = useCallback(async () => {
    pendingRequest.current?.abort();
    const controller = new AbortController();
    pendingRequest.current = controller;
    const request = ++requestId.current;
    if (!enabled || taskIds.length === 0) {
      setLatestByTask(new Map());
      setActiveByTask(new Map());
      return;
    }
    try {
      const [latest, active] = await Promise.all([
        listLatestTaskInstances(taskIds, false, controller.signal),
        listLatestTaskInstances(taskIds, true, controller.signal),
      ]);
      if (controller.signal.aborted || request !== requestId.current) return;
      setLatestByTask(new Map(latest.map((record) => [record.definitionId, record])));
      setActiveByTask(new Map(active.map((record) => [record.definitionId, record])));
    } catch {
      // Keep the last observation. Authorization and transport errors are reported centrally.
    }
  }, [enabled, taskIds]);

  useEffect(() => {
    void refresh();
    return () => {
      requestId.current += 1;
      pendingRequest.current?.abort();
    };
  }, [refresh]);

  useEffect(() => {
    if (!enabled || taskIds.length === 0) return;
    const timer = window.setInterval(() => void refresh(), activeByTask.size > 0 ? 2000 : 15000);
    return () => window.clearInterval(timer);
  }, [activeByTask.size, enabled, refresh, taskIds.length]);

  return { latestByTask, activeByTask, refresh };
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
  activeInstance?: Pick<DataSyncInstanceRecord, "id" | "status">;
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

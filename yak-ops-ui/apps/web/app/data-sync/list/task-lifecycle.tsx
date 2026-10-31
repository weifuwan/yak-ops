import { Badge, Button, type BadgeProps } from "@yak-ops/yak-ui";
import { Fragment } from "react";

import { canManageTaskDefinition } from "@/service/data-sync/execution";

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
  instanceStateReady?: boolean;
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
  instanceStateReady = false,
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
  const canManage = canManageTaskDefinition(instanceStateReady, activeInstance);
  const lifecycleAction = published ? "unpublish" : "publish";
  const actions: LifecycleAction[] = [
    {
      key: "lifecycle",
      label: published ? "下线" : "上线",
      className: "text-[var(--yak-color-primary)]",

      loading: actionKey === `${record.id}:${lifecycleAction}`,
      disabled: !canManage,
      onClick: () => (published ? onUnpublish(record) : onPublish(record)),
    },
    {
      key: "edit",
      label: "编辑",
      className:
        "text-[var(--yak-components-collapse-section-icon)] hover:text-[var(--yak-color-primary)]",
      onClick: () => onEdit(record),
    },
    {
      key: "detail",
      label: "详情",
      className:
        "text-[var(--yak-components-collapse-section-icon)] hover:text-[var(--yak-color-primary)]",
      onClick: () => onDetail(record),
    },
    {
      key: "delete",
      label: "删除",
      className: published
        ? "text-[var(--yak-components-collapse-section-icon)]"
        : "text-[var(--yak-components-collapse-section-icon)] hover:text-[var(--yak-components-danger-text)]",
      disabled: published || !canManage,
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
        published && !activeInstance
          ? "text-[var(--yak-color-primary)]"
          : "text-[var(--yak-components-collapse-section-icon)]",
      disabled: !published || !canManage,
      loading: actionKey === `${record.id}:run`,
      onClick: () => onRun(record),
    });
  }

  return (
    <div className="flex items-center justify-center gap-0">
      {actions.map((action, index) => (
        <Fragment key={action.key}>
          {index > 0 ? (
            <span className="mx-0.5 h-3 w-px bg-[var(--yak-components-table-border-strong)]" />
          ) : null}
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

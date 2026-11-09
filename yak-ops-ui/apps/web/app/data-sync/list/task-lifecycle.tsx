import {
  Badge,
  Button,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  type BadgeProps,
} from "@yak-ops/yak-ui";
import { Ellipsis } from "lucide-react";
import { Fragment } from "react";

import { canManageTaskDefinition } from "@/service/data-sync/execution";

import {
  type DataSyncInstanceRecord,
  type DataSyncTaskRecord,
  type DataSyncTaskStatus,
} from "@/service/data-sync";

export const DATA_SYNC_TASK_STATUS_ITEMS: Record<DataSyncTaskStatus | "ALL", string> = {
  ALL: "全部状态",
  ONLINE: "已上线",
  OFFLINE: "已下线",
};

export const isPublishedTask = (record: DataSyncTaskRecord) => record.status === "ONLINE";

export const taskStatusMeta = (
  status: DataSyncTaskStatus | string,
): { label: string; tone: BadgeProps["tone"] } =>
  status === "ONLINE"
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
  workflowActive?: boolean;
  actionKey?: string;
  runtimeAction?: DataSyncTaskRuntimeAction;
  onRun?: (record: DataSyncTaskRecord) => void;
  onPublish: (record: DataSyncTaskRecord) => void;
  onUnpublish: (record: DataSyncTaskRecord) => void;
  onAlertConfig: (record: DataSyncTaskRecord) => void;
  onEdit: (record: DataSyncTaskRecord) => void;
  onDetail: (record: DataSyncTaskRecord) => void;
  onDelete: (record: DataSyncTaskRecord) => void;
}

export function DataSyncTaskLifecycleActions({
  record,
  activeInstance,
  instanceStateReady = false,
  workflowActive = false,
  actionKey,
  runtimeAction,
  onRun,
  onPublish,
  onUnpublish,
  onAlertConfig,
  onEdit,
  onDetail,
  onDelete,
}: DataSyncTaskLifecycleActionsProps) {
  const published = isPublishedTask(record);
  const canManage = !workflowActive && canManageTaskDefinition(instanceStateReady, activeInstance);
  const canUnpublish = !workflowActive && !activeInstance;
  const lifecycleAction = published ? "unpublish" : "publish";
  const actions: LifecycleAction[] = [
    {
      key: "lifecycle",
      label: published ? "下线" : "上线",
      className: "text-[var(--yak-color-primary)]",
      loading: actionKey === `${record.id}:${lifecycleAction}`,
      disabled: published ? !canUnpublish : !canManage,
      onClick: () => (published ? onUnpublish(record) : onPublish(record)),
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
            size="sm"
            isPending={action.loading}
            isDisabled={action.disabled || (Boolean(actionKey) && !action.loading)}
            className={`px-1 text-xs font-normal ${action.className}`}
            onClick={action.onClick}
          >
            {action.label}
          </Button>
        </Fragment>
      ))}

      <span className="mx-0.5 h-3 w-px bg-[var(--yak-components-table-border-strong)]" />

      <DropdownMenu>
        <DropdownMenuTrigger
          aria-label="更多操作"
          disabled={Boolean(actionKey)}
          className="inline-flex h-7 cursor-pointer items-center gap-1 rounded-[var(--yak-radius-control-small)] border-0 bg-transparent px-1.5 text-xs text-[var(--yak-components-collapse-section-icon)] outline-none hover:bg-[var(--yak-components-menu-item-hover)] hover:text-[var(--yak-color-primary)] disabled:cursor-not-allowed disabled:opacity-45"
        >
          更多
          <Ellipsis className="h-3.5 w-3.5" />
        </DropdownMenuTrigger>
        <DropdownMenuContent side="bottom" align="end" className="w-36">
          <DropdownMenuItem onClick={() => onAlertConfig(record)}>
            告警配置
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem disabled={!canManage} onClick={() => onEdit(record)}>
            编辑
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => onDetail(record)}>详情</DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem
            tone="danger"
            disabled={published || !canManage}
            onClick={() => onDelete(record)}
          >
            删除
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
}

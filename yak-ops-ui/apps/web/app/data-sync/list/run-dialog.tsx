import { useQuery } from "@tanstack/react-query";
import {
  Button,
  Field,
  FieldError,
  FieldLabel,
  Input,
  Modal,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";
import { useState } from "react";

import { useAuth } from "@/hooks/use-auth";
import { listAlertGroups } from "@/service/alert";
import type { DataSyncTaskRecord } from "@/service/data-sync";
import type { StartWorkflowOptions, WorkflowWarningType } from "@/service/workflow/execution";

const WARNING_LABELS: Record<WorkflowWarningType, string> = {
  NONE: "不通知",
  FAILURE: "仅失败时通知",
  SUCCESS: "仅成功时通知",
  ALL: "成功或失败都通知",
};

const WARNING_TYPES: WorkflowWarningType[] = ["NONE", "FAILURE", "SUCCESS", "ALL"];

export function RunDataSyncTaskDialog({
  task,
  pending,
  onClose,
  onRun,
}: {
  task: DataSyncTaskRecord;
  pending: boolean;
  onClose: () => void;
  onRun: (options: StartWorkflowOptions) => void;
}) {
  const { currentUser } = useAuth();
  const [warningType, setWarningType] = useState<WorkflowWarningType>("NONE");
  const [warningGroupId, setWarningGroupId] = useState<number>();
  const groupsQuery = useQuery({
    queryKey: ["workflow", "run", "alert-groups", currentUser?.id ?? ""],
    queryFn: () => listAlertGroups(),
    enabled: Boolean(currentUser?.id),
  });
  const groups = groupsQuery.data ?? [];
  const alertEnabled = warningType !== "NONE";
  const effectiveGroupId = warningGroupId ?? (alertEnabled ? groups[0]?.id : undefined);
  const groupRequiredError = alertEnabled && groupsQuery.isSuccess && groups.length === 0;
  const groupLoadError = alertEnabled && groupsQuery.isError && groups.length === 0;
  const runBlocked =
    alertEnabled &&
    (groupsQuery.isPending ||
      groupsQuery.isFetching ||
      groupLoadError ||
      !effectiveGroupId);

  return (
    <Modal
      open
      centered
      width={600}
      title="运行同步任务"
      onClose={() => {
        if (!pending) onClose();
      }}
      footer={
        <>
          <Button size="sm" variant="secondary" isDisabled={pending} onClick={onClose}>
            取消
          </Button>
          <Button
            size="sm"
            variant="primary"
            isPending={pending}
            isDisabled={runBlocked}
            onClick={() =>
              onRun({
                warningType,
                warningGroupId: alertEnabled ? effectiveGroupId : undefined,
              })
            }
          >
            运行
          </Button>
        </>
      }
    >
      <div className="space-y-2.5">
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel>任务</FieldLabel>
          <Input size="small" variant="outlined" value={task.name} disabled />
        </Field>

        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel>通知策略</FieldLabel>
          <Select<WorkflowWarningType>
            size="small"
            items={WARNING_LABELS}
            value={warningType}
            onValueChange={(value) => {
              if (value) setWarningType(value);
            }}
          >
            <SelectTrigger variant="outlined">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {WARNING_TYPES.map((value) => (
                <SelectItem key={value} value={value}>
                  <SelectItemText>{WARNING_LABELS[value]}</SelectItemText>
                  <SelectItemIndicator />
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </Field>

        {alertEnabled ? (
          <Field
            className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3"
            invalid={groupRequiredError}
          >
            <FieldLabel required className="pt-1.5">
              通知组
            </FieldLabel>
            <div className="min-w-0">
              <Select<string>
                size="small"
                disabled={groupsQuery.isPending || groupsQuery.isFetching || groups.length === 0}
                items={Object.fromEntries(
                  groups.map((group) => [String(group.id), group.groupName]),
                )}
                value={effectiveGroupId ? String(effectiveGroupId) : null}
                onValueChange={(value) => {
                  if (value) setWarningGroupId(Number(value));
                }}
              >
                <SelectTrigger variant="outlined">
                  <SelectValue
                    placeholder={
                      groupsQuery.isPending || groupsQuery.isFetching
                        ? "加载通知组..."
                        : "请选择通知组"
                    }
                  />
                </SelectTrigger>
                <SelectContent>
                  {groups.map((group) => (
                    <SelectItem key={group.id} value={String(group.id)}>
                      <SelectItemText>{group.groupName}</SelectItemText>
                      <SelectItemIndicator />
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {groupLoadError ? (
                <div className="mt-1 flex items-center gap-2 text-xs text-[var(--yak-components-danger-text)]">
                  通知组加载失败
                  <Button
                    size="sm"
                    variant="ghost"
                    className="h-auto px-0 py-0 text-xs font-normal"
                    onClick={() => void groupsQuery.refetch()}
                  >
                    重试
                  </Button>
                </div>
              ) : null}
              <FieldError match={groupRequiredError} className="mt-1">
                暂无通知组，请先在告警中心创建
              </FieldError>
              <a
                href="/management/alerts?tab=groups"
                target="_blank"
                rel="noreferrer"
                className="mt-1 inline-block text-xs text-[var(--yak-color-primary)] hover:underline"
              >
                管理通知组
              </a>
            </div>
          </Field>
        ) : null}
      </div>
    </Modal>
  );
}

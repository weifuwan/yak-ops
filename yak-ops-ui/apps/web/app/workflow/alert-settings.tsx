import { useQuery } from "@tanstack/react-query";
import {
  Button,
  Field,
  FieldError,
  FieldLabel,
  Select,
  SelectContent,
  SelectItem,
  SelectItemIndicator,
  SelectItemText,
  SelectTrigger,
  SelectValue,
} from "@yak-ops/yak-ui";
import { useEffect } from "react";

import { useAuth } from "@/hooks/use-auth";
import { listAlertGroups } from "@/service/alert";
import type { WorkflowAlertOptions, WorkflowWarningType } from "@/service/workflow/execution";

export const WORKFLOW_WARNING_LABELS: Record<WorkflowWarningType, string> = {
  NONE: "不通知",
  FAILURE: "仅失败时通知",
  SUCCESS: "仅成功时通知",
  ALL: "成功或失败都通知",
};

const WARNING_TYPES: WorkflowWarningType[] = ["NONE", "FAILURE", "SUCCESS", "ALL"];

export function WorkflowAlertSettings({
  value,
  onChange,
  onValidChange,
  disabled = false,
}: {
  value: WorkflowAlertOptions;
  onChange: (value: WorkflowAlertOptions) => void;
  onValidChange?: (valid: boolean) => void;
  disabled?: boolean;
}) {
  const { currentUser } = useAuth();
  const groupsQuery = useQuery({
    queryKey: ["workflow", "alert-groups", currentUser?.id ?? ""],
    queryFn: () => listAlertGroups(),
    enabled: Boolean(currentUser?.id),
  });
  const groups = groupsQuery.data ?? [];
  const alertEnabled = value.warningType !== "NONE";
  const configuredGroupExists =
    value.warningGroupId !== undefined && groups.some((group) => group.id === value.warningGroupId);
  const effectiveGroupId = alertEnabled
    ? configuredGroupExists
      ? value.warningGroupId
      : groups[0]?.id
    : undefined;
  const groupRequiredError = alertEnabled && groupsQuery.isSuccess && groups.length === 0;
  const groupLoadError = alertEnabled && groupsQuery.isError && groups.length === 0;
  const valid =
    !alertEnabled ||
    (!groupsQuery.isPending &&
      !groupsQuery.isFetching &&
      !groupLoadError &&
      Boolean(effectiveGroupId));

  useEffect(() => {
    onValidChange?.(valid);
  }, [onValidChange, valid]);

  useEffect(() => {
    if (alertEnabled && effectiveGroupId && value.warningGroupId !== effectiveGroupId) {
      onChange({ ...value, warningGroupId: effectiveGroupId });
    }
  }, [alertEnabled, effectiveGroupId, onChange, value]);

  return (
    <div className="space-y-2.5">
      <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
        <FieldLabel>通知策略</FieldLabel>
        <Select<WorkflowWarningType>
          size="small"
          items={WORKFLOW_WARNING_LABELS}
          value={value.warningType}
          disabled={disabled}
          onValueChange={(next) => {
            if (!next) return;
            onChange({
              warningType: next,
              warningGroupId: next === "NONE" ? undefined : value.warningGroupId,
            });
          }}
        >
          <SelectTrigger variant="outlined">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {WARNING_TYPES.map((item) => (
              <SelectItem key={item} value={item}>
                <SelectItemText>{WORKFLOW_WARNING_LABELS[item]}</SelectItemText>
                <SelectItemIndicator />
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </Field>

      {alertEnabled ? (
        <Field
          className="grid grid-cols-[104px_minmax(0,1fr)] items-start !gap-3"
          invalid={groupRequiredError || groupLoadError}
        >
          <FieldLabel required className="pt-1.5">
            通知组
          </FieldLabel>
          <div className="min-w-0">
            <Select<string>
              size="small"
              disabled={
                disabled ||
                groupsQuery.isPending ||
                groupsQuery.isFetching ||
                groups.length === 0
              }
              items={Object.fromEntries(groups.map((group) => [String(group.id), group.groupName]))}
              value={effectiveGroupId ? String(effectiveGroupId) : null}
              onValueChange={(next) => {
                if (next) {
                  onChange({ ...value, warningGroupId: Number(next) });
                }
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
  );
}

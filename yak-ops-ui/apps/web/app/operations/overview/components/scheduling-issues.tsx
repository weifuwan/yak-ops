import { CircleAlert, Clock3 } from "lucide-react";
import { Card, CardContent, Skeleton } from "@yak-ops/yak-ui";

function IssueMetric({
  label,
  value,
  loading,
  danger = false,
}: {
  label: string;
  value?: number;
  loading: boolean;
  danger?: boolean;
}) {
  const Icon = danger ? CircleAlert : Clock3;

  return (
    <div className="rounded-lg border border-[var(--yak-components-panel-border)] px-4 py-4">
      <div className="flex items-center justify-between gap-3">
        <div className="text-xs text-[var(--yak-components-muted-text)]">{label}</div>
        <Icon
          size={16}
          aria-hidden="true"
          className={danger ? "text-[#ef4444]" : "text-[var(--yak-components-muted-text)]"}
        />
      </div>
      <div className="mt-3 min-h-8 text-2xl font-semibold tabular-nums">
        {loading ? (
          <Skeleton width={60} height={26} />
        ) : value === undefined ? (
          "—"
        ) : (
          value.toLocaleString("zh-CN")
        )}
      </div>
    </div>
  );
}

export function SchedulingIssues({
  pending,
  errors,
  pendingLoading,
  errorsLoading,
}: {
  pending?: number;
  errors?: number;
  pendingLoading: boolean;
  errorsLoading: boolean;
}) {
  return (
    <Card>
      <CardContent className="p-5">
        <div className="mb-4">
          <div className="text-sm font-medium">调度异常</div>
          <div className="mt-1 text-xs text-[var(--yak-components-muted-text)]">
            调度命令当前状态
          </div>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <IssueMetric label="待处理命令" value={pending} loading={pendingLoading} />
          <IssueMetric label="异常命令" value={errors} loading={errorsLoading} danger />
        </div>
      </CardContent>
    </Card>
  );
}

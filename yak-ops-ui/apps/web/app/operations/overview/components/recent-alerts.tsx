import { BellRing } from "lucide-react";
import { Link } from "react-router-dom";
import { Card, CardContent, Skeleton } from "@yak-ops/yak-ui";

import type { AlertRecord } from "@/service/alert";

const ALERT_TYPE_LABELS: Record<string, string> = {
  WORKFLOW_INSTANCE_FAILURE: "工作流失败",
  WORKFLOW_INSTANCE_SUCCESS: "工作流成功",
  WORKFLOW_INSTANCE_BLOCKED: "工作流阻塞",
  WORKFLOW_INSTANCE_TIMEOUT: "工作流超时",
  FAULT_TOLERANCE_WARNING: "服务异常",
  TASK_FAILURE: "任务失败",
  TASK_SUCCESS: "任务成功",
  TASK_TIMEOUT: "任务超时",
  TASK_RESULT: "任务结果",
};

export function RecentAlerts({
  records,
  total,
  loading = false,
  error = false,
}: {
  records: AlertRecord[];
  total?: number;
  loading?: boolean;
  error?: boolean;
}) {
  return (
    <Card>
      <CardContent className="p-5">
        <div className="mb-4 flex items-center justify-between gap-4">
          <div className="flex items-center gap-2 text-sm font-medium">
            <BellRing size={16} aria-hidden="true" />
            最近告警
          </div>
          <div className="flex items-center gap-3 text-xs">
            <span className="text-[var(--yak-components-muted-text)]">
              {total === undefined ? "—" : `${total.toLocaleString("zh-CN")} 条记录`}
            </span>
            <Link
              to="/operations/alerts"
              className="font-medium text-[var(--yak-color-primary)] hover:underline"
            >
              查看全部
            </Link>
          </div>
        </div>

        {loading ? (
          <div className="space-y-3">
            {Array.from({ length: 3 }, (_, index) => (
              <Skeleton key={index} width="100%" height={34} />
            ))}
          </div>
        ) : error && records.length === 0 ? (
          <div className="py-10 text-center text-sm text-[var(--yak-components-muted-text)]">
            告警记录加载失败
          </div>
        ) : records.length ? (
          <div className="divide-y divide-[var(--yak-components-panel-border)]">
            {records.map((record) => (
              <div key={record.id} className="flex items-center gap-4 py-3 text-xs">
                <div className="min-w-0 flex-1">
                  <div className="truncate font-medium">{record.title || "告警"}</div>
                  <div className="mt-0.5 truncate text-[var(--yak-components-muted-text)]">
                    {record.alertType
                      ? ALERT_TYPE_LABELS[record.alertType] ?? record.alertType
                      : "-"}
                  </div>
                </div>
                <div className="shrink-0 text-[var(--yak-components-muted-text)]">
                  {record.createTime || "-"}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <div className="py-10 text-center text-sm text-[var(--yak-components-muted-text)]">
            暂无告警记录
          </div>
        )}
      </CardContent>
    </Card>
  );
}

import { Button, Skeleton, Switch } from "@yak-ops/yak-ui";
import type { ScheduleSummary } from "@/service/schedule";
import { registrationText } from "./schedule-presentation";

/** The list owns fetching; individual cells never query a schedule. */
export function TaskScheduleCell({
  schedule,
  name,
  published,
  loading,
  error,
  pending,
  onConfigure,
  onToggle,
}: {
  schedule?: ScheduleSummary;
  name: string;
  published: boolean;
  loading: boolean;
  error: boolean;
  pending: boolean;
  onConfigure: () => void;
  onToggle: (enabled: boolean) => void;
}) {
  if (loading)
    return (
      <div className="flex items-center gap-2">
        <Skeleton width={110} height={14} />
        <Skeleton width={26} height={16} />
      </div>
    );
  if (error)
    return (
      <Button size="sm" variant="ghost" onClick={onConfigure}>
        调度加载失败
      </Button>
    );
  const observation = schedule ? registrationText(schedule) : null;
  return (
    <div className="min-w-0 space-y-1">
      <div className="flex items-center gap-2">
        <Button
          size="sm"
          variant="ghost"
          className="min-w-0 px-0 font-mono text-xs"
          onClick={onConfigure}
          title={schedule ? `${schedule.cronExpression} · ${schedule.timeZone}` : "配置调度"}
        >
          <span className="truncate">{schedule?.cronExpression || "手动"}</span>
        </Button>
        {schedule ? (
          <span title={!published && !schedule.enabled ? "请先上线任务" : undefined}>
            <Switch
              size="small"
              aria-label={`启用调度：${name}`}
              checked={schedule.enabled}
              disabled={pending || (!published && !schedule.enabled)}
              onCheckedChange={onToggle}
            />
          </span>
        ) : null}
      </div>
      {observation ? (
        <div
          role="status"
          className={
            schedule?.registrationStatus === "ERROR"
              ? "text-xs text-[#d92d20]"
              : "text-xs text-[#98a2b3]"
          }
        >
          {observation}
        </div>
      ) : null}
    </div>
  );
}

import { Button } from "@yak-ops/yak-ui";
import type { ScheduleTimeRule } from "@/service/schedule";
import { formatScheduleTime } from "../schedule-presentation";
import { useSchedulePreview } from "../use-schedule-preview";

export function SchedulePreview({ rule }: { rule: ScheduleTimeRule }) {
  const preview = useSchedulePreview(rule.cronExpression, rule.timeZone);
  return (
    <div aria-live="polite">
      <div className="flex flex-wrap justify-between gap-2 text-xs text-[var(--yak-components-muted-text)]">
        <span className="font-medium">未来 5 次触发时间</span>
        <span>{rule.timeZone}</span>
      </div>
      {preview.status === "ready" && preview.times.length > 0 ? (
        <div className="mt-2 space-y-1.5 font-mono text-xs text-[var(--yak-components-muted-text)]">
          {preview.times.map((time) => (
            <div key={time}>{formatScheduleTime(time, rule.timeZone)}</div>
          ))}
        </div>
      ) : (
        <div className="mt-2 flex items-center gap-2 text-xs text-[var(--yak-components-muted-text)]">
          <span>
            {preview.status === "loading"
              ? "正在计算…"
              : preview.status === "invalid"
                ? "Cron 或时区无效"
                : preview.status === "error"
                  ? "预览请求失败"
                  : preview.status === "empty"
                    ? "请输入 Cron"
                    : "暂无未来触发时间"}
          </span>
          {preview.status === "error" ? (
            <Button size="sm" variant="ghost" onClick={preview.retry}>
              重试
            </Button>
          ) : null}
        </div>
      )}
    </div>
  );
}

import { Alert, Button, CollapseSection } from "@yak-ops/yak-ui";
import { ScheduleForm } from "@/app/schedule/form/schedule-form";
import type { ScheduleRecord, ScheduleTimeRule } from "@/service/schedule";

interface Props {
  schedule: ScheduleRecord | null;
  scheduleForm: ScheduleTimeRule;
  scheduleError: string;
  scheduleNeedsReload: boolean;
  scheduleLoading: boolean;
  saving: boolean;
  applicable?: boolean;
  onReload: () => void;
  onChange: (value: ScheduleTimeRule) => void;
}
export function EditorScheduleSection({
  schedule,
  scheduleForm,
  scheduleError,
  scheduleNeedsReload,
  scheduleLoading,
  saving,
  applicable = true,
  onReload,
  onChange,
}: Props) {
  return (
    <CollapseSection id="schedule" title="调度配置">
      <div className="space-y-3 rounded-lg border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-4">
        {scheduleError ? (
          <Alert>
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span>{scheduleError}</span>
              {scheduleNeedsReload ? (
                <Button
                  size="small"
                  disabled={saving || scheduleLoading}
                  onClick={() => {
                    onReload();
                  }}
                >
                  重新加载状态
                </Button>
              ) : null}
            </div>
          </Alert>
        ) : null}
        {scheduleLoading ? (
          <div className="text-xs text-[var(--yak-components-collapse-section-icon)]">
            加载调度…
          </div>
        ) : (
          <ScheduleForm
            value={scheduleForm}
            onChange={(value) => {
              onChange(value);
            }}
            disabled={saving || scheduleNeedsReload || Boolean(schedule?.enabled) || !applicable}
            allowClear={!schedule}
          />
        )}
        {schedule?.enabled ? (
          <div className="text-xs text-[var(--yak-components-collapse-section-icon)]">
            请先在任务列表停止调度再修改配置
          </div>
        ) : null}
        {!applicable ? (
          <p className="text-xs text-[var(--yak-components-muted-text)]">
            当前编辑方案不支持调度，已有调度仍按已发布版本执行。
          </p>
        ) : null}
      </div>
    </CollapseSection>
  );
}

import type { ChangeEvent, ReactNode } from "react";
import { CronSchedulerPicker } from "@yak-ops/yak-ui";

import { SCHEDULE_CONTROL_CLASS } from "../shared/constants";
import type { ScheduleSection } from "../shared/types";
import type { WorkflowSidePanelController } from "../hooks/useWorkflowSidePanelController";
import { ScheduleSelect } from "./ScheduleSelect";

type RenderSectionHeader = (
  section: ScheduleSection,
  title: string,
  action?: ReactNode,
) => ReactNode;

export function ScheduleTimeSection({
  controller,
  renderSectionHeader,
}: {
  controller: WorkflowSidePanelController;
  renderSectionHeader: RenderSectionHeader;
}) {
  const {
    scheduleCollapsed,
    scheduleForm,
    scheduleIsOnline,
    updateScheduleField,
    previewTimes,
  } = controller;
  const timezoneOptions = Array.from(
    new Set([
      "Asia/Shanghai",
      "Asia/Singapore",
      "Asia/Hong_Kong",
      "Asia/Tokyo",
      "UTC",
      "Europe/London",
      "America/New_York",
      scheduleForm.timezoneId,
    ]),
  ).map((zone) => ({ value: zone, label: zone }));

  return (
<section id="workflow-schedule-time" className="scroll-mt-3">
              {renderSectionHeader("time", "调度时间")}
              {!scheduleCollapsed.time ? (
                <div className="space-y-2.5 pb-2 pt-3">
                  <label className="grid grid-cols-[92px_minmax(0,1fr)] items-center gap-3">
                    <span className="text-xs text-[#687385]">Cron 表达式</span>
                    <div className="min-w-0 [&>*]:w-full">
                      <CronSchedulerPicker
                        value={scheduleForm.crontab}
                        disabled={scheduleIsOnline}
                        allowClear
                        placeholder="点击配置 Cron"
                        size="small"
                        variant="outlined"
                        onValueChange={(value) => updateScheduleField("crontab", value)}
                      />
                    </div>
                  </label>

                  <label className="grid grid-cols-[92px_minmax(0,1fr)] items-center gap-3">
                    <span className="text-xs text-[#687385]">调度时区</span>
                    <ScheduleSelect
                      ariaLabel="调度时区"
                      value={scheduleForm.timezoneId}
                      options={timezoneOptions}
                      disabled={scheduleIsOnline}
                      onValueChange={(value) => updateScheduleField("timezoneId", value)}
                    />
                  </label>

                  <label className="grid grid-cols-[92px_minmax(0,1fr)] items-center gap-3">
                    <span className="text-xs text-[#687385]">生效开始时间</span>
                    <input
                      className={SCHEDULE_CONTROL_CLASS}
                      type="datetime-local"
                      value={scheduleForm.startTime}
                      disabled={scheduleIsOnline}
                      onChange={(event: ChangeEvent<HTMLInputElement>) =>
                        updateScheduleField("startTime", event.target.value)
                      }
                    />
                  </label>

                  <label className="grid grid-cols-[92px_minmax(0,1fr)] items-center gap-3">
                    <span className="text-xs text-[#687385]">生效结束时间</span>
                    <input
                      className={SCHEDULE_CONTROL_CLASS}
                      type="datetime-local"
                      value={scheduleForm.endTime}
                      disabled={scheduleIsOnline}
                      onChange={(event: ChangeEvent<HTMLInputElement>) =>
                        updateScheduleField("endTime", event.target.value)
                      }
                    />
                  </label>

                  {previewTimes.length > 0 ? (
                    <div className="rounded-md border border-[#e5e9ef] bg-[#fbfcfe] p-2.5">
                      <div className="mb-2 text-xs font-semibold text-[#455064]">
                        接下来 {previewTimes.length} 次触发时间
                      </div>
                      <div className="space-y-1">
                        {previewTimes.map((time, index) => (
                          <div key={time + index} className="text-xs text-[#687384]">
                            {index + 1}. {time}
                          </div>
                        ))}
                      </div>
                    </div>
                  ) : null}
                </div>
              ) : null}
            </section>
  );
}

import type { ComponentProps } from "react";
import { CollapseSection, Field, FieldLabel } from "@yak-ops/yak-ui";
import { EditorScheduleSection } from "./schedule-section";

export function RunConfigurationSection({
  replication,
  continuous,
  schedule,
}: {
  replication: boolean;
  continuous: boolean;
  schedule?: ComponentProps<typeof EditorScheduleSection>;
}) {
  return (
    <CollapseSection id="env" title="运行配置">
      <div className="space-y-4 rounded-lg border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-4">
        <Field className="grid grid-cols-[104px_minmax(0,1fr)] items-center !gap-3">
          <FieldLabel>执行方式</FieldLabel>
          <span className="text-sm">{continuous ? "持续运行" : "执行完成后结束"}</span>
        </Field>
        {replication ? (
          <p className="text-xs text-[var(--yak-components-collapse-section-icon)]">
            每次提交按当前策略创建独立实例，不续用上次实例的检查点。
          </p>
        ) : null}
        {schedule ? (
          <div className="border-t border-[var(--yak-components-panel-border)] pt-4">
            <EditorScheduleSection {...schedule} />
          </div>
        ) : null}
      </div>
    </CollapseSection>
  );
}

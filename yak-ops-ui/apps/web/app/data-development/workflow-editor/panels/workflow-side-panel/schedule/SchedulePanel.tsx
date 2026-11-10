import type { ReactNode } from "react";
import { ChevronDown, Clock3, RefreshCw, Save } from "lucide-react";

import { PanelButton, PanelNotice } from "../components/PanelPrimitives";
import { MUTED_CLASS } from "../shared/constants";
import type { ScheduleSection } from "../shared/types";
import type { WorkflowSidePanelController } from "../hooks/useWorkflowSidePanelController";
import { GlobalParametersSection } from "./GlobalParametersSection";
import { ScheduleStrategySection } from "./ScheduleStrategySection";
import { ScheduleTimeSection } from "./ScheduleTimeSection";

export function SchedulePanel({
  controller,
}: {
  controller: WorkflowSidePanelController;
}) {
  const {
    scheduleSection,
    setScheduleSection,
    scheduleCollapsed,
    setScheduleCollapsed,
    scheduleQuery,
    currentSchedule,
    scheduleIsOnline,
    workflowIsOnline,
    scheduleForm,
    alertGroupsQuery,
    notificationGroupMissing,
    schedulePreviewing,
    previewSchedule,
    scheduleToggling,
    toggleSchedule,
    scheduleSaving,
    saveSchedule,
  } = controller;

    if (scheduleQuery.isPending) {
      return (
        <div className="flex min-h-0 flex-1 items-center justify-center text-xs text-[#8b93a1]">
          正在加载调度配置...
        </div>
      );
    }

    if (scheduleQuery.isError) {
      return (
        <div className="flex min-h-0 flex-1 flex-col items-start gap-3 p-4">
          <PanelNotice tone="warning">调度配置加载失败，请检查工作空间和接口状态。</PanelNotice>
          <PanelButton onClick={() => void scheduleQuery.refetch()} variant="secondary">
            <RefreshCw size={13} />
            重新加载
          </PanelButton>
        </div>
      );
    }

    const navigateToSection = (section: ScheduleSection) => {
      setScheduleSection(section);
      setScheduleCollapsed((current) =>
        current[section] ? { ...current, [section]: false } : current,
      );
      document
        .getElementById(`workflow-schedule-${section}`)
        ?.scrollIntoView({ behavior: "smooth", block: "start" });
    };

    const toggleSection = (section: ScheduleSection) => {
      setScheduleCollapsed((current) => ({
        ...current,
        [section]: !current[section],
      }));
    };

    const renderSectionHeader = (section: ScheduleSection, title: string, action?: ReactNode) => (
      <div className="flex h-9 items-center justify-between rounded-sm bg-[#f7f7f7] px-2.5 transition-colors">
        <button
          type="button"
          aria-expanded={!scheduleCollapsed[section]}
          onClick={() => toggleSection(section)}
          className="flex h-full min-w-0 flex-1 cursor-pointer items-center gap-2 text-left"
        >
          <ChevronDown
            size={14}
            className={[
              "shrink-0 text-[#7b8492] transition-transform",
              scheduleCollapsed[section] ? "-rotate-90" : "rotate-0",
            ].join(" ")}
          />
          <span className="text-[13px] font-semibold text-[#303846]">{title}</span>
        </button>
        {action}
      </div>
    );

    const scheduleStatusLabel = !currentSchedule
      ? "尚未配置"
      : scheduleIsOnline
        ? "已上线"
        : "已下线";

    return (
      <div className="schedule-right-container flex min-h-0 flex-1 flex-col">
        <div className="flex h-9 shrink-0 items-stretch gap-1 border-b border-[#e5e7eb] bg-white px-3">
          {[
            { key: "params" as const, label: "全局参数" },
            { key: "strategy" as const, label: "调度策略" },
            { key: "time" as const, label: "调度时间" },
          ].map((item) => (
            <button
              key={item.key}
              type="button"
              onClick={() => navigateToSection(item.key)}
              className={[
                "relative shrink-0 cursor-pointer px-2 text-xs transition-colors",
                scheduleSection === item.key
                  ? "font-semibold text-[#315efb]"
                  : "text-[#7b8492] hover:text-[#465064]",
              ].join(" ")}
            >
              {item.label}
              {scheduleSection === item.key ? (
                <span className="absolute inset-x-2 bottom-0 h-0.5 rounded-full bg-[#315efb]" />
              ) : null}
            </button>
          ))}
          <div className="ml-auto inline-flex shrink-0 items-center gap-1.5 text-[11px] text-[#687385]">
            <span
              className={[
                "size-1.5 rounded-full",
                scheduleIsOnline ? "bg-[#2da866]" : "bg-[#aab1bc]",
              ].join(" ")}
            />
            {scheduleStatusLabel}
          </div>
        </div>

        <div className="main-content schedule-config-wrapper min-h-0 flex-1 overflow-y-auto px-4 py-3">
          <div className="flow-right-panel-content space-y-4 pb-4">
            {!currentSchedule ? (
              <PanelNotice tone={workflowIsOnline ? "info" : "warning"}>
                {workflowIsOnline
                  ? "尚未配置调度，保存后将创建工作流级调度。"
                  : "请先发布工作流，再创建调度。"}
              </PanelNotice>
            ) : null}

            <GlobalParametersSection controller={controller} renderSectionHeader={renderSectionHeader} />
            <ScheduleStrategySection controller={controller} renderSectionHeader={renderSectionHeader} />
            <ScheduleTimeSection controller={controller} renderSectionHeader={renderSectionHeader} />
          </div>
        </div>
        <div className="shrink-0 border-t border-[#e8ebf0] bg-white px-3 py-2.5">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <PanelButton
              variant="secondary"
              onClick={() => void previewSchedule()}
              disabled={schedulePreviewing || !scheduleForm.crontab.trim()}
            >
              <Clock3 size={13} />
              预览
            </PanelButton>
            <div className="flex items-center gap-2">
              {currentSchedule ? (
                <PanelButton
                  variant={scheduleIsOnline ? "danger" : "secondary"}
                  onClick={() => void toggleSchedule()}
                  disabled={
                    scheduleToggling ||
                    (!scheduleIsOnline && !workflowIsOnline)
                  }
                  title={
                    !scheduleIsOnline && !workflowIsOnline
                      ? "请先发布工作流"
                      : undefined
                  }
                >
                  {scheduleToggling
                    ? "处理中..."
                    : scheduleIsOnline
                      ? "下线调度"
                      : "上线调度"}
                </PanelButton>
              ) : null}
              <PanelButton
                onClick={() => void saveSchedule()}
                disabled={
                  scheduleSaving ||
                  scheduleIsOnline ||
                  (!currentSchedule && !workflowIsOnline) ||
                  !scheduleForm.crontab.trim() ||
                  (scheduleForm.warningType !== "NONE" &&
                    (alertGroupsQuery.isPending ||
                      alertGroupsQuery.isFetching ||
                      alertGroupsQuery.isError ||
                      notificationGroupMissing))
                }
              >
                <Save size={13} />
                {scheduleSaving ? "保存中..." : "保存调度"}
              </PanelButton>
            </div>
          </div>
        </div>
      </div>
    );

}

import type { ChangeEvent, ReactNode } from "react";
import {
  ChevronDown,
  Clock3,
  Plus,
  RefreshCw,
  Save,
  Trash2,
} from "lucide-react";
import { CronSchedulerPicker, toast } from "@yak-ops/yak-ui";

import { PanelButton, PanelNotice } from "../components/PanelPrimitives";
import {
  FAILURE_STRATEGIES,
  GLOBAL_PARAMETER_TYPES,
  MISSED_FIRE_POLICIES,
  MUTED_CLASS,
  PRIORITIES,
  SCHEDULE_CONTROL_CLASS,
  WARNING_TYPES,
} from "../shared/constants";
import type { GlobalParameterType, ScheduleSection } from "../shared/types";
import type {
  WorkflowFailureStrategy,
  WorkflowMissedFirePolicy,
  WorkflowPriority,
  WorkflowWarningType,
} from "@/service/workflow/management";
import type { WorkflowSidePanelController } from "../hooks/useWorkflowSidePanelController";

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
    globalParameters,
    setGlobalParameters,
    savedGlobalParameterCount,
    globalParametersSaving,
    updateGlobalParameter,
    saveGlobalParameters,
    scheduleForm,
    updateScheduleField,
    alertGroupsQuery,
    alertGroups,
    notificationGroupId,
    notificationGroupMissing,
    previewTimes,
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
      <div className="flex h-9 items-center justify-between border-b border-[#e8ebf0]">
        <button
          type="button"
          aria-expanded={!scheduleCollapsed[section]}
          onClick={() => toggleSection(section)}
          className="flex h-full min-w-0 flex-1 items-center gap-2 text-left"
        >
          <ChevronDown
            size={14}
            className={[
              "shrink-0 text-[#7b8492] transition-transform",
              scheduleCollapsed[section] ? "-rotate-90" : "rotate-0",
            ].join(" ")}
          />
          <span className="text-xs font-semibold text-[#303846]">{title}</span>
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
                "relative shrink-0 px-2 text-xs transition-colors",
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

            <section id="workflow-schedule-params" className="scroll-mt-3">
              {renderSectionHeader(
                "params",
                "全局参数",
                <button
                  type="button"
                  disabled={workflowIsOnline}
                  onClick={() =>
                    setGlobalParameters((current) => [
                      ...current,
                      {
                        prop: "PARAM_" + (current.length + 1),
                        direct: "IN",
                        type: "VARCHAR",
                        value: "",
                        sensitive: false,
                      },
                    ])
                  }
                  className="inline-flex h-7 items-center gap-1 rounded px-2 text-[11px] text-[#315efb] transition-colors hover:bg-[#f2f5ff] disabled:cursor-not-allowed disabled:text-[#aab2bf]"
                >
                  <Plus size={12} />
                  添加参数
                </button>,
              )}
              {!scheduleCollapsed.params ? (
                <div className="pb-2 pt-3">
                  {workflowIsOnline ? (
                    <div className="mb-2 text-[11px] text-[#8b93a1]">
                      工作流上线期间不能修改全局参数。
                    </div>
                  ) : null}

                  <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_24px] gap-2 px-0.5 pb-1.5 text-[11px] text-[#8b93a1]">
                    <span>参数名</span>
                    <span>参数值</span>
                    <span className="text-center">操作</span>
                  </div>

                  {globalParameters.length === 0 ? (
                    <div className="border-y border-[#edf0f3] py-5 text-center text-xs text-[#9aa2ad]">
                      暂无全局参数
                    </div>
                  ) : (
                    <div className="divide-y divide-[#edf0f3]">
                      {globalParameters.map((parameter, index) => (
                        <div key={index} className="py-2.5">
                          <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_24px] items-center gap-2">
                            <input
                              aria-label="参数名"
                              className={SCHEDULE_CONTROL_CLASS}
                              value={parameter.prop}
                              placeholder="参数名"
                              disabled={workflowIsOnline}
                              onChange={(event: ChangeEvent<HTMLInputElement>) =>
                                updateGlobalParameter(index, { prop: event.target.value })
                              }
                            />
                            <input
                              aria-label="参数值"
                              className={SCHEDULE_CONTROL_CLASS}
                              type={parameter.sensitive ? "password" : "text"}
                              value={parameter.value}
                              placeholder="参数值"
                              disabled={workflowIsOnline}
                              onChange={(event: ChangeEvent<HTMLInputElement>) =>
                                updateGlobalParameter(index, { value: event.target.value })
                              }
                            />
                            <button
                              type="button"
                              title="删除参数"
                              aria-label="删除参数"
                              disabled={
                                workflowIsOnline ||
                                (savedGlobalParameterCount > 0 && globalParameters.length <= 1)
                              }
                              onClick={() =>
                                setGlobalParameters((current) =>
                                  current.filter((_item, itemIndex) => itemIndex !== index),
                                )
                              }
                              className="flex size-6 items-center justify-center rounded text-[#9aa2ad] transition-colors hover:bg-[#fff3f2] hover:text-[#cf4a43] disabled:cursor-not-allowed disabled:opacity-35"
                            >
                              <Trash2 size={13} />
                            </button>
                          </div>

                          <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-2">
                            <label className="inline-flex items-center gap-1.5 text-[11px] text-[#7a8492]">
                              类型
                              <select
                                className="h-7 rounded-md border border-[#dfe3e8] bg-white px-1.5 text-[11px] text-[#465064] outline-none focus:border-[#8ba9ff] disabled:bg-[#f7f8fa]"
                                value={parameter.type}
                                disabled={workflowIsOnline}
                                onChange={(event: ChangeEvent<HTMLSelectElement>) =>
                                  updateGlobalParameter(index, {
                                    type: event.target.value as GlobalParameterType,
                                  })
                                }
                              >
                                {GLOBAL_PARAMETER_TYPES.map((type) => (
                                  <option key={type} value={type}>
                                    {type}
                                  </option>
                                ))}
                              </select>
                            </label>
                            <label className="inline-flex items-center gap-1.5 text-[11px] text-[#7a8492]">
                              方向
                              <select
                                className="h-7 rounded-md border border-[#dfe3e8] bg-white px-1.5 text-[11px] text-[#465064] outline-none focus:border-[#8ba9ff] disabled:bg-[#f7f8fa]"
                                value={parameter.direct}
                                disabled={workflowIsOnline}
                                onChange={(event: ChangeEvent<HTMLSelectElement>) =>
                                  updateGlobalParameter(index, {
                                    direct: event.target.value as "IN" | "OUT",
                                  })
                                }
                              >
                                <option value="IN">输入</option>
                                <option value="OUT">输出</option>
                              </select>
                            </label>
                            <label className="inline-flex items-center gap-1.5 text-[11px] text-[#7a8492]">
                              <input
                                className="size-3 accent-[#315efb]"
                                type="checkbox"
                                checked={parameter.sensitive}
                                disabled={workflowIsOnline}
                                onChange={(event: ChangeEvent<HTMLInputElement>) => {
                                  if (
                                    !event.target.checked &&
                                    parameter.sensitive &&
                                    parameter.value === "******"
                                  ) {
                                    toast.error("敏感参数值已脱敏，请先重新填写实际值");
                                    return;
                                  }
                                  updateGlobalParameter(index, {
                                    sensitive: event.target.checked,
                                  });
                                }}
                              />
                              敏感参数
                            </label>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}

                  <div className="flex justify-end pt-3">
                    <PanelButton
                      onClick={() => void saveGlobalParameters()}
                      disabled={
                        workflowIsOnline ||
                        globalParametersSaving ||
                        (globalParameters.length === 0 && savedGlobalParameterCount === 0)
                      }
                    >
                      <Save size={13} />
                      {globalParametersSaving ? "保存中..." : "保存参数"}
                    </PanelButton>
                  </div>

                  {savedGlobalParameterCount > 0 && globalParameters.length === 1 ? (
                    <div className="pt-2 text-[11px] text-[#8b93a1]">
                      当前后端暂不支持清空全部已保存参数，请至少保留一项。
                    </div>
                  ) : null}
                </div>
              ) : null}
            </section>

            <section id="workflow-schedule-strategy" className="scroll-mt-3">
              {renderSectionHeader("strategy", "调度策略")}
              {!scheduleCollapsed.strategy ? (
                <div className="space-y-2.5 pb-2 pt-3">
                  <label className="grid grid-cols-[92px_minmax(0,1fr)] items-center gap-3">
                    <span className="text-xs text-[#687385]">失败策略</span>
                    <select
                      className={SCHEDULE_CONTROL_CLASS}
                      value={scheduleForm.failureStrategy}
                      disabled={scheduleIsOnline}
                      onChange={(event: ChangeEvent<HTMLSelectElement>) =>
                        updateScheduleField(
                          "failureStrategy",
                          event.target.value as WorkflowFailureStrategy,
                        )
                      }
                    >
                      {FAILURE_STRATEGIES.map((item) => (
                        <option key={item.value} value={item.value}>
                          {item.label}
                        </option>
                      ))}
                    </select>
                  </label>

                  <label className="grid grid-cols-[92px_minmax(0,1fr)] items-center gap-3">
                    <span className="text-xs text-[#687385]">错过调度处理</span>
                    <select
                      className={SCHEDULE_CONTROL_CLASS}
                      value={scheduleForm.missedFirePolicy}
                      disabled={scheduleIsOnline}
                      onChange={(event: ChangeEvent<HTMLSelectElement>) =>
                        updateScheduleField(
                          "missedFirePolicy",
                          event.target.value as WorkflowMissedFirePolicy,
                        )
                      }
                    >
                      {MISSED_FIRE_POLICIES.map((item) => (
                        <option key={item.value} value={item.value}>
                          {item.label}
                        </option>
                      ))}
                    </select>
                  </label>

                  <label className="grid grid-cols-[92px_minmax(0,1fr)] items-center gap-3">
                    <span className="text-xs text-[#687385]">实例优先级</span>
                    <select
                      className={SCHEDULE_CONTROL_CLASS}
                      value={scheduleForm.workflowInstancePriority}
                      disabled={scheduleIsOnline}
                      onChange={(event: ChangeEvent<HTMLSelectElement>) =>
                        updateScheduleField(
                          "workflowInstancePriority",
                          event.target.value as WorkflowPriority,
                        )
                      }
                    >
                      {PRIORITIES.map((item) => (
                        <option key={item.value} value={item.value}>
                          {item.label}
                        </option>
                      ))}
                    </select>
                  </label>

                  <label className="grid grid-cols-[92px_minmax(0,1fr)] items-center gap-3">
                    <span className="text-xs text-[#687385]">通知策略</span>
                    <select
                      className={SCHEDULE_CONTROL_CLASS}
                      value={scheduleForm.warningType}
                      disabled={scheduleIsOnline}
                      onChange={(event: ChangeEvent<HTMLSelectElement>) =>
                        updateScheduleField(
                          "warningType",
                          event.target.value as WorkflowWarningType,
                        )
                      }
                    >
                      {WARNING_TYPES.map((item) => (
                        <option key={item.value} value={item.value}>
                          {item.label}
                        </option>
                      ))}
                    </select>
                  </label>

                  {scheduleForm.warningType !== "NONE" ? (
                    <label className="grid grid-cols-[92px_minmax(0,1fr)] items-center gap-3">
                      <span className="text-xs text-[#687385]">通知组</span>
                      <div className="min-w-0">
                        <select
                          className={SCHEDULE_CONTROL_CLASS}
                          value={String(notificationGroupId)}
                          disabled={scheduleIsOnline || alertGroupsQuery.isPending}
                          onChange={(event: ChangeEvent<HTMLSelectElement>) =>
                            updateScheduleField("warningGroupId", Number(event.target.value))
                          }
                        >
                          {alertGroups.map((group) => (
                            <option key={group.id} value={String(group.id)}>
                              {group.groupName}
                            </option>
                          ))}
                        </select>
                        {alertGroupsQuery.isPending ? (
                          <span className="mt-1 block text-[11px] text-[#8b93a1]">
                            正在加载通知组...
                          </span>
                        ) : notificationGroupMissing ? (
                          <span className="mt-1 block text-[11px] text-[#c64a43]">
                            暂无通知组，请先在告警中心创建
                          </span>
                        ) : alertGroupsQuery.isError ? (
                          <button
                            type="button"
                            onClick={() => void alertGroupsQuery.refetch()}
                            className="mt-1 text-[11px] text-[#315efb] hover:underline"
                          >
                            通知组加载失败，点击重试
                          </button>
                        ) : null}
                      </div>
                    </label>
                  ) : null}

                  <label className="grid grid-cols-[92px_minmax(0,1fr)] items-center gap-3">
                    <span className="text-xs text-[#687385]">Worker Group</span>
                    <input
                      className={SCHEDULE_CONTROL_CLASS}
                      value={scheduleForm.workerGroup}
                      disabled={scheduleIsOnline}
                      onChange={(event: ChangeEvent<HTMLInputElement>) =>
                        updateScheduleField("workerGroup", event.target.value)
                      }
                    />
                  </label>

                  <label className="grid grid-cols-[92px_minmax(0,1fr)] items-center gap-3">
                    <span className="text-xs text-[#687385]">Tenant Code</span>
                    <input
                      className={SCHEDULE_CONTROL_CLASS}
                      value={scheduleForm.tenantCode}
                      disabled={scheduleIsOnline}
                      onChange={(event: ChangeEvent<HTMLInputElement>) =>
                        updateScheduleField("tenantCode", event.target.value)
                      }
                    />
                  </label>
                </div>
              ) : null}
            </section>

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
                    <select
                      className={SCHEDULE_CONTROL_CLASS}
                      value={scheduleForm.timezoneId}
                      disabled={scheduleIsOnline}
                      onChange={(event: ChangeEvent<HTMLSelectElement>) =>
                        updateScheduleField("timezoneId", event.target.value)
                      }
                    >
                      {Array.from(
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
                      ).map((zone) => (
                        <option key={zone} value={zone}>
                          {zone}
                        </option>
                      ))}
                    </select>
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

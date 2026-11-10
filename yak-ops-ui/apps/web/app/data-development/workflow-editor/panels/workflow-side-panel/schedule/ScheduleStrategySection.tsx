import type { ChangeEvent, ReactNode } from "react";

import type {
  WorkflowFailureStrategy,
  WorkflowMissedFirePolicy,
  WorkflowPriority,
  WorkflowWarningType,
} from "@/service/workflow/management";
import {
  FAILURE_STRATEGIES,
  MISSED_FIRE_POLICIES,
  PRIORITIES,
  SCHEDULE_CONTROL_CLASS,
  WARNING_TYPES,
} from "../shared/constants";
import type { ScheduleSection } from "../shared/types";
import type { WorkflowSidePanelController } from "../hooks/useWorkflowSidePanelController";

type RenderSectionHeader = (
  section: ScheduleSection,
  title: string,
  action?: ReactNode,
) => ReactNode;

export function ScheduleStrategySection({
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
    alertGroupsQuery,
    alertGroups,
    notificationGroupId,
    notificationGroupMissing,
  } = controller;

  return (
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
  );
}

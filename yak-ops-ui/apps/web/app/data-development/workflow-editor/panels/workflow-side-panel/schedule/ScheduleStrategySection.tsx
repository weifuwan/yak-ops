import type { ChangeEvent, ReactNode } from "react";

import {
  FAILURE_STRATEGIES,
  MISSED_FIRE_POLICIES,
  PRIORITIES,
  SCHEDULE_CONTROL_CLASS,
  WARNING_TYPES,
} from "../shared/constants";
import type { ScheduleSection } from "../shared/types";
import type { WorkflowSidePanelController } from "../hooks/useWorkflowSidePanelController";
import { ScheduleSelect } from "./ScheduleSelect";

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
                    <ScheduleSelect
                      ariaLabel="失败策略"
                      value={scheduleForm.failureStrategy}
                      options={FAILURE_STRATEGIES}
                      disabled={scheduleIsOnline}
                      onValueChange={(value) => updateScheduleField("failureStrategy", value)}
                    />
                  </label>

                  <label className="grid grid-cols-[92px_minmax(0,1fr)] items-center gap-3">
                    <span className="text-xs text-[#687385]">错过调度处理</span>
                    <ScheduleSelect
                      ariaLabel="错过调度处理"
                      value={scheduleForm.missedFirePolicy}
                      options={MISSED_FIRE_POLICIES}
                      disabled={scheduleIsOnline}
                      onValueChange={(value) => updateScheduleField("missedFirePolicy", value)}
                    />
                  </label>

                  <label className="grid grid-cols-[92px_minmax(0,1fr)] items-center gap-3">
                    <span className="text-xs text-[#687385]">实例优先级</span>
                    <ScheduleSelect
                      ariaLabel="实例优先级"
                      value={scheduleForm.workflowInstancePriority}
                      options={PRIORITIES}
                      disabled={scheduleIsOnline}
                      onValueChange={(value) =>
                        updateScheduleField("workflowInstancePriority", value)
                      }
                    />
                  </label>

                  <label className="grid grid-cols-[92px_minmax(0,1fr)] items-center gap-3">
                    <span className="text-xs text-[#687385]">通知策略</span>
                    <ScheduleSelect
                      ariaLabel="通知策略"
                      value={scheduleForm.warningType}
                      options={WARNING_TYPES}
                      disabled={scheduleIsOnline}
                      onValueChange={(value) => updateScheduleField("warningType", value)}
                    />
                  </label>

                  {scheduleForm.warningType !== "NONE" ? (
                    <label className="grid grid-cols-[92px_minmax(0,1fr)] items-center gap-3">
                      <span className="text-xs text-[#687385]">通知组</span>
                      <div className="min-w-0">
                        <ScheduleSelect
                          ariaLabel="通知组"
                          value={
                            alertGroups.some((group) => group.id === notificationGroupId)
                              ? String(notificationGroupId)
                              : null
                          }
                          options={alertGroups.map((group) => ({
                            value: String(group.id),
                            label: group.groupName,
                          }))}
                          disabled={scheduleIsOnline || alertGroupsQuery.isPending}
                          placeholder="请选择通知组"
                          onValueChange={(value) =>
                            updateScheduleField("warningGroupId", Number(value))
                          }
                        />
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

import type { ChangeEvent, ReactNode } from "react";
import { Plus, Save, Trash2 } from "lucide-react";
import { toast } from "@yak-ops/yak-ui";

import { PanelButton } from "../components/PanelPrimitives";
import {
  GLOBAL_PARAMETER_TYPES,
  SCHEDULE_CONTROL_CLASS,
} from "../shared/constants";
import type { ScheduleSection } from "../shared/types";
import type { WorkflowSidePanelController } from "../hooks/useWorkflowSidePanelController";
import { ScheduleSelect } from "./ScheduleSelect";

type RenderSectionHeader = (
  section: ScheduleSection,
  title: string,
  action?: ReactNode,
) => ReactNode;

export function GlobalParametersSection({
  controller,
  renderSectionHeader,
}: {
  controller: WorkflowSidePanelController;
  renderSectionHeader: RenderSectionHeader;
}) {
  const {
    scheduleCollapsed,
    workflowIsOnline,
    globalParameters,
    setGlobalParameters,
    savedGlobalParameterCount,
    globalParametersSaving,
    updateGlobalParameter,
    saveGlobalParameters,
  } = controller;

  return (
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
                              <ScheduleSelect
                                ariaLabel="参数类型"
                                value={parameter.type}
                                options={GLOBAL_PARAMETER_TYPES.map((type) => ({
                                  value: type,
                                  label: type,
                                }))}
                                disabled={workflowIsOnline}
                                triggerClassName="w-auto min-w-[112px]"
                                onValueChange={(type) => updateGlobalParameter(index, { type })}
                              />
                            </label>
                            <label className="inline-flex items-center gap-1.5 text-[11px] text-[#7a8492]">
                              方向
                              <ScheduleSelect
                                ariaLabel="参数方向"
                                value={parameter.direct}
                                options={[
                                  { value: "IN", label: "输入" },
                                  { value: "OUT", label: "输出" },
                                ]}
                                disabled={workflowIsOnline}
                                triggerClassName="w-auto min-w-[76px]"
                                onValueChange={(direct) => updateGlobalParameter(index, { direct })}
                              />
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
  );
}

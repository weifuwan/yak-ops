import type { ChangeEvent } from "react";
import { Save } from "lucide-react";

import { PanelButton } from "../components/PanelPrimitives";
import {
  EXECUTION_TYPES,
  PROPERTY_CONTROL_CLASS,
  PROPERTY_TEXTAREA_CLASS,
} from "../shared/constants";
import type { ExecutionType } from "../shared/types";
import type { WorkflowSidePanelController } from "../hooks/useWorkflowSidePanelController";
import { GlobalParametersSection } from "./GlobalParametersSection";

export function PropertiesPanel({
  controller,
}: {
  controller: WorkflowSidePanelController;
}) {
  const {
    propertyForm,
    setPropertyForm,
    workflowIsOnline,
    propertySaving,
    saveProperties,
  } = controller;

  return (
    <div className="space-y-4 px-4 py-3">
      <section className="rounded-md border border-[#e8ebef] bg-white px-3 py-2">
        <div className="mb-1 border-b border-[#edf0f3] pb-2">
          <h3 className="text-xs font-semibold text-[#303846]">基本信息</h3>
          <p className="mt-1 text-[11px] text-[#8b93a1]">
            定义工作流本身的名称、说明和整体执行行为。
          </p>
        </div>

        <div className="space-y-1">
          <div className="grid grid-cols-[100px_minmax(0,1fr)] items-center gap-3 py-2.5">
            <label htmlFor="workflow-property-name" className="text-xs text-[#687385]">名称</label>
            <input
              id="workflow-property-name"
              className={PROPERTY_CONTROL_CLASS}
              value={propertyForm.name}
              disabled={workflowIsOnline}
              maxLength={100}
              onChange={(event: ChangeEvent<HTMLInputElement>) =>
                setPropertyForm(current => ({ ...current, name: event.target.value }))
              }
            />
          </div>

          <div className="grid grid-cols-[100px_minmax(0,1fr)] items-start gap-3 py-2.5">
            <label htmlFor="workflow-property-description" className="pt-2 text-xs text-[#687385]">描述</label>
            <textarea
              id="workflow-property-description"
              className={PROPERTY_TEXTAREA_CLASS}
              rows={2}
              maxLength={255}
              value={propertyForm.description}
              disabled={workflowIsOnline}
              onChange={(event: ChangeEvent<HTMLTextAreaElement>) =>
                setPropertyForm(current => ({ ...current, description: event.target.value }))
              }
            />
          </div>

          <div className="grid grid-cols-[100px_minmax(0,1fr)] items-center gap-3 py-2.5">
            <label htmlFor="workflow-property-timeout" className="text-xs text-[#687385]">工作流超时（分钟）</label>
            <input
              id="workflow-property-timeout"
              className={PROPERTY_CONTROL_CLASS}
              type="number"
              min={0}
              step={1}
              value={propertyForm.timeout}
              disabled={workflowIsOnline}
              onChange={(event: ChangeEvent<HTMLInputElement>) =>
                setPropertyForm(current => ({ ...current, timeout: event.target.value }))
              }
            />
          </div>

          <div className="grid grid-cols-[100px_minmax(0,1fr)] items-center gap-3 py-2.5">
            <label htmlFor="workflow-property-execution-type" className="text-xs text-[#687385]">执行方式</label>
            <select
              id="workflow-property-execution-type"
              className={PROPERTY_CONTROL_CLASS}
              value={propertyForm.executionType}
              disabled={workflowIsOnline}
              onChange={(event: ChangeEvent<HTMLSelectElement>) =>
                setPropertyForm(current => ({
                  ...current,
                  executionType: event.target.value as ExecutionType,
                }))
              }
            >
              {EXECUTION_TYPES.map(item => (
                <option key={item.value} value={item.value}>{item.label}</option>
              ))}
            </select>
          </div>
        </div>

        <div className="flex justify-end border-t border-[#edf0f3] pt-3">
          <PanelButton onClick={() => void saveProperties()} disabled={workflowIsOnline || propertySaving}>
            <Save size={13} />
            {propertySaving ? "保存中..." : "保存属性"}
          </PanelButton>
        </div>
      </section>

      <GlobalParametersSection controller={controller} />
    </div>
  );
}

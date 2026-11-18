import { FolderOpen } from "lucide-react";
import type { TreeSelectOption } from "@yak-ops/yak-ui";

import type {
  ShellConfigTab,
  ShellLocalParam,
  ShellTaskForm,
  ShellTaskUpdateField,
} from "../types";
import { ShellParametersPanel } from "./shell-parameters-panel";
import { ShellResourcesPanel } from "./shell-resources-panel";

export function ShellConfigPanel({
  form,
  readOnly,
  activeTab,
  onActiveTabChange,
  updateField,
  updateLocalParam,
  resourceTree,
  resourceLoading,
  resourceError,
  onRetryResources,
}: {
  form: ShellTaskForm;
  readOnly: boolean;
  activeTab: ShellConfigTab;
  onActiveTabChange: (tab: ShellConfigTab) => void;
  updateField: ShellTaskUpdateField;
  updateLocalParam: (index: number, patch: Partial<ShellLocalParam>) => void;
  resourceTree: TreeSelectOption[];
  resourceLoading: boolean;
  resourceError: boolean;
  onRetryResources: () => void;
}) {
  return (
    <section className="flex h-[286px] max-h-[38vh] min-h-[220px] shrink-0 flex-col border-t border-[#e5e7eb] bg-white">
      <nav className="flex h-9 shrink-0 items-center gap-1 border-b border-[#edf0f3] bg-[#fbfcfe] px-3">
        <button
          type="button"
          aria-pressed={activeConfigTab === "parameters"}
          onClick={() => setActiveConfigTab("parameters")}
          className={[
            "flex h-full cursor-pointer items-center gap-1.5 border-b-2 px-2 text-xs transition-colors",
            activeConfigTab === "parameters"
              ? "border-[#315efb] font-semibold text-[#315efb]"
              : "border-transparent text-[#687385] hover:text-[#303846]",
          ].join(" ")}
        >
          自定义参数
          <span className="rounded-full bg-[#edf1f7] px-1.5 py-0.5 text-[10px] text-[#687385]">
            {form.localParams.length}
          </span>
        </button>
        <button
          type="button"
          aria-pressed={activeConfigTab === "resources"}
          onClick={() => setActiveConfigTab("resources")}
          className={[
            "flex h-full cursor-pointer items-center gap-1.5 border-b-2 px-2 text-xs transition-colors",
            activeConfigTab === "resources"
              ? "border-[#315efb] font-semibold text-[#315efb]"
              : "border-transparent text-[#687385] hover:text-[#303846]",
          ].join(" ")}
        >
          <FolderOpen size={13} />
          依赖资源
          <span className="rounded-full bg-[#edf1f7] px-1.5 py-0.5 text-[10px] text-[#687385]">
            {form.resourceList.length}
          </span>
        </button>
        <span className="ml-auto hidden text-[10px] text-[#98a1ad] lg:inline">
          参数配置沿用 DolphinScheduler localParams 结构
        </span>
      </nav>
      <div className="min-h-0 flex-1 overflow-auto p-3">
        {activeTab === "parameters" ? (
          <ShellParametersPanel
            form={form}
            readOnly={readOnly}
            updateField={updateField}
            updateLocalParam={updateLocalParam}
          />
        ) : (
          <ShellResourcesPanel
            form={form}
            readOnly={readOnly}
            updateField={updateField}
            resourceTree={resourceTree}
            resourceLoading={resourceLoading}
            resourceError={resourceError}
            onRetryResources={onRetryResources}
          />
        )}
      </div>
    </section>
  );
}

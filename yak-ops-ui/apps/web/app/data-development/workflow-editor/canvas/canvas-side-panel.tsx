import {
  Activity,
  Code2,
  List,
  X,
  type LucideIcon,
} from "lucide-react";

export type CanvasPanelKey = "nodes" | "spec" | "operations";

const PANEL_CONTENT: Record<
  CanvasPanelKey,
  {
    title: string;
    description: string;
    placeholder: string;
    Icon: LucideIcon;
  }
> = {
  nodes: {
    title: "内部节点列表",
    description: "查看当前工作流中的节点。",
    placeholder:
      "内部节点列表区域已预留。后续在这里实现节点搜索、列表展示和画布定位。",
    Icon: List,
  },
  spec: {
    title: "Workflow Spec",
    description: "查看当前工作流的定义内容。",
    placeholder:
      "Spec 内容区域已预留。后续在这里展示工作流基本信息、节点定义和依赖关系。",
    Icon: Code2,
  },
  operations: {
    title: "运维中心",
    description: "查看当前工作流相关的运维信息。",
    placeholder:
      "运维中心区域已预留。后续在这里接入运行状态、实例记录和运维操作。",
    Icon: Activity,
  },
};

export function CanvasSidePanel({
  activePanel,
  onClose,
}: {
  activePanel: CanvasPanelKey | null;
  onClose: () => void;
}) {
  const content = activePanel ? PANEL_CONTENT[activePanel] : null;
  const Icon = content?.Icon;

  return (
    <aside
      id="workflow-canvas-side-panel"
      role="complementary"
      aria-label={content?.title ?? "画布辅助面板"}
      aria-hidden={!content}
      className="absolute bottom-3 right-3 top-[60px] z-20 flex w-[380px] max-w-[calc(100%-24px)] flex-col overflow-hidden rounded-lg border border-[#dfe3e8] bg-white shadow-xl transition-transform duration-200 ease-out"
      style={{
        transform: content
          ? "translateX(0)"
          : "translateX(calc(100% + 16px))",
      }}
    >
      <div className="flex h-11 shrink-0 items-center justify-between border-b border-[#edf0f3] px-3">
        <div className="flex min-w-0 items-center gap-2">
          {Icon ? (
            <Icon size={15} className="shrink-0 text-[#4779df]" />
          ) : null}
          <span className="truncate text-sm font-medium text-[#303846]">
            {content?.title ?? "画布辅助面板"}
          </span>
        </div>
        <button
          type="button"
          title="关闭面板"
          aria-label="关闭面板"
          tabIndex={content ? 0 : -1}
          onClick={onClose}
          className="flex size-7 shrink-0 cursor-pointer items-center justify-center rounded-md text-[#7b8492] transition-colors hover:bg-[#f3f5f8] hover:text-[#303846]"
        >
          <X size={15} />
        </button>
      </div>

      <div className="min-h-0 flex-1 overflow-auto p-4">
        {content ? (
          <div className="space-y-3">
            <p className="text-xs leading-5 text-[#687282]">
              {content.description}
            </p>
            <div className="rounded-md border border-dashed border-[#dfe3e8] bg-[#fafbfc] p-4">
              <p className="text-xs leading-6 text-[#7b8492]">
                {content.placeholder}
              </p>
            </div>
          </div>
        ) : null}
      </div>
    </aside>
  );
}

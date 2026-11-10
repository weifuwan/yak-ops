import {
  Code2,
  List,
  X,
  type LucideIcon,
} from "lucide-react";

export type CanvasPanelKey = "nodes" | "spec";

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
    title: "Spec",
    description: "查看当前工作流的定义内容。",
    placeholder:
      "Spec 内容区域已预留。后续在这里展示工作流基本信息、节点定义和依赖关系。",
    Icon: Code2,
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
      className={[
        "h-full min-h-0 shrink-0 overflow-hidden bg-white transition-[width,flex-basis] duration-200 ease-out",
        content ? "border-l border-[#e5e7eb]" : "",
      ].join(" ")}
      style={{
        width: content ? "min(64%, 600px)" : "0px",
        flexBasis: content ? "min(64%, 600px)" : "0px",
        pointerEvents: content ? "auto" : "none",
      }}
    >
      <div className="flex h-full min-h-0 w-full flex-col">
        <header className="flex h-12 shrink-0 items-center justify-between border-b border-[#e5e7eb] px-4">
          <div className="flex min-w-0 items-center gap-2">
            {Icon ? (
              <Icon size={15} className="shrink-0 text-[#687385]" />
            ) : null}
            <span className="truncate text-sm font-semibold text-[#303846]">
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
        </header>

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
      </div>
    </aside>
  );
}

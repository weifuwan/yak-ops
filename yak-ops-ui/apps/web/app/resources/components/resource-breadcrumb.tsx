import { ArrowUp, House } from "lucide-react";
import type { WorkflowResourceNode } from "@/service/resources";

interface ResourceBreadcrumbProps {
  path: WorkflowResourceNode[];
  total: number;
  canGoParent: boolean;
  onParentClick: () => void;
  onRootClick: () => void;
  onOpen: (node: WorkflowResourceNode) => void;
}

export function ResourceBreadcrumb({
  path,
  total,
  canGoParent,
  onParentClick,
  onRootClick,
  onOpen,
}: ResourceBreadcrumbProps) {
  return (
    <nav
      aria-label="资源路径"
      className="mb-3 flex min-h-8 min-w-0 items-center gap-1.5 text-[11px]"
    >
      <button
        type="button"
        title="返回上级"
        aria-label="返回上级"
        disabled={!canGoParent}
        onClick={onParentClick}
        className="flex size-7 shrink-0 cursor-pointer items-center justify-center rounded-md text-[#788394] transition-colors hover:bg-[#f1f3f6] hover:text-[#303846] disabled:cursor-not-allowed disabled:opacity-35"
      >
        <ArrowUp size={14} />
      </button>
      <button
        type="button"
        title="返回根目录"
        onClick={onRootClick}
        className={[
          "flex shrink-0 cursor-pointer items-center gap-1.5 rounded-md px-2 py-1.5 transition-colors hover:bg-[#f4f6f8]",
          path.length === 0 ? "font-medium text-[#394150]" : "text-[#8b94a2]",
        ].join(" ")}
      >
        <House size={14} className="block shrink-0" />
        <span className="inline-flex h-[14px] items-center leading-[14px]">资源文件</span>
      </button>
      {path.map((node, index) => (
        <span
          key={node.fullName || node.name}
          className="flex min-w-0 items-center gap-1"
        >
          <span aria-hidden="true" className="text-[#c0c6cf]">/</span>
          <button
            type="button"
            onClick={() => onOpen(node)}
            title={node.name}
            className={[
              "min-w-0 max-w-40 truncate rounded px-1.5 py-1.5 cursor-pointer transition-colors hover:bg-[#f4f6f8]",
              index === path.length - 1
                ? "font-medium text-[#394150]"
                : "text-[#8b94a2]",
            ].join(" ")}
          >
            {node.name}
          </button>
        </span>
      ))}
      <span className="ml-auto hidden shrink-0 text-[10px] text-[#a0a7b2] sm:inline">
        {total} 项资源
      </span>
    </nav>
  );
}

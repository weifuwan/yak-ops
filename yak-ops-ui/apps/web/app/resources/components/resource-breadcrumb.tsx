import type { WorkflowResourceNode } from "@/service/resources";

interface ResourceBreadcrumbProps {
  path: WorkflowResourceNode[];
  total: number;
  onRootClick: () => void;
  onOpen: (node: WorkflowResourceNode) => void;
}

export function ResourceBreadcrumb({
  path,
  total,
  onRootClick,
  onOpen,
}: ResourceBreadcrumbProps) {
  return (
    <nav
      aria-label="资源路径"
      className="mb-3 flex min-h-7 min-w-0 items-center gap-1 text-[11px]"
    >
      <button
        type="button"
        onClick={onRootClick}
        className={[
          "shrink-0 cursor-pointer rounded px-1.5 py-1 transition-colors hover:bg-[#f4f6f8]",
          path.length === 0 ? "font-medium text-[#394150]" : "text-[#8b94a2]",
        ].join(" ")}
      >
        资源文件
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
            className={[
              "min-w-0 max-w-40 truncate rounded px-1.5 py-1 cursor-pointer transition-colors hover:bg-[#f4f6f8]",
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

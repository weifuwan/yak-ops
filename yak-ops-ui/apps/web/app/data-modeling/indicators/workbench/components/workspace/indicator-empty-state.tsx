import { FolderOpen } from "lucide-react";

export function IndicatorEmptyState() {
  return (
    <div className="absolute inset-0 flex min-h-0 items-center justify-center bg-white">
      <div className="flex flex-col items-center text-center">
        <div
          aria-hidden="true"
          className="relative mb-5 flex h-[116px] w-[144px] items-center justify-center text-[var(--yak-components-muted-text)] opacity-20"
        >
          <div className="absolute left-7 top-3 size-8 rounded-full border border-current opacity-40" />
          <div className="absolute right-6 top-5 size-4 rounded-full border border-current opacity-40" />
          <FolderOpen size={78} strokeWidth={1.2} />
        </div>
        <p className="text-xs leading-5 text-[var(--yak-components-muted-text)]">
          请点击左侧文件开始编辑
        </p>
      </div>
    </div>
  );
}

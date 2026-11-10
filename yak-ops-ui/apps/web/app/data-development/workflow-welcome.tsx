import { Zap } from "lucide-react";

const SHORTCUTS = [
  { label: "显示所有命令", keys: ["Ctrl", "Shift", "P"] },
  { label: "转到文件", keys: ["Ctrl", "P"] },
  { label: "打开“设置”", keys: ["Ctrl", ","] },
  { label: "在文件中查找", keys: ["Ctrl", "Shift", "F"] },
] as const;

/**
 * 暂用 DataWorks 风格的线性图形作为欢迎页占位 Logo。
 * 后续替换 Yak-ops 品牌 SVG 时，只需要调整这个组件。
 */
function WorkflowWelcomeLogo() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 240 240"
      className="h-[220px] w-[220px] shrink-0 text-[#e4e4e4]"
      fill="none"
    >
      <g
        stroke="currentColor"
        strokeWidth="14"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <path d="M 28 166 V 82 L 108 34 V 82 L 73 103 V 193" />
        <path d="M 73 193 V 103 L 153 55 V 103 L 119 124 V 220" />
        <path d="M 119 220 V 124 L 192 80 V 128 L 164 145 V 193" />
        <path d="M 192 128 V 166" />
      </g>
      <g fill="currentColor">
        <circle cx="28" cy="166" r="8" />
        <circle cx="73" cy="193" r="8" />
        <circle cx="119" cy="220" r="8" />
        <circle cx="164" cy="193" r="8" />
        <circle cx="192" cy="166" r="8" />
      </g>
    </svg>
  );
}

function ShortcutKeys({ keys }: { keys: readonly string[] }) {
  return (
    <span className="inline-flex items-center gap-[3px]">
      {keys.map((key, index) => (
        <span key={key} className="inline-flex items-center gap-[3px]">
          {index > 0 ? (
            <span aria-hidden="true" className="text-[10px] text-[#999]">
              +
            </span>
          ) : null}
          <kbd className="inline-flex h-[17px] min-w-[17px] items-center justify-center rounded-[3px] border border-[#e6e6e6] border-b-[#d3d3d3] bg-[#f4f4f4] px-[4px] text-[10px] font-normal leading-none text-[#666] shadow-[inset_0_-1px_0_rgba(0,0,0,0.08)]">
            {key}
          </kbd>
        </span>
      ))}
    </span>
  );
}

export function WorkflowWelcome() {
  return (
    <div className="flex h-full min-h-0 items-center justify-center overflow-hidden p-8 text-center">
      <div className="mx-auto flex h-[calc(100%-30px)] w-full max-w-[290px] flex-col items-center justify-center">
        <WorkflowWelcomeLogo />

        <div className="mt-5 grid grid-cols-[1fr_auto] items-center gap-x-[10px] gap-y-[13px]">
          {SHORTCUTS.map((shortcut) => (
            <div key={shortcut.label} className="contents">
              <span className="whitespace-nowrap text-right text-[12px] leading-[17px] tracking-[0.02em] text-[#858585]">
                {shortcut.label}
              </span>
              <ShortcutKeys keys={shortcut.keys} />
            </div>
          ))}
        </div>

        <div className="mt-7 inline-flex items-center gap-1 whitespace-nowrap text-[12px] text-[#3b73ed]">
          <Zap size={14} strokeWidth={1.8} aria-hidden="true" />
          <span>显示欢迎页面</span>
        </div>
      </div>
    </div>
  );
}

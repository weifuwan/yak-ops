import { Zap } from "lucide-react";

type DataAgentSidebarFooterProps = {
  userName: string;
  userInitial: string;
};

export function DataAgentSidebarFooter({
  userName,
  userInitial,
}: DataAgentSidebarFooterProps) {
  return (
    <footer className="mt-auto shrink-0 px-1">
      <div className="flex h-[58px] items-center justify-between rounded-[14px] border border-[#ddddda] bg-[#fafafa] px-3 shadow-[0_1px_2px_rgba(0,0,0,0.025)]">
        <div className="min-w-0">
          <div className="truncate text-xs font-semibold text-[#20201e]">
            Upgrade to Pro
          </div>
          <div className="truncate text-[10px] leading-4 text-[#777777]">
            Unlock more features
          </div>
        </div>
        <span className="ml-2 flex size-8 shrink-0 items-center justify-center rounded-full bg-[#eee2ff] text-[#7541d8]">
          <Zap size={17} fill="currentColor" strokeWidth={1.8} />
        </span>
      </div>

      <div className="flex h-8 items-center px-1.5 pt-2">
        <span
          title={userName}
          aria-label={userName}
          className="relative flex size-5 items-center justify-center rounded-full bg-[#ed3f80] text-[10px] font-semibold text-white"
        >
          {userInitial}
          <span className="absolute -right-0.5 -top-0.5 size-2 rounded-full border border-[#f0f0f0] bg-[#f04452]" />
        </span>
      </div>
    </footer>
  );
}

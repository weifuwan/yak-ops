import {
  ChevronRight,
  Code2,
  Database,
  ListChecks,
  MessageSquareText,
  type LucideIcon,
} from "lucide-react";

type DataAgentWelcomeProps = {
  onEnter: (prompt?: string) => void;
};

const ENTRYPOINTS: {
  id: string;
  label: string;
  description: string;
  prompt: string;
  Icon: LucideIcon;
}[] = [
  {
    id: "metadata",
    label: "数据表 / 元数据",
    description: "粘贴表结构、字段或查询结果",
    prompt: "请帮我分析下面的数据表结构和字段含义：",
    Icon: Database,
  },
  {
    id: "sql",
    label: "SQL / 脚本",
    description: "分析、解释或优化 SQL 与代码",
    prompt: "请帮我检查、解释并优化下面的 SQL 或脚本：",
    Icon: Code2,
  },
  {
    id: "logs",
    label: "任务运行日志",
    description: "定位任务失败和运行异常原因",
    prompt: "请帮我分析下面的任务运行日志，定位异常原因并给出修复建议：",
    Icon: ListChecks,
  },
  {
    id: "prompt",
    label: "直接描述需求",
    description: "用自然语言告诉我想完成什么",
    prompt: "",
    Icon: MessageSquareText,
  },
];

export function DataAgentWelcome({ onEnter }: DataAgentWelcomeProps) {
  return (
    <div className="flex h-dvh min-h-[520px] w-full flex-col overflow-hidden bg-[#f4f6f6] p-2 text-[#1c292b]">
      <header className="flex h-7 shrink-0 items-center justify-between px-0.5 pb-2">
        <button
          type="button"
          aria-label="进入 Data Agent 工作区"
          title="进入 Data Agent 工作区"
          className="inline-flex h-7 cursor-pointer items-center gap-2 rounded-[7px] border-0 bg-transparent px-[5px] text-[13px] font-semibold text-[#172426] transition-colors duration-150 hover:bg-[#e9eeee] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[rgba(254,44,85,0.42)] motion-reduce:transition-none"
          onClick={() => onEnter()}
        >
          <img
            src="/yakable_logo.png"
            alt=""
            draggable={false}
            className="block size-[17px] object-contain"
          />
          <span>Data Agent</span>
        </button>
        <span className="text-[9px] font-semibold tracking-[0.12em] text-[#9aa4a4] max-[640px]:hidden">
          YAK-OPS · DATA AGENT
        </span>
      </header>

      <main className="relative flex min-h-0 flex-1 items-center justify-center overflow-hidden rounded-[14px] border border-[#dfe4e4] bg-white shadow-[0_1px_2px_rgb(20_34_35_/_3%)]">
        <div className="flex min-h-full w-full items-center justify-center overflow-y-auto px-5 py-9 pb-12 max-[640px]:px-[14px] max-[640px]:pt-6 max-[640px]:pb-[72px] [@media(max-height:680px)]:py-[18px]">
          <section
            aria-label="Data Agent 欢迎介绍"
            className="flex w-[min(100%,396px)] flex-col gap-2.5 [@media(max-height:680px)]:gap-[7px]"
          >
            <div className="flex flex-col items-start gap-1.5">
              <p
                className="m-0 max-w-full animate-data-agent-message-in rounded-[19px] bg-[#f0f2f2] px-3.5 py-2.5 text-[13.5px] leading-[1.55] text-[#1e2b2d] opacity-0 max-[640px]:text-[13px] [@media(max-height:680px)]:py-2 motion-reduce:animate-none motion-reduce:opacity-100 motion-reduce:transform-none"
                style={{ animationDelay: "80ms" }}
              >
                你好，欢迎使用 Yak-OPS Data Agent。我是你的数据开发助手。
              </p>
              <p
                className="m-0 max-w-full animate-data-agent-message-in rounded-[19px] bg-[#f0f2f2] px-3.5 py-2.5 text-[13.5px] leading-[1.55] text-[#1e2b2d] opacity-0 max-[640px]:text-[13px] [@media(max-height:680px)]:py-2 motion-reduce:animate-none motion-reduce:opacity-100 motion-reduce:transform-none"
                style={{ animationDelay: "180ms" }}
              >
                我可以帮你理解数据、编写 SQL、排查任务异常，也可以协助你梳理数据开发流程。
              </p>
              <p
                className="m-0 max-w-full animate-data-agent-message-in rounded-[19px] bg-[#f0f2f2] px-3.5 py-2.5 text-[13.5px] leading-[1.55] text-[#1e2b2d] opacity-0 max-[640px]:text-[13px] [@media(max-height:680px)]:py-2 motion-reduce:animate-none motion-reduce:opacity-100 motion-reduce:transform-none"
                style={{ animationDelay: "280ms" }}
              >
                你可以提供数据表结构、SQL 脚本或运行日志，也可以直接告诉我你想完成什么。
              </p>
              <p
                className="m-0 max-w-[82%] animate-data-agent-message-in self-end rounded-[19px] bg-[#f2f4f4] px-3.5 py-2.5 text-[13.5px] leading-[1.55] text-[#1e2b2d] opacity-0 max-[640px]:text-[13px] [@media(max-height:680px)]:py-2 motion-reduce:animate-none motion-reduce:opacity-100 motion-reduce:transform-none"
                style={{ animationDelay: "380ms" }}
              >
                我们从什么开始？
              </p>
            </div>

            <div
              aria-label="选择开始方式"
              className="flex flex-col gap-0.5 overflow-hidden rounded-[18px] border border-[#dfe4e4] bg-white/[0.92] p-1.5 opacity-0 shadow-[0_2px_8px_rgb(20_34_35_/_3%)] animate-data-agent-card-in motion-reduce:animate-none motion-reduce:opacity-100 motion-reduce:transform-none"
              role="group"
            >
              {ENTRYPOINTS.map((entry, index) => {
                const Icon = entry.Icon;
                return (
                  <button
                    key={entry.id}
                    type="button"
                    className="group/entry flex min-h-[60px] w-full cursor-pointer items-center gap-[11px] rounded-xl border-0 bg-transparent px-[9px] py-1.5 text-left opacity-0 transition-[background-color,transform] duration-[180ms] hover:translate-x-0.5 hover:bg-[#f6f8f7] focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-[rgba(254,44,85,0.38)] max-[640px]:min-h-[58px] max-[640px]:gap-[9px] max-[640px]:px-[7px] [@media(max-height:680px)]:min-h-[54px] motion-reduce:animate-none motion-reduce:opacity-100 motion-reduce:transition-none motion-reduce:transform-none animate-data-agent-message-in"
                    style={{ animationDelay: 460 + index * 70 + "ms" }}
                    onClick={() => onEnter(entry.prompt)}
                  >
                    <span className="first:border-[#b7d8c8] first:bg-[#eef8f2] first:text-[#388c66] flex h-12 w-16 shrink-0 items-center justify-center rounded-[9px] border border-dashed border-[#cbd4d2] bg-[#f6f8f7] text-[#536967] max-[640px]:w-14 max-[640px]:basis-14">
                      <Icon size={19} strokeWidth={1.7} />
                    </span>
                    <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                      <span className="overflow-hidden text-ellipsis whitespace-nowrap text-[13px] font-semibold leading-5 text-[#1d2a2c]">
                        {entry.label}
                      </span>
                      <span className="overflow-hidden text-ellipsis whitespace-nowrap text-[11px] leading-[17px] text-[#899393]">
                        {entry.description}
                      </span>
                    </span>
                    <ChevronRight
                      size={16}
                      strokeWidth={1.6}
                      className="shrink-0 text-[#aab3b2] transition-[color,transform] duration-150 group-hover/entry:translate-x-0.5 group-hover/entry:text-[#536967] motion-reduce:transition-none"
                    />
                  </button>
                );
              })}
            </div>
          </section>
        </div>

        <div
          aria-hidden="true"
          className="absolute right-[max(28px,calc(50%_-_300px))] bottom-8 h-[76px] w-[37px] -rotate-[9deg] animate-data-agent-mascot-float rounded-full bg-[linear-gradient(112deg,#b9f1bd_0%,#86d48e_42%,#55a968_100%)] shadow-[inset_3px_0_5px_rgb(255_255_255_/_50%),inset_-4px_-2px_7px_rgb(28_99_50_/_16%),0_5px_8px_rgb(24_72_42_/_15%)] max-[640px]:right-[18px] max-[640px]:bottom-3 max-[640px]:h-[58px] max-[640px]:w-7 max-[640px]:opacity-[0.85] [@media(max-height:680px)]:hidden motion-reduce:animate-none motion-reduce:transform-none"
        >
          <span className="absolute top-[17px] left-[10px] h-[7px] w-[5px] rounded-[50%] bg-[radial-gradient(circle_at_35%_28%,#64716b_0_12%,#18251f_45%,#07120c_100%)] shadow-[0_0_1px_rgb(0_0_0_/_35%)] max-[640px]:top-[13px] max-[640px]:left-2 max-[640px]:h-[5px] max-[640px]:w-1" />
          <span className="absolute top-4 left-[21px] h-[7px] w-[5px] rounded-[50%] bg-[radial-gradient(circle_at_35%_28%,#64716b_0_12%,#18251f_45%,#07120c_100%)] shadow-[0_0_1px_rgb(0_0_0_/_35%)] max-[640px]:top-3 max-[640px]:left-[17px] max-[640px]:h-[5px] max-[640px]:w-1" />
          <span className="absolute top-2 left-[7px] h-[22px] w-2 rotate-[-8deg] rounded-[50%] bg-white/[0.28] blur-[2px]" />
        </div>
      </main>
    </div>
  );
}

export default DataAgentWelcome;

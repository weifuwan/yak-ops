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
    <div className="data-agent-welcome-shell">
      <header className="data-agent-welcome-header">
        <button
          type="button"
          aria-label="进入 Data Agent 工作区"
          title="进入 Data Agent 工作区"
          className="data-agent-welcome-brand"
          onClick={() => onEnter()}
        >
          <img src="/yakable_logo.png" alt="" draggable={false} />
          <span>Data Agent</span>
        </button>
        <span className="data-agent-welcome-kicker">YAK-OPS · DATA AGENT</span>
      </header>

      <main className="data-agent-welcome-panel">
        <div className="data-agent-welcome-content">
          <section
            aria-label="Data Agent 欢迎介绍"
            className="data-agent-welcome-conversation"
          >
            <div className="data-agent-welcome-messages">
              <p className="data-agent-welcome-message" style={{ animationDelay: "80ms" }}>
                你好，欢迎使用 Yak-OPS Data Agent。我是你的数据开发助手。
              </p>
              <p className="data-agent-welcome-message" style={{ animationDelay: "180ms" }}>
                我可以帮你理解数据、编写 SQL、排查任务异常，也可以协助你梳理数据开发流程。
              </p>
              <p className="data-agent-welcome-message" style={{ animationDelay: "280ms" }}>
                你可以提供数据表结构、SQL 脚本或运行日志，也可以直接告诉我你想完成什么。
              </p>
              <p
                className="data-agent-welcome-message data-agent-welcome-message-outgoing"
                style={{ animationDelay: "380ms" }}
              >
                我们从什么开始？
              </p>
            </div>

            <div
              aria-label="选择开始方式"
              className="data-agent-welcome-entrypoints"
              role="group"
            >
              {ENTRYPOINTS.map((entry, index) => {
                const Icon = entry.Icon;
                return (
                  <button
                    key={entry.id}
                    type="button"
                    className="data-agent-welcome-entry"
                    style={{ animationDelay: 460 + index * 70 + "ms" }}
                    onClick={() => onEnter(entry.prompt)}
                  >
                    <span className="data-agent-welcome-entry-icon">
                      <Icon size={19} strokeWidth={1.7} />
                    </span>
                    <span className="data-agent-welcome-entry-copy">
                      <span className="data-agent-welcome-entry-title">{entry.label}</span>
                      <span className="data-agent-welcome-entry-description">
                        {entry.description}
                      </span>
                    </span>
                    <ChevronRight
                      size={16}
                      strokeWidth={1.6}
                      className="data-agent-welcome-entry-arrow"
                    />
                  </button>
                );
              })}
            </div>
          </section>
        </div>

        <div className="data-agent-mascot" aria-hidden="true">
          <span className="data-agent-mascot-eye data-agent-mascot-eye-left" />
          <span className="data-agent-mascot-eye data-agent-mascot-eye-right" />
          <span className="data-agent-mascot-shine" />
        </div>
      </main>
    </div>
  );
}

export default DataAgentWelcome;

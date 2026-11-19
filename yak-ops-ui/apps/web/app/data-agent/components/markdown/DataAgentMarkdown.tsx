import { memo } from "react";
import { cjk } from "@streamdown/cjk";
import { code } from "@streamdown/code";
import { Streamdown } from "streamdown";

import "./data-agent-markdown.css";

const plugins = { code, cjk };

const DISALLOWED_ELEMENTS = [
  "iframe",
  "script",
  "style",
  "object",
  "embed",
  "form",
  "input",
  "button",
  "video",
  "audio",
  "source",
];

type DataAgentMarkdownProps = {
  content: string;
  isStreaming: boolean;
};

function DataAgentMarkdownComponent({
  content,
  isStreaming,
}: DataAgentMarkdownProps) {
  return (
    <Streamdown
      className="data-agent-markdown"
      plugins={plugins}
      shikiTheme={["github-light", "github-dark"]}
      mode="streaming"
      isAnimating={isStreaming}
      skipHtml
      disallowedElements={DISALLOWED_ELEMENTS}
      lineNumbers={false}
    >
      {content}
    </Streamdown>
  );
}

export const DataAgentMarkdown = memo(DataAgentMarkdownComponent);
DataAgentMarkdown.displayName = "DataAgentMarkdown";

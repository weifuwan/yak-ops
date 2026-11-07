import type { ReactNode } from "react";

const KEY_CLASS = "text-[#0550ae]";
const STRING_CLASS = "text-[#0a3069]";
const NUMBER_CLASS = "text-[#953800]";
const LITERAL_CLASS = "text-[#8250df]";
const COMMENT_CLASS = "text-[#6e7781]";
const PUNCTUATION_CLASS = "text-[#57606a]";

function renderScalar(value: string): ReactNode {
  const trimmed = value.trim();

  if (!trimmed) return null;
  if (/^(true|false|null|~)$/i.test(trimmed)) {
    return <span className={LITERAL_CLASS}>{value}</span>;
  }
  if (/^-?\d+(?:\.\d+)?$/.test(trimmed)) {
    return <span className={NUMBER_CLASS}>{value}</span>;
  }
  if (
    (trimmed.startsWith('"') && trimmed.endsWith('"')) ||
    (trimmed.startsWith("'") && trimmed.endsWith("'"))
  ) {
    return <span className={STRING_CLASS}>{value}</span>;
  }
  return <span className={STRING_CLASS}>{value}</span>;
}

function renderLine(line: string, index: number) {
  const indent = line.match(/^\s*/)?.[0] || "";
  const content = line.slice(indent.length);

  if (!content) return <span key={index}>{"\n"}</span>;
  if (content.startsWith("#")) {
    return (
      <span key={index}>
        {indent}<span className={COMMENT_CLASS}>{content}</span>{"\n"}
      </span>
    );
  }

  const listPrefix = content.startsWith("- ") ? "- " : "";
  const body = listPrefix ? content.slice(2) : content;
  const colon = body.indexOf(":");

  if (colon < 0) {
    return (
      <span key={index}>
        {indent}
        {listPrefix ? <span className={PUNCTUATION_CLASS}>{listPrefix}</span> : null}
        {renderScalar(body)}
        {"\n"}
      </span>
    );
  }

  const key = body.slice(0, colon);
  const value = body.slice(colon + 1);

  return (
    <span key={index}>
      {indent}
      {listPrefix ? <span className={PUNCTUATION_CLASS}>{listPrefix}</span> : null}
      <span className={KEY_CLASS}>{key}</span>
      <span className={PUNCTUATION_CLASS}>:</span>
      {renderScalar(value)}
      {"\n"}
    </span>
  );
}

export function YamlPreview({ value }: { value: string }) {
  return (
    <pre className="max-h-[600px] overflow-auto rounded border border-[var(--yak-components-panel-border)] bg-[var(--yak-components-panel-bg)] p-4 font-mono text-xs leading-5 text-[var(--yak-components-panel-text)]">
      <code>{value.split("\n").map(renderLine)}</code>
    </pre>
  );
}

export default YamlPreview;

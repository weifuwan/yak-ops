import type { ReactNode } from "react";

type Block =
  | { type: "table"; header: string[]; rows: string[][] }
  | { type: "list"; ordered: boolean; items: string[] }
  | { type: "code"; content: string }
  | { type: "heading"; level: number; content: string }
  | { type: "paragraph"; content: string };

const TABLE_SEPARATOR = /^\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?$/;

function splitRow(line: string): string[] {
  const inner = line.trim().replace(/^\|/, "").replace(/\|$/, "");
  return inner.split("|").map((cell) => cell.trim());
}

function looksLikeRow(line: string): boolean {
  const trimmed = line.trim();
  return trimmed.includes("|") && splitRow(trimmed).length >= 2;
}

function parseBlocks(content: string): Block[] {
  const lines = content.split("\n");
  const blocks: Block[] = [];
  let index = 0;

  while (index < lines.length) {
    const line = lines[index];
    const trimmed = line.trim();

    if (!trimmed) {
      index += 1;
      continue;
    }

    if (trimmed.startsWith("```")) {
      const buffer: string[] = [];
      index += 1;
      while (index < lines.length && !lines[index].trim().startsWith("```")) {
        buffer.push(lines[index]);
        index += 1;
      }
      index += 1;
      blocks.push({ type: "code", content: buffer.join("\n") });
      continue;
    }

    // A table needs a header row followed by a separator row.
    if (looksLikeRow(trimmed) && index + 1 < lines.length && TABLE_SEPARATOR.test(lines[index + 1].trim())) {
      const header = splitRow(trimmed);
      const rows: string[][] = [];
      index += 2;
      while (index < lines.length && looksLikeRow(lines[index])) {
        rows.push(splitRow(lines[index]));
        index += 1;
      }
      blocks.push({ type: "table", header, rows });
      continue;
    }

    const heading = /^(#{1,4})\s+(.*)$/.exec(trimmed);
    if (heading) {
      blocks.push({ type: "heading", level: heading[1].length, content: heading[2] });
      index += 1;
      continue;
    }

    if (/^[-*]\s+/.test(trimmed) || /^\d+[.)]\s+/.test(trimmed)) {
      const ordered = /^\d+[.)]\s+/.test(trimmed);
      const items: string[] = [];
      while (index < lines.length) {
        const current = lines[index].trim();
        if (!current) {
          break;
        }
        if (ordered && /^\d+[.)]\s+/.test(current)) {
          items.push(current.replace(/^\d+[.)]\s+/, ""));
          index += 1;
        } else if (!ordered && /^[-*]\s+/.test(current)) {
          items.push(current.replace(/^[-*]\s+/, ""));
          index += 1;
        } else if (items.length > 0 && !looksLikeRow(current) && !TABLE_SEPARATOR.test(current)) {
          // Lazy continuation line of the previous list item.
          items[items.length - 1] = `${items[items.length - 1]} ${current}`;
          index += 1;
        } else {
          break;
        }
      }
      blocks.push({ type: "list", ordered, items });
      continue;
    }

    const paragraph: string[] = [];
    while (index < lines.length) {
      const current = lines[index].trim();
      if (!current || looksLikeRow(current) || /^[-*]\s+/.test(current) || /^\d+[.)]\s+/.test(current)
          || /^#{1,4}\s+/.test(current) || current.startsWith("```")) {
        break;
      }
      paragraph.push(lines[index]);
      index += 1;
    }
    blocks.push({ type: "paragraph", content: paragraph.join("\n") });
  }

  return blocks;
}

function renderInline(text: string, keyPrefix: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  const pattern = /(\*\*[^*]+\*\*|`[^`]+`)/g;
  let cursor = 0;
  let match: RegExpExecArray | null;
  let part = 0;

  while ((match = pattern.exec(text)) !== null) {
    if (match.index > cursor) {
      nodes.push(text.slice(cursor, match.index));
    }
    const token = match[0];
    if (token.startsWith("**")) {
      nodes.push(
        <strong key={`${keyPrefix}-b${part}`} className="font-semibold text-[#161823]">
          {token.slice(2, -2)}
        </strong>,
      );
    } else {
      nodes.push(
        <code key={`${keyPrefix}-c${part}`} className="rounded bg-[#eceff3] px-1 py-0.5 font-mono text-[11.5px] text-[#334155]">
          {token.slice(1, -1)}
        </code>,
      );
    }
    cursor = match.index + token.length;
    part += 1;
  }
  if (cursor < text.length) {
    nodes.push(text.slice(cursor));
  }
  return nodes;
}

export function MarkdownMessage({ content }: { content: string }) {
  const blocks = parseBlocks(content);
  return (
    <div className="space-y-2 text-[13px] leading-relaxed text-[#1f2329]">
      {blocks.map((block, blockIndex) => {
        if (block.type === "table") {
          return (
            <div key={`t${blockIndex}`} className="max-w-full overflow-x-auto rounded border border-[#e6eaef]">
              <table className="w-max min-w-full border-collapse text-[11.5px]">
                <thead>
                  <tr className="bg-[#eef2f6]">
                    {block.header.map((cell, cellIndex) => (
                      <th
                        key={`h${cellIndex}`}
                        className="whitespace-nowrap border border-[#dfe4ea] px-1.5 py-1 text-left font-semibold text-[#3d4652]"
                      >
                        {renderInline(cell, `h${cellIndex}`)}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {block.rows.map((row, rowIndex) => (
                    <tr key={`r${rowIndex}`} className={rowIndex % 2 === 0 ? "bg-white" : "bg-[#fafbfc]"}>
                      {block.header.map((_, cellIndex) => (
                        <td
                          key={`d${cellIndex}`}
                          className="whitespace-nowrap border border-[#e6eaef] px-1.5 py-1 align-top text-[#1f2329]"
                        >
                          {renderInline(row[cellIndex] ?? "", `r${rowIndex}c${cellIndex}`)}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          );
        }

        if (block.type === "code") {
          return (
            <pre key={`c${blockIndex}`} className="overflow-x-auto rounded-lg bg-[#1f2329] px-3 py-2 text-[11.5px] leading-relaxed text-[#e6eaef]">
              <code>{block.content}</code>
            </pre>
          );
        }

        if (block.type === "heading") {
          return (
            <p key={`h${blockIndex}`} className="pt-1 text-[13px] font-semibold text-[#161823]">
              {renderInline(block.content, `hh${blockIndex}`)}
            </p>
          );
        }

        if (block.type === "list") {
          const ListTag = block.ordered ? "ol" : "ul";
          return (
            <ListTag
              key={`l${blockIndex}`}
              className={
                block.ordered
                  ? "list-decimal space-y-1 pl-5 marker:text-[#8a8f99]"
                  : "list-disc space-y-1 pl-5 marker:text-[#8a8f99]"
              }
            >
              {block.items.map((item, itemIndex) => (
                <li key={`i${itemIndex}`}>{renderInline(item, `l${blockIndex}i${itemIndex}`)}</li>
              ))}
            </ListTag>
          );
        }

        return (
          <p key={`p${blockIndex}`} className="whitespace-pre-wrap">
            {renderInline(block.content, `p${blockIndex}`)}
          </p>
        );
      })}
    </div>
  );
}

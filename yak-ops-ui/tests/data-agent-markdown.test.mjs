import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
const workspace = read("../apps/web/app/data-agent/components/DataAgentWorkspace.tsx");
const assistantMessage = read("../apps/web/app/data-agent/components/workspace/conversation/AssistantMessage.tsx");
const conversationHook = read("../apps/web/hooks/useDataAgentConversation.ts");
const reducer = read("../apps/web/app/data-agent/model/conversation-reducer.ts");
const markdown = read("../apps/web/app/data-agent/components/markdown/DataAgentMarkdown.tsx");
const markdownStyles = read("../apps/web/app/data-agent/components/markdown/data-agent-markdown.css");
const globalStyles = read("../apps/web/app/styles/global.css");
const webPackage = JSON.parse(read("../apps/web/package.json"));
const lock = JSON.parse(read("../package-lock.json"));

test("assistant responses render through Streamdown without changing the SSE message flow", () => {
  assert.match(assistantMessage, /import \{ DataAgentMarkdown \} from "\.\.\/\.\.\/markdown\/DataAgentMarkdown";/);
  assert.match(
    assistantMessage,
    /<DataAgentMarkdown\s+content=\{message\.content\}\s+isStreaming=\{message\.status === "streaming"\}\s*\/>/,
  );
  assert.doesNotMatch(workspace, /DataAgentMarkdown|text_delta|sendAgentMessage/);
  assert.match(conversationHook, /sendAgentMessage/);
  assert.match(reducer, /content: message\.content \+ action\.delta/);
  assert.match(
    read("../apps/web/app/data-agent/components/workspace/conversation/ConfirmationCard.tsx"),
    /确认执行/,
  );
});

test("the renderer enables streaming repair, Shiki highlighting and Chinese punctuation handling", () => {
  assert.match(markdown, /from "streamdown"/);
  assert.match(markdown, /from "@streamdown\/code"/);
  assert.match(markdown, /from "@streamdown\/cjk"/);
  assert.match(markdown, /plugins=\{plugins\}/);
  assert.match(markdown, /shikiTheme=\{\["github-light", "github-dark"\]\}/);
  assert.match(markdown, /mode="streaming"/);
  assert.match(markdown, /isAnimating=\{isStreaming\}/);
  assert.match(markdown, /lineNumbers=\{false\}/);
  assert.match(markdown, /controls=\{\{ table: false \}\}/);
  assert.match(markdown, /skipHtml/);
  assert.match(markdown, /disallowedElements=\{DISALLOWED_ELEMENTS\}/);
});

test("Markdown styles restore list markers and follow Yak-OPS visual tokens", () => {
  assert.match(markdownStyles, /--primary:\s*#fe2c55/);
  assert.match(markdownStyles, /data-streamdown="unordered-list"/);
  assert.match(markdownStyles, /list-style-type:\s*disc !important/);
  assert.match(markdownStyles, /data-streamdown="ordered-list"/);
  assert.match(markdownStyles, /data-streamdown="code-block"/);
  assert.match(markdownStyles, /data-streamdown="table-wrapper"/);
  assert.match(markdownStyles, /border:\s*0/);
  assert.match(globalStyles, /@theme inline/);
  assert.match(globalStyles, /node_modules\/streamdown\/dist/);
  assert.match(globalStyles, /node_modules\/@streamdown\/code\/dist/);
  assert.match(globalStyles, /node_modules\/@streamdown\/cjk\/dist/);
});

test("Streamdown runtime packages are pinned in both the app manifest and npm lockfile", () => {
  const versions = {
    streamdown: "2.7.1",
    "@streamdown/code": "2.0.0",
    "@streamdown/cjk": "1.0.4",
  };
  for (const [name, version] of Object.entries(versions)) {
    assert.equal(webPackage.dependencies[name], version);
    assert.equal(lock.packages["apps/web"].dependencies[name], version);
    assert.equal(lock.packages["node_modules/" + name].version, version);
  }
});

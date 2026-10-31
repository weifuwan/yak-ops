import assert from "node:assert/strict";
import { test } from "node:test";
import { checkDataSyncStyleTokens } from "./data-sync-style-architecture.mjs";

const check = (path, source) => checkDataSyncStyleTokens(path, source);
const detail = "apps/web/app/data-sync/detail/task-summary.tsx";
const editor = "apps/web/app/data-sync/editor/properties-drawer.tsx";
const list = "apps/web/app/data-sync/list/task-table.tsx";

test("migrated Data Sync components reject fixed Tailwind colors", () => {
  const tailwind = 'const Panel = () => <div className="text-[#344054] bg-[#fff]" />;';
  const inline = 'const Panel = () => <div style={{ backgroundColor: "#fff" }} />;';
  const template = 'const item = `text-[#d92d20] ${selected ? "font-bold" : ""}`;';

  assert.match(check(detail, tailwind).join("\n"), /embeds a fixed color/);
  assert.match(check(editor, inline).join("\n"), /embeds a fixed color/);
  assert.match(check(list, template).join("\n"), /embeds a fixed color/);
});

test("semantic Tailwind colors, arbitrary geometry and comments are valid", () => {
  const source = [
    "// old text-[#ff00ff] should not count",
    'const Panel = () => <div className="text-[var(--yak-components-muted-text)] w-[300px]" />;',
  ].join("\n");
  assert.deepEqual(check(detail, source), []);
});

test("domain-specific log syntax colors remain in their CSS owner", () => {
  const log = ".yak-log-level { color: #79adff }";
  const other = 'const Panel = () => <div className="text-[#fff]" />;';
  assert.deepEqual(check("apps/web/app/data-sync/task-instance-log.css", log), []);
  assert.deepEqual(check("apps/web/app/layout/index.tsx", other), []);
});

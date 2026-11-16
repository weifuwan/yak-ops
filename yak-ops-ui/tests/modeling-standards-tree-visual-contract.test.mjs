import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const root = dirname(fileURLToPath(import.meta.url));
const read = (path) => readFileSync(join(root, path), "utf8");

const indicatorTree = read("../apps/web/app/data-modeling/indicators/workbench/components/sidebar/indicator-tree.tsx");
const standardTree = read("../apps/web/app/data-modeling/standards/components/standard-tree.tsx");
const standardIcons = read("../apps/web/app/data-modeling/standards/components/icons/index.tsx");
const dictionaryList = read("../apps/web/app/data-modeling/standards/naming-dictionary/dictionary-list.tsx");
const standardsPage = read("../apps/web/app/data-modeling/standards/components/directory-standards-page.tsx");
const dictionaryPage = read("../apps/web/app/data-modeling/standards/naming-dictionary/dictionary-standards-content.tsx");

test("standard directory tree follows the indicator tree row visual contract", () => {
  const rowClass = /group flex min-h-8 w-full items-center gap-1 rounded-none px-0 text-left text-xs leading-6 transition-colors/;
  const rowDivider = /boxShadow: "0 0 0 0\.5px #eceff780"/;

  assert.match(indicatorTree, rowClass);
  assert.match(standardTree, rowClass);
  assert.match(indicatorTree, rowDivider);
  assert.match(standardTree, rowDivider);
  assert.ok(standardTree.includes("paddingLeft: depth * 24"));
  assert.ok(standardTree.includes('width="8"'));
  assert.ok(standardTree.includes('height="8"'));
  assert.ok(standardTree.includes("duration-150"));
  assert.ok(standardTree.includes('bg-[#DFE7FF] text-[var(--yak-components-panel-text)]'));
});

test("standard record nodes use module-specific public SVG icons", () => {
  assert.ok(standardTree.includes('import { StandardIcon } from "./icons";'));
  assert.ok(standardTree.includes('name={module === "FIELD" ? "field-standard" : module === "CODE" ? "standard-code" : "unit-standard"}'));
  assert.ok(standardTree.includes("size={14}"));
  assert.ok(!standardTree.includes("FileText"));
  for (const icon of ["field-standard", "standard-code", "unit-standard"]) {
    assert.ok(standardIcons.includes(`"${icon}"`), `standard icon component should support ${icon}`);
  }
  assert.ok(standardIcons.includes('icons/data-modeling/standards/${name}.svg'));
});

test("standard tree keeps its directory actions and uses skeleton loading", () => {
  for (const handler of [
    "onToggle(node.key)",
    "onSelect(node)",
    "onCreateDirectory(node)",
    "onCreateStandardSet(node)",
    "onCreateRecord(node)",
    "onEditDirectory(node)",
    "onEditRecord(node)",
    "onDeleteDirectory(node)",
    "onDeleteRecord(node)",
  ]) {
    assert.ok(standardTree.includes(handler), `standard tree should preserve ${handler}`);
  }

  assert.ok(standardTree.includes("<Skeleton"));
  assert.ok(standardTree.includes("onContextMenu"));
});

test("naming dictionary list follows the indicator tree row visual contract", () => {
  assert.match(dictionaryList, /min-h-8 w-full cursor-pointer items-center gap-1 rounded-none px-0 text-left text-xs leading-6/);
  assert.match(dictionaryList, /boxShadow: "0 0 0 0\.5px #eceff780"/);
  assert.ok(dictionaryList.includes('bg-[#DFE7FF] text-[var(--yak-components-panel-text)]'));
  assert.ok(dictionaryList.includes("<DictionaryListSkeleton />"));
  assert.ok(dictionaryList.includes("onSelect(item.id)"));
  assert.ok(dictionaryList.includes("onPageChange"));
});

test("standards sidebars use the indicator sidebar width and resize bounds", () => {
  for (const source of [standardsPage, dictionaryPage]) {
    assert.ok(source.includes("useState(292)"));
    assert.ok(source.includes("Math.min(360, Math.max(260, event.clientX - rect.left))"));
    assert.ok(source.includes("w-px shrink-0 cursor-col-resize"));
  }
});

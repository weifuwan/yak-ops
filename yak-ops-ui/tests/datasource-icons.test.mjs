import assert from "node:assert/strict";
import { test } from "node:test";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

import { COMMON_DB_OPTIONS } from "../apps/web/app/datasource/constants.ts";
import { DATASOURCE_ICON_ASSETS } from "../apps/web/app/datasource/icons/icon-assets.ts";

const root = join(dirname(fileURLToPath(import.meta.url)), "../apps/web/public");
const existingReactIcons = new Set(["MYSQL", "ORACLE", "POSTGRE_SQL"]);

test("every bundled datasource type has a local SVG or the existing React SVG", () => {
  for (const { value } of COMMON_DB_OPTIONS) {
    assert.ok(existingReactIcons.has(value) || DATASOURCE_ICON_ASSETS[value], "Missing icon for " + value);
  }
});

test("standalone SVG assets contain actual vector markup, not iconfont <use> references", () => {
  assert.equal(Object.keys(DATASOURCE_ICON_ASSETS).length, 8);
  for (const [type, src] of Object.entries(DATASOURCE_ICON_ASSETS)) {
    assert.match(src, /^\/datasource-icons\/[\w-]+\.svg$/);
    const svg = readFileSync(join(root, src.slice(1)), "utf8");
    assert.match(svg, /<svg\b/);
    assert.match(svg, /<\/svg>/);
    assert.doesNotMatch(svg, /<use\b|<script\b|javascript:/i, "Invalid SVG for " + type);
  }
});

import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const iconsRoot = join(dirname(fileURLToPath(import.meta.url)), "../apps/web/app/datasource/icons");
const components = [
  "DamengIcon",
  "StarRocksIcon",
  "ClickHouseIcon",
  "Db2Icon",
  "OceanBaseIcon",
  "SqlServerIcon",
  "DorisIcon",
  "SshIcon"
];
const router = readFileSync(join(iconsRoot, "DatabaseIcons.tsx"), "utf8");

test("eight supplied SVGs are wrapped by bounded, reusable React icon components", () => {
  for (const name of components) {
    const source = readFileSync(join(iconsRoot, name + ".tsx"), "utf8");
    assert.match(source, /height = "24"/);
    assert.match(source, /width = "24"/);
    assert.match(source, /width=\{width\}/);
    assert.match(source, /height=\{height\}/);
    assert.match(source, /preserveAspectRatio="xMidYMid meet"/);
    assert.match(source, /viewBox="0 0 \d+ \d+"/);
    assert.doesNotMatch(source, /width="200"|height="200"|<use\b/);
    assert.ok(source.includes("export default " + name), name + " must be exported");
    assert.ok(router.includes('import ' + name + ' from "./' + name + '";'), name + " must be imported");
    assert.ok(router.includes("<" + name + " width={width} height={height} />"), name + " must use parent sizing");
  }
});

test("existing MySQL / Oracle / PostgreSQL icons and generic fallback remain intact", () => {
  for (const name of ["MysqlIcon", "OracleIcon", "PsSqlIcon"]) {
    assert.ok(router.includes("<" + name + " width={width} height={height} />"));
  }
  assert.ok(router.includes("<Database style={fallbackStyle}"));
});

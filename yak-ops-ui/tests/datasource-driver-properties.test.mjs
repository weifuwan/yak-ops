import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const root = join(dirname(fileURLToPath(import.meta.url)), "../apps/web");
const service = readFileSync(join(root, "service/datasource/index.ts"), "utf8");
const hook = readFileSync(join(root, "app/datasource/form/use-data-source-form.ts"), "utf8");
const field = readFileSync(join(root, "app/datasource/form/advanced-properties.tsx"), "utf8");

test("MySQL and PostgreSQL request installed JDBC driver metadata only", () => {
  assert.match(service, /type === "MYSQL" \|\| type === "POSTGRESQL"/);
  assert.match(service, /API \+ "\/connection-properties\?" \+ qs\(\{ type \}\)/);
  assert.doesNotMatch(service, /MYSQL: \["serverTimezone"/);
  assert.doesNotMatch(service, /POSTGRESQL: \["sslmode"/);
  assert.match(service, /PROPERTY_HINTS\[type\] \|\| \[\]/);
});

test("parameter editors still support custom values", () => {
  assert.match(hook, /setDriverPropertyMetadata\(result\?\.properties \|\| \[\]\)/);
  assert.match(hook, /const addProperty = \(\) =>/);
  assert.match(field, /driverProperty\?\.choices\?\.length/);
  assert.match(field, /patchProperty\(index, "value", event\.target\.value\)/);
});

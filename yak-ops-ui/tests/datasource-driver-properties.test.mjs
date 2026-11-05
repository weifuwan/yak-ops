import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const root = join(dirname(fileURLToPath(import.meta.url)), "../apps/web");
const service = readFileSync(join(root, "service/datasource/index.ts"), "utf8");
const hook = readFileSync(join(root, "app/datasource/form/use-data-source-form.ts"), "utf8");
const field = readFileSync(join(root, "app/datasource/form/advanced-properties.tsx"), "utf8");

test("all datasource types read optional connection properties from the backend", () => {
  assert.match(service, /getDataSourceConnectionProperties = async/);
  assert.match(service, /HttpUtils\.getData<DataSourceDriverProperty\[\]>/);
  assert.match(service, /API \+ "\/connection-properties\?" \+ qs\(\{ type: toBackendType\(dbType\) \}\)/);
  assert.doesNotMatch(service, /PROPERTY_HINTS/);
});

test("empty property array hides suggestions without disabling custom input", () => {
  assert.match(hook, /setPropertyKeyOptions\(properties\.map\(\(property\) => property\.name\)\)/);
  assert.match(hook, /setDriverPropertyMetadata\(properties\)/);
  assert.match(hook, /setPropertyKeysError\(true\)/);
  assert.match(field, /propertyKeysLoading \|\| propertyKeyOptions\.length > 0/);
  assert.match(field, /const driverPropertyMap = new Map/);
  assert.match(field, /propertyKeysError/);
  assert.match(field, /patchProperty\(index, "value", event\.target\.value\)/);
  assert.match(hook, /const addProperty = \(\) =>/);
});

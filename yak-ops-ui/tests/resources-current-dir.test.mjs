import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

import {
  resolveCurrentResourceDirectory,
} from "../apps/web/app/resources/utils.ts";

const root = dirname(fileURLToPath(import.meta.url));
const hook = readFileSync(
  join(root, "../apps/web/app/resources/hooks/use-resource-management.ts"),
  "utf8",
);
const baseDir = "D:\\tmp\\yak-ops\\default\\resources";

test("initializes an empty current directory from the backend base directory", () => {
  assert.equal(resolveCurrentResourceDirectory("", baseDir), baseDir);
});

test("replaces a stale Linux-style directory with the Windows base directory", () => {
  assert.equal(
    resolveCurrentResourceDirectory("/tmp/yak-ops/default/resources", baseDir),
    baseDir,
  );
});

test("preserves the current child directory when it is inside the latest base directory", () => {
  const currentDir = "D:\\tmp\\yak-ops\\default\\resources\\ggg";

  assert.equal(resolveCurrentResourceDirectory(currentDir, baseDir), currentDir);
});

test("treats mixed slash styles as the same resource path", () => {
  const mixedBaseDir = "D:/tmp/yak-ops/default/resources/";
  const currentDir = "D:\\tmp\\yak-ops\\default\\resources\\ggg";

  assert.equal(
    resolveCurrentResourceDirectory(currentDir, mixedBaseDir),
    currentDir,
  );
});

test("compares Windows resource paths without case sensitivity", () => {
  const upperCaseBaseDir = "D:\\TMP\\YAK-OPS\\DEFAULT\\RESOURCES";
  const currentDir = "d:/tmp/yak-ops/default/resources/Ggg";

  assert.equal(
    resolveCurrentResourceDirectory(currentDir, upperCaseBaseDir),
    currentDir,
  );
});

test("does not mistake a sibling directory with the same prefix for a child", () => {
  const siblingDir = "D:\\tmp\\yak-ops\\default\\resources-old";

  assert.equal(resolveCurrentResourceDirectory(siblingDir, baseDir), baseDir);
});

test("resets to the new base directory when the storage root changes", () => {
  const previousRootChild = "D:\\tmp\\yak-ops\\old\\resources\\subfolder";
  const nextBaseDir = "D:\\tmp\\yak-ops\\default\\resources";

  assert.equal(
    resolveCurrentResourceDirectory(previousRootChild, nextBaseDir),
    nextBaseDir,
  );
});

test("the resource hook uses the tested resolver whenever the backend base directory changes", () => {
  assert.match(
    hook,
    /setCurrentDir\(\(current\) =>\s*resolveCurrentResourceDirectory\(current, baseDir\),?\s*\)/,
  );
  assert.match(hook, /\}, \[baseDirQuery\.data\]\);/);
});

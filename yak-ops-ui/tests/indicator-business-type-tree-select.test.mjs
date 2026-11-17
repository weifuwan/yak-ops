import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";
import { buildIndicatorScopeOptions } from "../apps/web/app/data-modeling/indicators/workbench/utils/indicator-scope-options.ts";

const root = dirname(fileURLToPath(import.meta.url));
const read = (path) => readFileSync(join(root, path), "utf8");

const filter = read("../apps/web/app/data-modeling/indicators/workbench/components/sidebar/indicator-tree-filter.tsx");
const atomPage = read("../apps/web/app/data-modeling/indicators/atom-indicator/index.tsx");
const derivativePage = read("../apps/web/app/data-modeling/indicators/derivative-indicator/index.tsx");
const compositePage = read("../apps/web/app/data-modeling/indicators/composite-indicator/index.tsx");
const modifierPage = read("../apps/web/app/data-modeling/indicators/modifier/index.tsx");

test("business category filter uses the hierarchical business type API and Yak UI TreeSelect", () => {
  assert.match(filter, /businessTypeApi\.tree\(projectCode\)/);
  assert.match(filter, /<TreeSelect/);
  assert.match(filter, /items=\{businessTypeTreeOptions\}/);
  assert.match(filter, /searchPlaceholder="搜索业务分类"/);
  assert.match(filter, /children: children\.length \? children : undefined/);
});

test("business category filter and tree nodes use the business type SVG", () => {
  assert.match(filter, /PlanningIcon name="business-type"/);
  assert.match(filter, /icon: <PlanningIcon name="business-type"/);
});

test("only atom, derivative and composite indicator pages opt into the tree selector", () => {
  for (const [name, page] of [
    ["atom", atomPage],
    ["derivative", derivativePage],
    ["composite", compositePage],
  ]) {
    assert.match(page, /businessTypeTreeSelect/);
  }
  assert.doesNotMatch(modifierPage, /businessTypeTreeSelect/);
});

test("other scope types retain the existing flat select behavior", () => {
  assert.match(filter, /businessTypeTreeSelect\s*\?\s*businessTypeApi\.tree\(projectCode\)/);
  assert.match(filter, /dataDomainApi\.all<ModelingRecord>\(projectCode\)/);
  assert.match(filter, /dataMartApi\.all<ModelingRecord>\(projectCode\)/);
  assert.match(filter, /subjectDomainApi\.all<ModelingRecord>\(projectCode\)/);
});

test("flat scope selects render the matching Chinese label instead of the stored ID", () => {
  const id = "61e73dc9b25c470b8f66320b";

  assert.deepEqual(
    buildIndicatorScopeOptions([{ id, cnName: "客户数据域" }], "DATA_DOMAIN"),
    [{ value: id, label: "客户数据域" }],
  );
  assert.deepEqual(
    buildIndicatorScopeOptions([{ id, martName: "交易集市" }], "DATA_MART"),
    [{ value: id, label: "交易集市" }],
  );
  assert.deepEqual(
    buildIndicatorScopeOptions([{ id, domainName: "用户主题域" }], "SUBJECT_DOMAIN"),
    [{ value: id, label: "用户主题域" }],
  );
  assert.deepEqual(
    buildIndicatorScopeOptions([{ id, businessName: "交易业务" }], "BUSINESS_TYPE"),
    [{ value: id, label: "交易业务" }],
  );
});

test("scope selectors explicitly resolve labels and never expose an unmatched ID", () => {
  assert.match(filter, /<SelectValue placeholder=\{scopeSelectPlaceholder\}>/);
  assert.match(filter, /scopeOptions\.find\(\(option\) => option\.value === String\(value \?\? ""\)\)\?\.label \|\| scopeSelectPlaceholder/);
  assert.match(filter, /value=\{selectedScopeValue \|\| undefined\}/);
  assert.match(filter, /value=\{selectedScopeValue \|\| null\}/);
  assert.match(filter, /if \(!scopeValue \|\| !scopeOptionsQuery\.isSuccess\) return;/);
});

test("scope labels use the correct name field for each directory type and avoid ID fallback", () => {
  const id = "opaque-id";
  assert.deepEqual(
    buildIndicatorScopeOptions([{ id, cnName: "客户域", domainName: "customer" }], "DATA_DOMAIN"),
    [{ value: id, label: "客户域" }],
  );
  assert.deepEqual(
    buildIndicatorScopeOptions([{ id, domainName: "用户主题域" }], "SUBJECT_DOMAIN"),
    [{ value: id, label: "用户主题域" }],
  );
  assert.deepEqual(
    buildIndicatorScopeOptions([{ id }], "DATA_DOMAIN"),
    [{ value: id, label: "未命名" }],
  );
});

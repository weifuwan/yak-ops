import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const root = dirname(fileURLToPath(import.meta.url));
const read = (path) => readFileSync(join(root, path), "utf8");

const indicatorTree = read("../apps/web/app/data-modeling/indicators/workbench/components/sidebar/indicator-tree.tsx");
const planningTreeVisuals = read("../apps/web/app/data-modeling/planning/components/tree-visuals.tsx");
const businessTypeTree = read("../apps/web/app/data-modeling/planning/business-type/business-type-tree.tsx");
const dataMartTree = read("../apps/web/app/data-modeling/planning/application-layer/data-mart/data-mart-tree.tsx");
const subjectDomainTree = read("../apps/web/app/data-modeling/planning/application-layer/subject-domain/subject-domain-tree.tsx");

const planningTrees = [
  ["business type", businessTypeTree],
  ["data mart", dataMartTree],
  ["subject domain", subjectDomainTree],
];

test("planning tree rows follow the indicator tree visual contract", () => {
  const rowClass = /group flex min-h-8 w-full items-center gap-1 rounded-none px-0 text-left text-xs leading-6 transition-colors/;
  const rowDivider = /boxShadow: "0 0 0 0\.5px #eceff780"/;

  assert.match(indicatorTree, rowClass);
  assert.match(planningTreeVisuals, rowClass);
  assert.match(indicatorTree, rowDivider);
  assert.match(planningTreeVisuals, rowDivider);
  assert.ok(planningTreeVisuals.includes("paddingLeft: depth * 24"));
  assert.ok(planningTreeVisuals.includes('width="8"'));
  assert.ok(planningTreeVisuals.includes('height="8"'));
  assert.ok(planningTreeVisuals.includes("duration-150"));
  assert.ok(planningTreeVisuals.includes('bg-[#DFE7FF] text-[var(--yak-components-panel-text)]'));
});

test("all planning tree pages use the shared row and skeleton components", () => {
  for (const [name, source] of planningTrees) {
    assert.ok(source.includes("PlanningTreeRow"), `${name} tree should use PlanningTreeRow`);
    assert.ok(source.includes("PlanningTreeSkeleton"), `${name} tree should use PlanningTreeSkeleton`);
    assert.ok(source.includes('px-3 pb-2 pt-3'), `${name} tree header should align with indicator sidebar`);
    assert.ok(source.includes('min-h-0 flex-1 overflow-y-auto pb-3'), `${name} tree scroll area should align with indicator sidebar`);
    assert.ok(!source.includes("ChevronDown"), `${name} tree should use the shared animated arrow`);
    assert.ok(!source.includes("PlusSquare"), `${name} tree should use the shared Yak UI action buttons`);
  }
  assert.ok(planningTreeVisuals.includes("<Skeleton"));
});

test("planning tree node icons preserve their business-specific meaning", () => {
  assert.ok(businessTypeTree.includes('icon="business-type"'));
  assert.ok(dataMartTree.includes('icon="data-mart"'));
  assert.ok(subjectDomainTree.includes('icon={isMart ? "data-mart" : "subject-domain"}'));
  assert.ok(subjectDomainTree.includes(">集市</span>"));
});

test("planning tree selection and context-menu actions remain intact", () => {
  assert.ok(businessTypeTree.includes("onSelect(node)"));
  assert.ok(businessTypeTree.includes('runContextAction("add")'));
  assert.ok(dataMartTree.includes("onSelect(node)"));
  assert.ok(dataMartTree.includes('runContextAction("delete")'));
  assert.ok(subjectDomainTree.includes("onSelect(node)"));
  assert.ok(subjectDomainTree.includes('if (item.type === "SUBJECT")'));
  assert.ok(subjectDomainTree.includes('runContextAction("edit")'));
});

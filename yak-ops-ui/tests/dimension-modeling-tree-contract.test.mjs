import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";
import {
  collectDimensionExpandedKeys,
  filterDimensionTree,
} from "../apps/web/app/data-modeling/models/dimension-modeling/workbench/utils/dimension-tree.ts";

const root = dirname(fileURLToPath(import.meta.url));
const read = (path) => readFileSync(join(root, path), "utf8");

const api = read("../apps/web/service/data-modeling/models/dimension-modeling.ts");
const hook = read("../apps/web/app/data-modeling/models/dimension-modeling/workbench/hooks/use-dimension-tree.ts");
const sidebar = read("../apps/web/app/data-modeling/models/dimension-modeling/workbench/components/dimension-sidebar.tsx");
const controller = read("../../yak-ops-api/src/main/java/io/yak/ops/api/controller/DimensionModelingController.java");
const groupingEnum = read("../../yak-ops-modeling/src/main/java/io/yak/ops/modeling/domain/enums/DimensionTreeGroupingEnum.java");
const treeBuilder = read("../../yak-ops-modeling/src/main/java/io/yak/ops/modeling/application/service/DimensionTreeBuilder.java");

test("dimension tree API is connected to the project-scoped backend tree endpoint", () => {
  assert.match(api, /resourcePath\(projectCode\) \+ "\/tree"/);
  assert.match(api, /HttpUtils\.postData<DimensionTreeNode\[\]>/);
  assert.match(controller, /@RequestMapping\("projects\/\{projectCode\}\/modeling\/dimensions"\)/);
  assert.match(controller, /@PostMapping\("\/tree"\)/);
  assert.match(controller, /dimensionModelingService\.tree\(dto\)/);
});

test("tree query sends warehouse, grouping, scope and keyword filters to the backend", () => {
  assert.match(hook, /dwhType: dwType/);
  assert.match(hook, /selectOption: groupBy/);
  assert.match(hook, /params\[scopeParameter\(groupBy\)\] = scopeValue/);
  assert.match(hook, /keyword: keyword\.trim\(\) \|\| undefined/);
  assert.match(hook, /placeholderData: \(previousData\) => previousData/);
  for (const field of ["busTypeId", "domainId", "martId", "subjectId"]) {
    assert.ok(hook.includes('return "' + field + '"'));
  }
});

test("business category uses its real hierarchy API and Yak UI TreeSelect", () => {
  assert.match(sidebar, /businessTypeApi\.tree\(projectCode\)/);
  assert.match(sidebar, /<TreeSelect/);
  assert.match(sidebar, /items=\{businessTypeTreeOptions\}/);
  assert.match(sidebar, /searchPlaceholder="搜索业务分类"/);
  assert.match(sidebar, /children: children\.length \? children : undefined/);
  assert.doesNotMatch(sidebar, /businessTypeApi\.all/);
});

test("application layer supports data mart and subject domain scope selectors", () => {
  assert.match(sidebar, /APPLICATION_SCOPE_OPTIONS/);
  assert.match(sidebar, /dataMartApi\.all<ModelingRecord>\(projectCode\)/);
  assert.match(sidebar, /subjectDomainApi\.all<ModelingRecord>\(projectCode\)/);
  assert.match(sidebar, /value: "DATA_MART"/);
  assert.match(sidebar, /value: "SUBJECT_DOMAIN"/);
  assert.match(groupingEnum, /DATA_MART/);
  assert.match(groupingEnum, /SUBJECT_DOMAIN/);
  assert.match(treeBuilder, /collectDescendantIds/);
  assert.match(treeBuilder, /retainPathToSelection/);
});

test("dimension tree rows follow the atom indicator tree visual contract", () => {
  assert.match(sidebar, /group flex min-h-8 w-full items-center gap-1 rounded-none px-0 text-left text-xs leading-6 transition-colors/);
  assert.match(sidebar, /boxShadow: "0 0 0 0\.5px #eceff780"/);
  assert.match(sidebar, /paddingLeft: depth \* 24/);
  assert.match(sidebar, /width="8"/);
  assert.match(sidebar, /height="8"/);
  assert.match(sidebar, /duration-150/);
  assert.match(sidebar, /bg-\[#DFE7FF\] text-\[var\(--yak-components-panel-text\)\]/);
  assert.match(sidebar, /<DimensionTreeSkeleton \/>/);
  assert.match(sidebar, /PlanningIcon name="business-type"/);
  assert.match(sidebar, /PlanningIcon name="data-domain"/);
});

test("dimension tree search preserves matching ancestors and expands only the matching path", () => {
  const tree = [{
    key: "root",
    title: "维度建模",
    type: "ROOT_NODE",
    leaf: false,
    children: [
      {
        key: "business-a",
        title: "客户业务",
        type: "BUSINESS_TYPE",
        leaf: false,
        children: [{
          key: "table-a",
          title: "客户维度表",
          searchText: "dim_customer",
          type: "DIM",
          leaf: true,
        }],
      },
      {
        key: "business-b",
        title: "交易业务",
        type: "BUSINESS_TYPE",
        leaf: false,
        children: [{
          key: "table-b",
          title: "订单维度表",
          searchText: "dim_order",
          type: "DIM",
          leaf: true,
        }],
      },
    ],
  }];

  const filtered = filterDimensionTree(tree, "dim_customer");
  assert.deepEqual(filtered.map((node) => node.key), ["root"]);
  assert.deepEqual(filtered[0].children?.map((node) => node.key), ["business-a"]);
  assert.deepEqual(collectDimensionExpandedKeys(filtered), ["root", "business-a"]);
});

test("backend tree filters selected parent scopes by descendants and preserves the selected path", () => {
  assert.match(treeBuilder, /wrapper\.in\("bus_type_id", collectDescendantIds/);
  assert.match(treeBuilder, /wrapper\.in\("mart_id", collectDescendantIds/);
  assert.match(treeBuilder, /wrapper\.in\("subject_id", collectDescendantIds/);
  assert.match(treeBuilder, /roots\.removeIf\(node -> !retainPathToSelection/);
  assert.match(treeBuilder, /tableNode\.setSearchText\(table\.getTableName\(\)\)/);
  assert.match(treeBuilder, /应用层目录仅支持按数据集市或主题域分组/);
});

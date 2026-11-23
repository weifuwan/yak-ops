import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";
import {
  collectDimensionExpandedKeys,
  filterDimensionTree,
} from "../apps/web/app/data-modeling/models/dimension-modeling/workbench/utils/dimension-tree.ts";
import {
  preferredWarehouseLayer,
  warehouseEnumCode,
  warehouseLayerOptions,
} from "../apps/web/app/data-modeling/models/dimension-modeling/workbench/utils/warehouse-layers.ts";
import { availableDataDomains, defaultDataDomainId } from "../apps/web/app/data-modeling/models/dimension-modeling/workbench/utils/default-data-domain.ts";
import { buildApplicationScopeTree } from "../apps/web/app/data-modeling/models/dimension-modeling/workbench/utils/application-scope-tree.ts";

const root = dirname(fileURLToPath(import.meta.url));
const read = (path) => readFileSync(join(root, path), "utf8");

const api = read("../apps/web/service/data-modeling/models/dimension-modeling.ts");
const hook = read("../apps/web/app/data-modeling/models/dimension-modeling/workbench/hooks/use-dimension-tree.ts");
const sidebar = read("../apps/web/app/data-modeling/models/dimension-modeling/workbench/components/dimension-sidebar.tsx");
const businessTreeOptions = read("../apps/web/app/data-modeling/models/dimension-modeling/workbench/utils/business-type-tree-options.tsx");
const controller = read("../../yak-ops-api/src/main/java/io/yak/ops/api/controller/DimensionModelingController.java");
const groupingEnum = read("../../yak-ops-modeling/src/main/java/io/yak/ops/modeling/domain/enums/DimensionTreeGroupingEnum.java");
const treeBuilder = read("../../yak-ops-modeling/src/main/java/io/yak/ops/modeling/application/service/DimensionTreeBuilder.java");
const editor = read("../apps/web/app/data-modeling/models/dimension-modeling/workbench/components/dimension-editor.tsx");
const fieldSpreadsheet = read("../apps/web/app/data-modeling/models/dimension-modeling/workbench/components/field-spreadsheet/field-spreadsheet.tsx");
const fieldGridUtils = read("../apps/web/app/data-modeling/models/dimension-modeling/workbench/components/field-spreadsheet/field-grid-utils.ts");
const fieldGridTypes = read("../apps/web/app/data-modeling/models/dimension-modeling/workbench/components/field-spreadsheet/field-grid-types.ts");
const fieldGridSelect = read("../apps/web/app/data-modeling/models/dimension-modeling/workbench/components/field-spreadsheet/field-grid-select.tsx");
const fieldSpreadsheetToolbar = read("../apps/web/app/data-modeling/models/dimension-modeling/workbench/components/field-spreadsheet/field-spreadsheet-toolbar.tsx");
const fieldReferenceDialog = read("../apps/web/app/data-modeling/models/dimension-modeling/workbench/components/field-spreadsheet/field-reference-dialog.tsx");
const fieldBatchImportDialog = read("../apps/web/app/data-modeling/models/dimension-modeling/workbench/components/field-spreadsheet/field-batch-import-dialog.tsx");
const createTypes = read("../apps/web/app/data-modeling/models/dimension-modeling/workbench/types.ts");
const dimensionPo = read("../../yak-ops-dao/src/main/java/io/yak/ops/dao/entity/DimensionTablePO.java");
const dimensionService = read("../../yak-ops-modeling/src/main/java/io/yak/ops/modeling/application/service/impl/DimensionModelingServiceImpl.java");
const dimensionSql = read("../../yak-ops-modeling/src/main/resources/sql/modeling_postgresql.sql");

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

test("source layer only filters by business type without a grouping menu", () => {
  assert.match(sidebar, /const SOURCE_SCOPE_OPTIONS:[\s\S]*?\{ value: "BUSINESS_TYPE", label: "业务分类" \}/);
  assert.match(sidebar, /dwType === "SOURCE_LAYER"\s*\? SOURCE_SCOPE_OPTIONS/);
  assert.match(sidebar, /dwType === "SOURCE_LAYER" \? \(\s*<span/);
  assert.match(sidebar, /aria-label="贴源层仅按业务分类筛选"/);
  assert.match(sidebar, /<ScopeIcon scope="BUSINESS_TYPE" \/>/);
  assert.match(sidebar, /\) : \(\s*<Select\s+size="small"/);
  assert.match(hook, /const \[groupBy, setGroupBy\] = useState<DimensionTreeGrouping>\("BUSINESS_TYPE"\)/);
  assert.match(hook, /return dwType === "APPLICATION_LAYER" \? "DATA_MART" : "BUSINESS_TYPE"/);
  assert.match(hook, /if \(dwType === "SOURCE_LAYER" && value !== "BUSINESS_TYPE"\) return/);
  assert.match(hook, /setGroupBy\(defaultGrouping\(nextDwType\)\)/);
  assert.match(hook, /selectOption: groupBy/);
  assert.match(sidebar, /const COMMON_SCOPE_OPTIONS:[\s\S]*?"DATA_DOMAIN"/);
  assert.match(sidebar, /const APPLICATION_SCOPE_OPTIONS:[\s\S]*?"SUBJECT_DOMAIN"/);
});

test("business category uses its real hierarchy API and Yak UI TreeSelect", () => {
  assert.match(sidebar, /businessTypeApi\.tree\(projectCode\)/);
  assert.match(sidebar, /<TreeSelect/);
  assert.match(sidebar, /items=\{businessTypeTreeOptions\}/);
  assert.match(sidebar, /searchPlaceholder="搜索业务分类"/);
  assert.match(businessTreeOptions, /children: children\.length \? children : undefined/);
  assert.match(sidebar, /mapBusinessTypeTreeOption/);
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
  assert.match(treeBuilder, /tableNode\.setSearchText\([\s\S]*table\.getEnShortName\(\)/);
  assert.match(treeBuilder, /应用层目录仅支持按数据集市或主题域分组/);
});


test("detail model uses the shared Yak-UI Cascader to select a domain and business process", () => {
  const sharedHook = read("../apps/web/app/data-modeling/planning/common-layer/business-process/use-business-process-cascader.ts");
  const oldHook = read("../apps/web/app/data-modeling/indicators/atom-indicator/hooks/use-business-process-cascader.ts");
  const service = read("../apps/web/service/data-modeling/planning/common-layer/business-process.ts");

  assert.match(editor, /import \{ useBusinessProcessCascader \}/);
  assert.match(editor, /const processQuery = useBusinessProcessCascader\(projectCode, values\.busTypeId, selectedTableType === "DETAIL"\)/);
  assert.match(editor, /<FormField label="业务过程" required>\s*<Cascader/);
  assert.match(editor, /items=\{processQuery\.options\}/);
  assert.match(editor, /value=\{values\.domainId && values\.busProcessId \? \[values\.domainId, values\.busProcessId\] : null\}/);
  assert.match(editor, /columnLabels=\{\["数据域", "业务过程"\]\}/);
  assert.match(editor, /domainId: path\?\.\[0\] \|\| ""/);
  assert.match(editor, /busProcessId: path\?\.\[path\.length - 1\] \|\| ""/);
  assert.match(editor, /processQuery\.refetch\(\)/);
  assert.doesNotMatch(editor, /processOptions|processesQuery/);
  assert.match(sharedHook, /businessProcessApi\.cascaderOption\(projectCode, busTypeId\)/);
  assert.match(sharedHook, /enabled: Boolean\(projectCode && enabled\)/);
  assert.match(sharedHook, /selectable: depth > 0 && children\.length === 0/);
  assert.match(oldHook, /useBusinessProcessCascader/);
  assert.match(service, /"\/option\/cascader"/);
  assert.match(editor, /busTypeId: businessTypeId, domainId: "", busProcessId: ""/);
});

test("dimension editor selects hierarchical business category IDs through Yak-UI TreeSelect", () => {
  assert.ok(editor.includes('businessTypeApi.tree(projectCode)'));
  assert.ok(editor.includes('planningQueryKeys.businessTypeTree(projectCode)'));
  assert.ok(!editor.includes('businessTypeApi.all<ModelingRecord>'));
  assert.ok(editor.includes('<TreeSelect'));
  assert.ok(editor.includes('items={businessTypeTreeOptions}'));
  assert.ok(editor.includes('value={values.busTypeId || null}'));
  assert.ok(editor.includes('searchPlaceholder="搜索业务分类"'));
  assert.ok(editor.includes('onValueChange={(value) => changeBusinessType(value || "")}'));
  assert.ok(editor.includes('businessTypeTreeQuery.refetch()'));
  assert.ok(editor.includes('busTypeId: values.busTypeId || null'));
  assert.ok(businessTreeOptions.includes('value,'));
  assert.ok(businessTreeOptions.includes('label: String(node.title || value)'));
  assert.ok(sidebar.includes('from "../utils/business-type-tree-options"'));
});

test("application model groups marts and subject domains beneath nonselectable business categories", () => {
  const businessTypes = [{
    key: "retail", value: "retail", title: "零售", children: [
      { key: "retail-child", value: "retail-child", title: "零售业务分类" },
    ],
  }, { key: "services", value: "services", title: "服务业" }];
  const marts = [
    { id: "mart-main", businessTypeId: "retail-child" },
    { id: "mart-orphan", businessTypeId: "removed-business-type" },
  ];
  const scope = [
    { key: "mart-main-MART", value: "mart-main-MART", title: "行业集市", type: "MART", children: [
      { key: "subject-a-SUBJECT", value: "subject-a-SUBJECT", title: "商品", type: "SUBJECT", children: [
        { key: "subject-child-SUBJECT", value: "subject-child-SUBJECT", title: "商品属性", type: "SUBJECT" },
      ] },
      { key: "mart-child-MART", value: "mart-child-MART", title: "子集市", type: "MART" },
    ] },
    { key: "mart-orphan-MART", value: "mart-orphan-MART", title: "遗留集市", type: "MART" },
  ];
  const tree = buildApplicationScopeTree(businessTypes, marts, scope);
  assert.equal(tree[0].label, "零售");
  assert.equal(tree[0].selectable, false);
  assert.equal(tree[0].children?.[0]?.label, "零售业务分类");
  assert.equal(tree[0].children?.[0]?.selectable, false);
  assert.equal(tree[0].children?.[0]?.children?.[0]?.value, "mart-main-MART");
  assert.equal(tree[0].children?.[0]?.children?.[0]?.children?.[0]?.value, "subject-a-SUBJECT");
  assert.equal(tree[0].children?.[0]?.children?.[0]?.children?.[0]?.children?.[0]?.value, "subject-child-SUBJECT");
  assert.equal(tree[0].children?.[0]?.children?.[0]?.children?.[1]?.value, "mart-child-MART");
  assert.equal(tree[1].selectable, false);
  assert.equal(tree[2].label, "未关联业务分类");
  assert.equal(tree[2].selectable, false);
  assert.equal(tree[2].children?.[0]?.value, "mart-orphan-MART");
  assert.deepEqual(buildApplicationScopeTree([], [], []), []);
});

test("application model TreeSelect preserves the save contract and disallows business category choice", () => {
  assert.match(editor, /queryFn: \(\) => subjectDomainApi\.tree\(projectCode\)/);
  assert.match(editor, /buildApplicationScopeTree\(/);
  assert.match(editor, /<FormField label="集市\/主题" required>\s*<TreeSelect/);
  assert.match(editor, /items=\{martSubjectOptions\}/);
  assert.match(editor, /value=\{values\.martOrSubject \|\| null\}/);
  assert.match(editor, /searchPlaceholder="搜索数据集市或主题域"/);
  assert.match(editor, /onValueChange=\{\(value\) => changeValue\("martOrSubject", value \|\| ""\)\}/);
  assert.match(editor, /martOrSubject: values\.martOrSubject \|\| null/);
  assert.match(dimensionService, /"SUBJECT"\.equalsIgnoreCase\(type\)/);
  assert.match(dimensionService, /"MART"\.equalsIgnoreCase\(type\)/);
  assert.doesNotMatch(editor, /subjectsQuery|dimension-editor-marts/);
});

test("dimension creation groups each Base UI menu label with its items", () => {
  const menu = sidebar.match(
    /<DropdownMenuContent align="end" side="bottom" className="w-44">([\s\S]*?)<\/DropdownMenuContent>/,
  )?.[1];
  assert.ok(menu, "dimension creation dropdown should exist");

  const groups = [...menu.matchAll(/<DropdownMenuGroup>([\s\S]*?)<\/DropdownMenuGroup>/g)]
    .map((match) => match[1]);
  assert.equal(groups.length, 2);
  assert.match(groups[0], /<DropdownMenuLabel>维度模型<\/DropdownMenuLabel>/);
  assert.match(groups[0], /onClick=\{\(\) => onCreate\("DIM"\)\}/);
  assert.match(groups[1], /<DropdownMenuLabel>逻辑模型<\/DropdownMenuLabel>/);
  assert.match(groups[1], /DIMENSION_CREATE_OPTIONS\.filter/);
});

test("dimension creation offers six typed entries and excludes NONE_LAYER", () => {
  for (const type of ["DIM", "SOURCE", "DIMENSION", "DETAIL", "SUMMARY", "APPLICATION"]) {
    assert.match(createTypes, new RegExp('type: "' + type + '"'));
  }
  assert.doesNotMatch(createTypes, /NONE_LAYER/);
  assert.match(editor, /warehouseLayerOptions/);
  assert.match(dimensionService, /未分层仅用于历史数据管理/);
});

test("warehouse layer selection binds IDs, displays real names, and handles serialized enums", () => {
  const layers = [
    { id: "dim-default", enShortName: "DIM", cnName: "维度层", dwType: "PUBLIC_LAYER", dwModelType: "DIMENSION_DIM" },
    { id: "dim-custom", enShortName: "CUST_DIM", cnName: "公共维度层", dwType: { code: "PUBLIC_LAYER" }, dwModelType: { value: "DIM" } },
    { id: "dim-app", enShortName: "DIM_APP", cnName: "应用维度", dwType: "APPLICATION_LAYER", dwModelType: "DIMENSION_DIM" },
    { id: "detail", enShortName: "DWD", cnName: "明细层", dwType: "PUBLIC_LAYER", dwModelType: "DETAIL" },
    { id: "source", enShortName: "ODS", cnName: "贴源层", dwType: "SOURCE_LAYER", dwModelType: "SOURCE" },
    { id: "summary", enShortName: "DWS", cnName: "汇总数据层", dwType: "PUBLIC_LAYER", dwModelType: "SUMMARY" },
    { id: "application", enShortName: "ADS", cnName: "应用数据层", dwType: "APPLICATION_LAYER", dwModelType: "APPLICATION" },
    { id: "invalid", cnName: "错误配置", dwType: "SOURCE_LAYER", dwModelType: "DIM" },
    { id: "legacy", cnName: "历史未分层", dwType: "NONE_LAYER", dwModelType: null },
  ];
  assert.equal(warehouseEnumCode({ name: "public_layer" }), "PUBLIC_LAYER");
  const dimensions = warehouseLayerOptions(layers, "DIM");
  assert.deepEqual(dimensions.map((item) => item.value), ["dim-default", "dim-custom"]);
  assert.equal(dimensions[0].label, "公共层 · 维度层");
  assert.equal(dimensions[1].label, "公共层 · 公共维度层");
  assert.equal(preferredWarehouseLayer(dimensions, "DIM", "PUBLIC_LAYER")?.value, "dim-default");
  assert.deepEqual(warehouseLayerOptions(layers, "DIMENSION").map((item) => item.value), ["dim-default", "dim-app"]);
  assert.deepEqual(warehouseLayerOptions(layers, "SOURCE").map((item) => item.value), ["source"]);
  assert.deepEqual(warehouseLayerOptions(layers, "DETAIL").map((item) => item.value), ["detail"]);
  assert.deepEqual(warehouseLayerOptions(layers, "SUMMARY").map((item) => item.value), ["summary"]);
  assert.deepEqual(warehouseLayerOptions(layers, "APPLICATION").map((item) => item.value), ["application"]);
  assert.deepEqual(warehouseLayerOptions(layers, "DIM", "legacy").map((item) => item.value), ["dim-default", "dim-custom", "legacy"]);
  assert.deepEqual(warehouseLayerOptions([], "DIM"), []);
});

test("new dimension models default to the first compatible warehouse layer without replacing an existing selection", () => {
  assert.match(editor, /const firstLayerId = layerOptions\[0\]\?\.value/);
  assert.match(editor, /tab\.mode !== "create" \|\| recordId \|\| !layerOptions\.length/);
  assert.match(editor, /setValues\(\(current\) => current\.dwhId/);
  assert.doesNotMatch(editor, /preferredWarehouseLayer\(layerOptions/);

  const layerRows = [
    { id: "invalid", cnName: "应用数据层", dwType: "APPLICATION_LAYER", dwModelType: "APPLICATION" },
    { id: "dim-2", cnName: "企业维度层", dwType: "PUBLIC_LAYER", dwModelType: "DIMENSION_DIM" },
    { id: "dim-1", cnName: "公共维度层", dwType: "PUBLIC_LAYER", dwModelType: "DIM" },
  ];
  assert.equal(warehouseLayerOptions(layerRows, "DIM")[0].value, "dim-2");
  assert.equal(warehouseLayerOptions(layerRows, "APPLICATION")[0].value, "invalid");
});

test("new dimensions choose the protected system-default data domain, otherwise the first option", () => {
  const domainRows = [
    { id: "commerce", enShortName: "commerce", flag: 0 },
    { id: "default", enShortName: "default", flag: 1, cnName: "默认" },
    { id: "analytics", enShortName: "analytics", flag: 0 },
  ];
  assert.equal(defaultDataDomainId(domainRows), "default");
  assert.equal(defaultDataDomainId(domainRows.slice(0, 1)), "commerce");
  assert.equal(defaultDataDomainId([]), "");
  assert.equal(defaultDataDomainId([{ id: "named", enShortName: "default", flag: 0 }, domainRows[0]]), "named");
  assert.equal(defaultDataDomainId([{ id: "system", flag: "1" }, domainRows[0]]), "system");
  assert.match(editor, /tab\.mode !== "create" \|\| recordId \|\| \(!isDim && !isSummary\)/);
  assert.match(editor, /defaultDataDomainId\(availableDomains\)/);
  assert.match(editor, /setValues\(\(current\) => current\.domainId/);
  assert.match(editor, /domainId: resolvedDomainId \|\| null/);
});

test("async defaults remain visible in controlled Base UI Select before effects hydrate form state", () => {
  // Passing undefined first creates an uncontrolled Select, so async updates may not display.
  assert.ok(editor.includes('items={Object.fromEntries(options.map((option) => [option.value, option.label]))}'));
  assert.ok(editor.includes('value={value || null}'));
  assert.ok(editor.includes('<SelectValue placeholder={placeholder} />'));
  assert.ok(!editor.includes('value={value || undefined}'));
  assert.ok(editor.includes('const isNewDraft = tab.mode === "create" && !recordId'));
  assert.ok(editor.includes('const resolvedDwhId = isNewDraft ? values.dwhId || layerOptions[0]?.value || "" : values.dwhId'));
  assert.ok(editor.includes('const resolvedDomainId = isNewDraft && (isDim || isSummary)'));
  assert.ok(editor.includes('value={resolvedDwhId}'));
  assert.ok(editor.includes('value={resolvedDomainId}'));
  assert.ok(editor.includes('dwhId: resolvedDwhId'));
  assert.ok(editor.includes('domainId: resolvedDomainId || null'));
  assert.ok(editor.includes('setValues((current) => ({ ...current, dwhId: resolvedDwhId, domainId: resolvedDomainId }))'));
  assert.ok(editor.includes('if (tab.mode !== "create" || recordId || !layerOptions.length) return'));
  assert.ok(editor.includes('setValues((current) => current.dwhId'));
  assert.ok(editor.includes('setValues((current) => current.domainId'));
});

test("dimension domain choices respect business category associations and fall back only on no links", () => {
  const all = [
    { id: "default", cnName: "默认", enShortName: "default", flag: 1 },
    { id: "finance", cnName: "财务域", flag: 0 },
    { id: "market", cnName: "营销域", flag: 0 },
  ];
  assert.deepEqual(availableDataDomains(all, "", null).map((domain) => domain.id),
    ["default", "finance", "market"]);
  assert.deepEqual(availableDataDomains(all, "category-a", null), []);
  assert.deepEqual(availableDataDomains(all, "category-a", []).map((domain) => domain.id),
    ["default", "finance", "market"]);
  assert.deepEqual(availableDataDomains(all, "category-a", [{ id: "finance" }]).map((domain) => domain.id),
    ["finance"]);
  assert.deepEqual(availableDataDomains(all, "category-a", [{ id: "deleted" }]), []);
  assert.equal(defaultDataDomainId(availableDataDomains(all, "category-a", [{ id: "finance" }])),
    "finance");
  assert.equal(defaultDataDomainId(availableDataDomains(all, "category-a", [])), "default");
});

test("dimension editor reloads linked domains per selected business type and resets stale dependents", () => {
  assert.match(editor, /planningQueryKeys\.businessTypeRelatedDomains\(projectCode, values\.busTypeId\)/);
  assert.match(editor, /businessTypeApi\.selectRelatedDomains<ModelingRecord>\(projectCode, values\.busTypeId\)/);
  assert.match(editor, /enabled: Boolean\(projectCode && values\.busTypeId\)/);
  assert.match(editor, /const domainOptionsReady = domainsQuery\.isSuccess && \(!values\.busTypeId \|\| relatedDomainsQuery\.isSuccess\)/);
  assert.match(editor, /const availableDomains = useMemo\(\(\) => availableDataDomains\(/);
  assert.match(editor, /domainOptionsReady \? defaultDataDomainId\(availableDomains\) : ""/);
  assert.match(editor, /busTypeId: businessTypeId, domainId: "", busProcessId: ""/);
  assert.match(editor, /onValueChange=\{\(value\) => changeBusinessType\(value \|\| ""\)\}/);
  assert.match(editor, /relatedDomainsQuery\.refetch\(\)/);
  assert.match(editor, /emptyContent=\{domainEmptyContent\}/);
  assert.match(editor, /domainId: resolvedDomainId \|\| null/);
});

test("dimension editor only passes emptyContent to Yak-UI Select when there are no options", () => {
  assert.ok(editor.includes("<SelectContent emptyContent={options.length === 0 ? emptyContent : undefined}>"));
  assert.ok(!editor.includes("<SelectContent emptyContent={emptyContent}>"));
});

test("dimension editor keeps dwhId when saving and distinguishes layer load states", () => {
  assert.match(editor, /dwhId: resolvedDwhId/);
  assert.match(editor, /options=\{displayedLayerOptions\}/);
  assert.match(editor, /emptyContent=\{layerEmptyContent\}/);
  assert.match(editor, /layersQuery\.isError/);
  assert.match(editor, /layersQuery\.isPending/);
  assert.match(editor, /layersQuery\.isSuccess && layerOptions\.length === 0/);
  assert.match(editor, /layersQuery\.refetch\(\)/);
});

test("model type is hidden for creation and typed edits while legacy migration remains editable", () => {
  const basicInfo = editor.slice(
    editor.indexOf('<FormSectionHeader title="基本信息" />'),
    editor.indexOf('{isApplication ? (', editor.indexOf('<FormSectionHeader title="基本信息" />')),
  );
  assert.ok(!basicInfo.includes('value={modelTypeLabel}'));
  assert.ok(!basicInfo.includes('selectedTableType ? ('));
  assert.ok(basicInfo.includes('{canChooseLegacyType ? ('));
  assert.ok(basicInfo.includes('value={values.tableType}'));
  assert.ok(basicInfo.includes('onChange={(value) => changeValue("tableType", value)}'));
  assert.ok(!basicInfo.includes('value="历史记录未记录模型类型，迁移时可指定"'));
  assert.ok(editor.includes('tableType: String(tab.createType || tab.record?.tableType || "")'));
  assert.ok(editor.includes('tableType: selectedTableType || null'));
});

test("dimension editor uses Yak-UI warning alert for unsaved changes and unsubmitted drafts", () => {
  assert.match(editor, /import \{[\s\S]*?Alert,[\s\S]*?\} from "@yak-ops\/yak-ui"/);
  assert.match(editor, /<Alert className="shrink-0 rounded-none border-x-0 border-t-0 px-4 py-1\.5">/);
  assert.match(editor, /const noticeMessage = dirty/);
  assert.match(editor, /当前存在未保存的修改，请先保存相关内容，再提交生成版本/);
  assert.match(editor, /当前草稿尚未提交，确认内容无误后，可点击「提交」生成版本/);
  assert.match(editor, /showNotice && !noticeDismissed/);
  assert.match(editor, /onClick=\{\(\) => setNoticeDismissed\(true\)\}/);
  assert.doesNotMatch(editor, /border-\[#dce6fb\] bg-\[#eef4ff\]/);
});

test("dimension editor uses one save action for basic information and fields", () => {
  const basicSaveStart = editor.indexOf("const saveBasicInfo = async");
  const fieldSaveStart = editor.indexOf("const saveFields = async");
  const unifiedSaveStart = editor.indexOf("const saveChanges = async");
  const submitStart = editor.indexOf("const submit = async");
  assert.ok(basicSaveStart >= 0 && fieldSaveStart > basicSaveStart);
  assert.ok(unifiedSaveStart > fieldSaveStart && submitStart > unifiedSaveStart);
  assert.doesNotMatch(editor.slice(basicSaveStart, fieldSaveStart), /replaceFields/);
  assert.match(editor, /dimensionModelingApi\.replaceFields/);

  const unifiedSave = editor.slice(unifiedSaveStart, submitStart);
  assert.match(unifiedSave, /saveBasicInfo\(false\)/);
  assert.match(unifiedSave, /targetRecordId = String\(basicSaved\.id \|\| ""\)/);
  assert.match(unifiedSave, /saveFields\(false, targetRecordId\)/);

  const toolbarStart = editor.indexOf('<div className="flex flex-wrap items-center gap-1.5">');
  const refreshStart = editor.indexOf('<Button size="sm" variant="ghost" onClick={() => void refresh()}>', toolbarStart);
  const toolbar = editor.slice(toolbarStart, refreshStart);
  assert.match(toolbar, /variant="ghost"/);
  assert.match(toolbar, /<Save size=\{13\} \/>保存/);
  assert.match(toolbar, /<Upload size=\{13\} \/>提交/);
  assert.doesNotMatch(toolbar, /保存字段|保存基本信息/);
  assert.match(editor, /fieldsForSave\(fields\)/);
  assert.match(editor, /validateDimensionFields\(persistedFields\)/);
  assert.match(editor, /fieldType="PROPERTY"/);
  assert.match(editor, /fieldType="PARTITION"/);
  assert.doesNotMatch(editor, /请先保存基本信息，再配置字段/);
  assert.doesNotMatch(editor, /请先保存基本信息，再配置分区字段/);
  assert.match(editor, /const saveFields = async \(showSuccess = true, targetRecordId = recordId\)/);
});

test("dimension field management uses a spreadsheet editor instead of editable Yak-UI tables", () => {
  assert.match(editor, /import FieldSpreadsheet/);
  assert.match(editor, /<FieldSpreadsheet[\s\S]*?fieldType="PROPERTY"/);
  assert.match(editor, /<FieldSpreadsheet[\s\S]*?fieldType="PARTITION"/);
  assert.doesNotMatch(editor, /const fieldColumns:/);
  assert.doesNotMatch(editor, /addField\(/);
  assert.match(fieldSpreadsheet, /role="grid"/);
  assert.match(fieldSpreadsheet, /role="gridcell"/);
  assert.match(fieldSpreadsheet, /ring-2 ring-inset ring-\[var\(--yak-color-primary\)\]/);
  assert.match(fieldSpreadsheet, /<FieldGridSelect/);
  assert.match(fieldGridSelect, /<Select<string>/);
  assert.match(fieldSpreadsheet, /<Checkbox/);
});

test("field spreadsheet supports keyboard navigation, paste, row operations and cell validation", () => {
  assert.match(fieldSpreadsheet, /handleGridPaste/);
  assert.match(fieldSpreadsheet, /event\.key === "Tab"/);
  assert.match(fieldSpreadsheet, /event\.key === "Enter"/);
  assert.match(fieldSpreadsheet, /ArrowLeft/);
  assert.match(fieldSpreadsheet, /ArrowRight/);
  assert.match(fieldSpreadsheet, /ArrowUp/);
  assert.match(fieldSpreadsheet, /ArrowDown/);
  assert.match(fieldSpreadsheet, /navigator\.clipboard/);
  assert.match(fieldSpreadsheetToolbar, /移除空白行/);
  assert.match(fieldSpreadsheetToolbar, /插入/);
  assert.match(fieldSpreadsheet, /validateDimensionFields\(fields\)/);
  assert.match(fieldGridUtils, /字段名称不能为空/);
  assert.match(fieldGridUtils, /字段名称重复/);
  assert.match(fieldGridUtils, /精度不能大于长度/);
  assert.match(fieldGridUtils, /fieldsForSave/);
});

test("source tables expose the DataWorks-style field column model", () => {
  assert.match(editor, /modelType=\{selectedTableType\}/);
  assert.match(fieldGridTypes, /fieldGridColumns/);
  assert.match(fieldGridTypes, /modelType !== "SOURCE"/);
  for (const key of ["sequence", "fieldName", "dataType", "cnName", "remark", "primaryFlag", "notNull", "sourceTb", "sourceField", "fieldType", "fieldStdId", "codeStdId", "actions"]) {
    assert.match(fieldGridTypes, new RegExp('"' + key + '"'));
  }
  assert.match(fieldGridTypes, /FIELD_CATEGORY_OPTIONS/);
  assert.match(fieldGridTypes, /PROPERTY/);
  assert.match(fieldGridTypes, /DIMENSION/);
  assert.match(fieldGridTypes, /MEASUREMENT/);
  assert.match(fieldSpreadsheet, /<FieldGridSelect[\s\S]*?options=\{FIELD_CATEGORY_OPTIONS\}/);
  assert.match(fieldSpreadsheet, /normalizeSectionField/);
  assert.match(editor, /field\.fieldType && field\.fieldType !== "PARTITION"/);
  assert.match(fieldSpreadsheetToolbar, /disabled=\{fixed\}/);
  assert.match(fieldSpreadsheet, /const referenceLabel = modelType === "SOURCE" \? "引用来源表" : "引用已有表"/);
  assert.match(fieldSpreadsheet, /引用来源表字段/);
});

test("field spreadsheet exposes advanced columns, batch import and existing-table references", () => {
  for (const title of ["关联字段标准", "关联标准代码", "度量单位", "来源表", "来源字段", "字段类别", "描述"]) {
    assert.match(fieldGridTypes, new RegExp(title));
  }
  assert.match(fieldSpreadsheetToolbar, /字段显示设置/);
  assert.match(fieldSpreadsheet, /FieldBatchImportDialog/);
  assert.match(fieldSpreadsheet, /FieldReferenceDialog/);
  assert.match(fieldBatchImportDialog, /parseImportedFields/);
  assert.match(fieldBatchImportDialog, /可直接从 Excel 粘贴/);
  assert.match(fieldReferenceDialog, /projectCode/);
  assert.match(fieldReferenceDialog, /listDataSources/);
  assert.match(fieldReferenceDialog, /listDataSourceDatabases/);
  assert.match(fieldReferenceDialog, /listDataSourceTables/);
  assert.match(fieldReferenceDialog, /listDataSourceColumns/);
  assert.match(fieldReferenceDialog, /sourceTb: sourceTable/);
  assert.match(fieldReferenceDialog, /sourceField: column\.name/);
});

test("DIM business identifiers use enShortName instead of tableName", () => {
  assert.match(api, /enShortName\?: string/);
  assert.match(dimensionPo, /private String enShortName;/);
  assert.match(dimensionSql, /en_short_name varchar\(255\)/);
  assert.match(dimensionService, /dto\.setTableName\(null\)/);
  assert.match(treeBuilder, /getEnShortName\(\)/);
  assert.match(dimensionService, /en_short_name/);
});

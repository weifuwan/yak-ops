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
import { defaultDataDomainId } from "../apps/web/app/data-modeling/models/dimension-modeling/workbench/utils/default-data-domain.ts";

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


test("dimension editor selects hierarchical business category IDs through Yak-UI TreeSelect", () => {
  assert.ok(editor.includes('businessTypeApi.tree(projectCode)'));
  assert.ok(editor.includes('planningQueryKeys.businessTypeTree(projectCode)'));
  assert.ok(!editor.includes('businessTypeApi.all<ModelingRecord>'));
  assert.ok(editor.includes('<TreeSelect'));
  assert.ok(editor.includes('items={businessTypeTreeOptions}'));
  assert.ok(editor.includes('value={values.busTypeId || null}'));
  assert.ok(editor.includes('searchPlaceholder="搜索业务分类"'));
  assert.ok(editor.includes('onValueChange={(value) => changeValue("busTypeId", value || "")}'));
  assert.ok(editor.includes('businessTypeTreeQuery.refetch()'));
  assert.ok(editor.includes('busTypeId: values.busTypeId || null'));
  assert.ok(businessTreeOptions.includes('value,'));
  assert.ok(businessTreeOptions.includes('label: String(node.title || value)'));
  assert.ok(sidebar.includes('from "../utils/business-type-tree-options"'));
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
  assert.match(editor, /defaultDataDomainId\(domainsQuery\.data \|\| \[\]\)/);
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

test("basic information and field management are saved independently", () => {
  const basicSaveStart = editor.indexOf("const saveBasicInfo = async");
  const fieldSaveStart = editor.indexOf("const saveFields = async");
  assert.ok(basicSaveStart >= 0 && fieldSaveStart > basicSaveStart);
  assert.doesNotMatch(editor.slice(basicSaveStart, fieldSaveStart), /replaceFields/);
  assert.match(editor, /dimensionModelingApi\.replaceFields/);
  assert.match(editor, /保存基本信息/);
  assert.match(editor, /保存字段/);
  assert.match(editor, /addField\("PARTITION"\)/);
});

test("DIM business identifiers use enShortName instead of tableName", () => {
  assert.match(api, /enShortName\?: string/);
  assert.match(dimensionPo, /private String enShortName;/);
  assert.match(dimensionSql, /en_short_name varchar\(255\)/);
  assert.match(dimensionService, /dto\.setTableName\(null\)/);
  assert.match(treeBuilder, /getEnShortName\(\)/);
  assert.match(dimensionService, /en_short_name/);
});

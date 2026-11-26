import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";
import { fieldOptions } from "../apps/web/app/data-modeling/indicators/workbench/utils/indicator-form.ts";

const root = dirname(fileURLToPath(import.meta.url));
const read = (path) => readFileSync(join(root, path), "utf8");

const tabs = read("../apps/web/app/data-modeling/indicators/workbench/index.tsx");
const resources = read("../apps/web/app/data-modeling/indicators/workbench/hooks/use-indicator-resources.ts");
const modifier = read("../apps/web/app/data-modeling/indicators/modifier/form/modifier-editor-fields.tsx");
const dimensionEditor = read("../apps/web/app/data-modeling/models/dimension-modeling/workbench/components/dimension-editor.tsx");
const overview = read("../apps/web/app/data-modeling/overview/index.tsx");
const reverseModeling = read("../apps/web/app/data-modeling/models/reverse-modeling/index.tsx");
const versionService = read("../../yak-ops-modeling/src/main/java/io/yak/ops/modeling/application/service/impl/AbstractVersionedModelingAssetService.java");
const baseVo = read("../../yak-ops-modeling/src/main/java/io/yak/ops/modeling/api/vo/ModelingBaseVO.java");
const businessTypeService = read("../../yak-ops-modeling/src/main/java/io/yak/ops/modeling/application/service/impl/BusinessTypeServiceImpl.java");
const timePeriodService = read("../../yak-ops-modeling/src/main/java/io/yak/ops/modeling/application/service/impl/TimePeriodServiceImpl.java");
const mysqlPatch = read("../../yak-ops-modeling/src/main/resources/sql/patches/modeling-publish-table-model_mysql.sql");

test("indicator workbench isolates visible tabs by the selected indicator type", () => {
  assert.match(tabs, /const visibleTabs = tabs\.tabs\.filter\(\(tab\) => tab\.kind === activeKind\)/);
  assert.match(tabs, /tabs=\{visibleTabs\}/);
  assert.match(tabs, /getVisibleTabKeysToClose/);
});

test("indicator option failures are isolated per resource", () => {
  assert.match(resources, /try \{/);
  assert.match(resources, /failedResources/);
  assert.match(resources, /optionsByResource: optionsQuery\.data\?\.optionsByResource/);
  assert.match(modifier, /当前账号无法读取用户列表，请手动输入负责人/);
  assert.match(modifier, /optionsFailedResources\.includes\("dataDomain"\)/);
});

test("new dimension models can choose Doris field types before the first save", () => {
  assert.match(dimensionEditor, /enabled: Boolean\(projectCode && values\.tableType !== "DIM"\)/);
  assert.match(dimensionEditor, /dorisFieldTypeOptions\.length \? dorisFieldTypeOptions : DATA_TYPE_OPTIONS/);
  assert.match(dimensionEditor, /fieldTypeOptions=\{fieldTypeOptions\}/);
});

test("time-period category options remove root nodes and duplicate category codes", () => {
  const options = fieldOptions(
    { key: "categoryId", label: "周期粒度", optionsSource: "timePeriodCategory" },
    {
      timePeriodCategory: [
        { id: "root", enName: "TIME_PERIOD", cnName: "时间周期", leaf: false },
        { id: "root-copy", enName: "TIME_PERIOD", cnName: "时间周期", leaf: false },
        { id: "day", enName: "DAY", cnName: "天", parentId: "root", leaf: true },
        { id: "day-copy", enName: "DAY", cnName: "天", parentId: "root-copy", leaf: true },
        { id: "week", enName: "WEEK", cnName: "周", parentId: "root", leaf: true },
      ],
    },
  );
  assert.deepEqual(options, [
    { value: "day", label: "天" },
    { value: "week", label: "周" },
  ]);
  assert.match(timePeriodService, /同一目录下时间周期粒度英文名称已存在/);
  assert.match(timePeriodService, /Set<String> seen/);
});

test("versioned modeling detail responses expose the latest version number", () => {
  assert.match(baseVo, /private Integer currentVersion/);
  assert.match(versionService, /view\.setCurrentVersion\(latest == null \? null : latest\.getVersionNumber\(\)\)/);
});

test("overview labels submitted assets accurately and removes the inert detail action", () => {
  assert.match(overview, /最新提交/);
  assert.match(overview, /latestSubmitted/);
  assert.doesNotMatch(overview, /最新发布|查看数据详情/);
});

test("business category abbreviations follow the lowercase naming convention", () => {
  assert.match(businessTypeService, /StringUtils\.lowerCase/);
  assert.match(businessTypeService, /a-z0-9/);
  assert.match(businessTypeService, /validateUniqueShortName/);
});

test("Doris publish schema patch adds table_model idempotently for existing MySQL databases", () => {
  assert.match(mysqlPatch, /information_schema\.columns/i);
  assert.match(mysqlPatch, /ADD COLUMN.*table_model/i);
  assert.match(mysqlPatch, /PREPARE/);
});

test("reverse modeling is clearly marked as planned rather than a working capability", () => {
  assert.match(reverseModeling, /逆向建模能力正在规划中/);
});

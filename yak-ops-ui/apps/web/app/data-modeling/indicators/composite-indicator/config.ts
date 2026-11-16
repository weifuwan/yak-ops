import type { IndicatorDefinition, IndicatorFieldOption } from "../workbench/types";

export const COMPOSITE_COMPUTE_MODE_OPTIONS: IndicatorFieldOption[] = [
  { value: "0", label: "计算表达式" },
  { value: "1", label: "同环比" },
];

export const COMPOSITE_COMPARE_TYPE_OPTIONS: IndicatorFieldOption[] = [
  { value: "DAY_ON_LAST_DAY", label: "日环比" },
  { value: "WEEK_ON_LAST_WEEK", label: "周环比" },
  { value: "WEEK_ON_WEEK_LAST_YEAR", label: "周同比" },
  { value: "MONTH_ON_LAST_MONTH", label: "月环比" },
  { value: "MONTH_ON_MONTH_LAST_YEAR", label: "月同比" },
  { value: "QUARTER_ON_LAST_QUARTER", label: "季度环比" },
  { value: "YEAR_ON_LAST_YEAR", label: "年环比" },
  { value: "YEAR_ON_YEAR", label: "年同比" },
];

export const COMPOSITE_RATIO_FORMULA = "(本期数据 - 上期数据) / 上期数据 * 100%";

export const COMPOSITE_INDICATOR_DEFINITION: IndicatorDefinition = {
  key: "composeIndicator",
  title: "复合指标",
  description: "基于派生指标和计算表达式构建复合指标，支持计算表达式及同环比模式。",
  listFilters: [
    {
      key: "businessTypeId",
      paramKey: "busTypeId",
      label: "全部业务分类",
      optionsSource: "businessType",
      optionLabelField: "businessName",
      dwType: "PUBLIC_LAYER",
    },
    {
      key: "domainId",
      paramKey: "domainId",
      label: "全部数据域",
      optionsSource: "dataDomain",
      dwType: "PUBLIC_LAYER",
    },
    {
      key: "martId",
      paramKey: "martId",
      label: "全部数据集市",
      optionsSource: "dataMart",
      optionLabelField: "martName",
      dwType: "APPLICATION_LAYER",
    },
    {
      key: "subjectId",
      paramKey: "subjectId",
      label: "全部主题域",
      optionsSource: "subjectDomain",
      optionLabelField: "domainName",
      dwType: "APPLICATION_LAYER",
    },
  ],
  columnFormats: { cnName: "indicatorName", submit: "status", computeMode: "computeMode" },
  versioned: true,
  fields: [
    { key: "computeMode", label: "计算模式", section: "计算逻辑", required: true, options: COMPOSITE_COMPUTE_MODE_OPTIONS },
    { key: "computeExpr", label: "计算口径", section: "计算逻辑", multiline: true, placeholder: "请输入计算表达式" },
    { key: "deriveId", label: "派生指标", section: "计算逻辑", optionsSource: "derivativeIndicator" },
    { key: "compareType", label: "对比类型", section: "计算逻辑", options: COMPOSITE_COMPARE_TYPE_OPTIONS },
    {
      key: "dwhId",
      label: "数仓分层",
      section: "基本信息",
      required: true,
      optionsSource: "dwLayer",
      optionLabelField: "cnName",
      optionLabelPrefixField: "dwTypeName",
      excludeOptionValues: ["SOURCE_LAYER", "NONE_LAYER"],
    },
    { key: "busTypeId", label: "业务分类", section: "基本信息", optionsSource: "businessType", optionLabelField: "businessName" },
    { key: "domainId", label: "数据域", section: "基本信息", optionsSource: "dataDomain" },
    { key: "busProcessId", label: "业务过程", section: "基本信息", optionsSource: "businessProcess" },
    { key: "martId", label: "数据集市", section: "基本信息", optionsSource: "dataMart", optionLabelField: "martName" },
    { key: "subjectId", label: "主题域", section: "基本信息", optionsSource: "subjectDomain", optionLabelField: "domainName" },
    { key: "enShortName", label: "英文缩写", section: "基本信息", required: true },
    { key: "cnName", label: "中文名称", section: "基本信息", required: true },
    { key: "enName", label: "英文名称", section: "基本信息" },
    { key: "director", label: "负责人", section: "基本信息", required: true },
    { key: "remark", label: "描述", section: "基本信息", multiline: true },
  ],
  columns: [
    { key: "enShortName", title: "英文缩写", width: 145 },
    { key: "cnName", title: "指标名称", width: 220 },
    { key: "computeMode", title: "计算模式", width: 130 },
    { key: "deriveId", title: "关联派生指标", width: 190 },
    { key: "director", title: "负责人", width: 120 },
    { key: "submit", title: "状态", width: 90 },
  ],
};
